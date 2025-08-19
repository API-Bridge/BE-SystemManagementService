package org.example.SystemManagementSvc.service;

import org.example.SystemManagementSvc.domain.ExternalApi;
import org.example.SystemManagementSvc.domain.HealthCheckResult;
import org.example.SystemManagementSvc.repository.ExternalApiRepository;
import org.example.SystemManagementSvc.repository.HealthCheckResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HealthCheckService 테스트")
class HealthCheckServiceTest {

    @Mock
    private ExternalApiRepository externalApiRepository;

    @Mock
    private HealthCheckResultRepository healthCheckResultRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private PrometheusMetricsService prometheusMetricsService;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private HealthCheckService healthCheckService;

    private ExternalApi mockApi;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(healthCheckService, "unhealthyTtlSeconds", 180L);

        mockApi = ExternalApi.builder()
                .apiId("test-api-1")
                .apiName("테스트 API")
                .apiUrl("https://api.test.com/health")
                .apiIssuer("테스트 제공업체")
                .domain(ExternalApi.ApiDomain.GOVERNMENT_DATA)
                .keyword(ExternalApi.ApiKeyword.WEATHER)
                .priority(ExternalApi.HealthCheckPriority.MEDIUM)
                .apiEffectiveness(true)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("모든 API 헬스체크 성공 테스트")
    void performHealthCheckForAllApis_Success() throws Exception {
        // Given
        List<ExternalApi> activeApis = List.of(mockApi);
        when(externalApiRepository.findByApiEffectivenessTrue()).thenReturn(activeApis);
        when(healthCheckResultRepository.findMaxConsecutiveFailures(anyString())).thenReturn(0);
        when(healthCheckResultRepository.save(any(HealthCheckResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        setupSuccessfulWebClientMock();

        // When
        CompletableFuture<Map<String, HealthCheckResult>> future = healthCheckService.performHealthCheckForAllApis();
        Map<String, HealthCheckResult> results = future.get();

        // Then
        assertThat(results).hasSize(1);
        assertThat(results).containsKey("test-api-1");
        
        HealthCheckResult result = results.get("test-api-1");
        assertThat(result.getStatus()).isEqualTo(HealthCheckResult.HealthStatus.HEALTHY);
        assertThat(result.getApiId()).isEqualTo("test-api-1");
        assertThat(result.getHttpStatusCode()).isEqualTo(200);

        verify(healthCheckResultRepository).save(any(HealthCheckResult.class));
        verify(prometheusMetricsService).recordHealthCheck(eq("테스트 API"), eq("테스트 제공업체"), eq(true), anyLong());
    }

    @Test
    @DisplayName("단일 API 헬스체크 성공 테스트")
    void performSingleHealthCheckAsync_Success() throws Exception {
        // Given
        when(healthCheckResultRepository.findMaxConsecutiveFailures(anyString())).thenReturn(0);
        when(healthCheckResultRepository.save(any(HealthCheckResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        setupSuccessfulWebClientMock();

        // When
        CompletableFuture<HealthCheckResult> future = healthCheckService.performSingleHealthCheckAsync(mockApi);
        HealthCheckResult result = future.get();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(HealthCheckResult.HealthStatus.HEALTHY);
        assertThat(result.getApiId()).isEqualTo("test-api-1");
        assertThat(result.getCheckType()).isEqualTo(HealthCheckResult.HealthCheckType.STATIC);
        assertThat(result.getHttpStatusCode()).isEqualTo(200);
        assertThat(result.getResponseTimeMs()).isGreaterThan(0);
        assertThat(result.getConsecutiveFailures()).isEqualTo(0);
    }

    @Test
    @DisplayName("높은 우선순위 API 동적 헬스체크 테스트")
    void performSingleHealthCheckAsync_HighPriorityDynamic() throws Exception {
        // Given
        mockApi = mockApi.toBuilder()
                .priority(ExternalApi.HealthCheckPriority.HIGH)
                .build();

        when(healthCheckResultRepository.findMaxConsecutiveFailures(anyString())).thenReturn(0);
        when(healthCheckResultRepository.save(any(HealthCheckResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        setupSuccessfulWebClientMock();

        // When
        CompletableFuture<HealthCheckResult> future = healthCheckService.performSingleHealthCheckAsync(mockApi);
        HealthCheckResult result = future.get();

        // Then
        assertThat(result.getCheckType()).isEqualTo(HealthCheckResult.HealthCheckType.DYNAMIC);
    }

    @Test
    @DisplayName("HTTP 4xx 에러 응답 테스트")
    void performSingleHealthCheckAsync_ClientError() throws Exception {
        // Given
        when(healthCheckResultRepository.findMaxConsecutiveFailures(anyString())).thenReturn(2);
        when(healthCheckResultRepository.save(any(HealthCheckResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        setupErrorWebClientMock(404, "Not Found");

        // When
        CompletableFuture<HealthCheckResult> future = healthCheckService.performSingleHealthCheckAsync(mockApi);
        HealthCheckResult result = future.get();

        // Then
        assertThat(result.getStatus()).isEqualTo(HealthCheckResult.HealthStatus.UNHEALTHY);
        assertThat(result.getHttpStatusCode()).isEqualTo(404);
        assertThat(result.getErrorMessage()).contains("HTTP Error");
        assertThat(result.getConsecutiveFailures()).isEqualTo(3);
    }

    @Test
    @DisplayName("HTTP 5xx 서버 에러 응답 테스트")
    void performSingleHealthCheckAsync_ServerError() throws Exception {
        // Given
        when(healthCheckResultRepository.findMaxConsecutiveFailures(anyString())).thenReturn(0);
        when(healthCheckResultRepository.save(any(HealthCheckResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        setupErrorWebClientMock(500, "Internal Server Error");

        // When
        CompletableFuture<HealthCheckResult> future = healthCheckService.performSingleHealthCheckAsync(mockApi);
        HealthCheckResult result = future.get();

        // Then
        assertThat(result.getStatus()).isEqualTo(HealthCheckResult.HealthStatus.UNHEALTHY);
        assertThat(result.getHttpStatusCode()).isEqualTo(500);
        assertThat(result.getConsecutiveFailures()).isEqualTo(1);
    }

    @Test
    @DisplayName("연결 타임아웃 테스트")
    void performSingleHealthCheckAsync_Timeout() throws Exception {
        // Given
        when(healthCheckResultRepository.findMaxConsecutiveFailures(anyString())).thenReturn(0);
        when(healthCheckResultRepository.save(any(HealthCheckResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        setupTimeoutWebClientMock();

        // When
        CompletableFuture<HealthCheckResult> future = healthCheckService.performSingleHealthCheckAsync(mockApi);
        HealthCheckResult result = future.get();

        // Then
        assertThat(result.getStatus()).isEqualTo(HealthCheckResult.HealthStatus.TIMEOUT);
        assertThat(result.getErrorMessage()).contains("Request failed");
        assertThat(result.isTimeout()).isTrue();
    }

    @Test
    @DisplayName("응답 시간이 긴 경우 성능 저하 상태 테스트")
    void performSingleHealthCheckAsync_SlowResponse() throws Exception {
        // Given
        when(healthCheckResultRepository.findMaxConsecutiveFailures(anyString())).thenReturn(0);
        when(healthCheckResultRepository.save(any(HealthCheckResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 5초 이상 걸리는 응답 시뮬레이션
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(
                Mono.just("{\"status\": \"ok\"}").delayElement(Duration.ofMillis(5100))
        );
        when(responseSpec.timeout(any(Duration.class))).thenReturn(responseSpec);

        // When
        CompletableFuture<HealthCheckResult> future = healthCheckService.performSingleHealthCheckAsync(mockApi);
        HealthCheckResult result = future.get();

        // Then
        assertThat(result.getStatus()).isEqualTo(HealthCheckResult.HealthStatus.DEGRADED);
        assertThat(result.getResponseTimeMs()).isGreaterThan(5000);
    }

    @Test
    @DisplayName("Redis 캐시에서 비정상 API 목록 조회 테스트")
    void getUnhealthyApisFromCache_Success() {
        // Given
        Set<String> mockKeys = Set.of(
                "unhealthy:api-1",
                "unhealthy:api-2",
                "unhealthy:api-3"
        );
        when(redisTemplate.keys("unhealthy:*")).thenReturn(mockKeys);

        // When
        List<String> unhealthyApis = healthCheckService.getUnhealthyApisFromCache();

        // Then
        assertThat(unhealthyApis).hasSize(3);
        assertThat(unhealthyApis).containsExactlyInAnyOrder("api-1", "api-2", "api-3");
    }

    @Test
    @DisplayName("특정 API 비정상 상태 확인 테스트")
    void isApiCurrentlyUnhealthy_True() {
        // Given
        String apiId = "test-api";
        when(redisTemplate.hasKey("unhealthy:" + apiId)).thenReturn(true);

        // When
        boolean isUnhealthy = healthCheckService.isApiCurrentlyUnhealthy(apiId);

        // Then
        assertThat(isUnhealthy).isTrue();
    }

    @Test
    @DisplayName("특정 API 정상 상태 확인 테스트")
    void isApiCurrentlyUnhealthy_False() {
        // Given
        String apiId = "healthy-api";
        when(redisTemplate.hasKey("unhealthy:" + apiId)).thenReturn(false);

        // When
        boolean isUnhealthy = healthCheckService.isApiCurrentlyUnhealthy(apiId);

        // Then
        assertThat(isUnhealthy).isFalse();
    }

    @Test
    @DisplayName("Redis 연결 실패 시 예외 처리 테스트")
    void getUnhealthyApisFromCache_RedisException() {
        // Given
        when(redisTemplate.keys(anyString())).thenThrow(new RuntimeException("Redis 연결 실패"));

        // When
        List<String> unhealthyApis = healthCheckService.getUnhealthyApisFromCache();

        // Then
        assertThat(unhealthyApis).isEmpty();
    }

    @Test
    @DisplayName("API 효과성 업데이트 테스트")
    void updateApiEffectiveness_Success() throws Exception {
        // Given
        when(externalApiRepository.findByApiEffectivenessTrue()).thenReturn(List.of(mockApi));
        when(externalApiRepository.findById(anyString())).thenReturn(Optional.of(mockApi));
        when(externalApiRepository.save(any(ExternalApi.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(healthCheckResultRepository.findMaxConsecutiveFailures(anyString())).thenReturn(0);
        when(healthCheckResultRepository.save(any(HealthCheckResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        setupErrorWebClientMock(500, "Internal Server Error");

        // When
        CompletableFuture<Map<String, HealthCheckResult>> future = healthCheckService.performHealthCheckForAllApis();
        future.get();

        // Then
        verify(externalApiRepository).save(argThat(api -> !api.getApiEffectiveness()));
    }

    private void setupSuccessfulWebClientMock() {
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("{\"status\": \"ok\"}"));
        when(responseSpec.timeout(any(Duration.class))).thenReturn(responseSpec);
    }

    private void setupErrorWebClientMock(int statusCode, String statusText) {
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenThrow(
                WebClientResponseException.create(statusCode, statusText, null, null, null)
        );
        when(responseSpec.timeout(any(Duration.class))).thenReturn(responseSpec);
    }

    private void setupTimeoutWebClientMock() {
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenThrow(
                new WebClientRequestException(new java.net.SocketTimeoutException("Connect timeout"), null, null, null)
        );
        when(responseSpec.timeout(any(Duration.class))).thenReturn(responseSpec);
    }
}