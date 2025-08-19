package org.example.SystemManagementSvc.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.SystemManagementSvc.domain.ExternalApi;
import org.example.SystemManagementSvc.domain.HealthCheckResult;
import org.example.SystemManagementSvc.repository.ExternalApiRepository;
import org.example.SystemManagementSvc.repository.HealthCheckResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("헬스체크 통합 테스트")
class HealthCheckIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ExternalApiRepository externalApiRepository;

    @Autowired
    private HealthCheckResultRepository healthCheckResultRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        
        // 테스트 데이터 초기화
        healthCheckResultRepository.deleteAll();
        externalApiRepository.deleteAll();
        
        // 테스트용 외부 API 데이터 생성
        setupTestData();
    }

    private void setupTestData() {
        // 테스트용 외부 API 생성
        ExternalApi weatherApi = ExternalApi.builder()
                .apiId("weather-api-test")
                .apiName("테스트 날씨 API")
                .apiUrl("https://api.weather.test.com/current")
                .apiIssuer("테스트 기상청")
                .apiOwner("test-team")
                .domain(ExternalApi.ApiDomain.WEATHER)
                .keyword(ExternalApi.ApiKeyword.REAL_TIME)
                .httpMethod("GET")
                .apiDescription("실시간 날씨 정보 제공")
                .apiEffectiveness(true)
                .build();

        ExternalApi trafficApi = ExternalApi.builder()
                .apiId("traffic-api-test")
                .apiName("테스트 교통 API")
                .apiUrl("https://api.traffic.test.com/info")
                .apiIssuer("테스트 교통공단")
                .apiOwner("test-team")
                .domain(ExternalApi.ApiDomain.TRAFFIC)
                .keyword(ExternalApi.ApiKeyword.REAL_TIME)
                .httpMethod("GET")
                .apiDescription("실시간 교통 정보 제공")
                .apiEffectiveness(false)
                .build();

        externalApiRepository.saveAll(List.of(weatherApi, trafficApi));

        // 테스트용 헬스체크 결과 생성
        HealthCheckResult healthyResult = HealthCheckResult.builder()
                .apiId("weather-api-test")
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .httpStatusCode(200)
                .responseTimeMs(150L)
                .responseSample("{\"temperature\": 25}")
                .checkedAt(LocalDateTime.now().minusMinutes(5))
                .consecutiveFailures(0)
                .sslValid(true)
                .build();

        HealthCheckResult unhealthyResult = HealthCheckResult.builder()
                .apiId("traffic-api-test")
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.UNHEALTHY)
                .httpStatusCode(500)
                .responseTimeMs(5000L)
                .errorMessage("Internal Server Error")
                .checkedAt(LocalDateTime.now().minusMinutes(3))
                .consecutiveFailures(2)
                .sslValid(true)
                .build();

        healthCheckResultRepository.saveAll(List.of(healthyResult, unhealthyResult));
    }

    @Test
    @DisplayName("API 상태 요약 조회 통합 테스트")
    void getApiStatusSummary_Integration() throws Exception {
        // When & Then
        mockMvc.perform(get("/health/summary"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.availableApis").isNumber())
                .andExpect(jsonPath("$.effectiveApis").isNumber())
                .andExpect(jsonPath("$.availabilityRate").isNumber())
                .andExpect(jsonPath("$.systemHealth").isString())
                .andExpect(jsonPath("$.lastUpdated").exists());
    }

    @Test
    @DisplayName("시스템 건강도 조회 통합 테스트")
    void getSystemHealth_Integration() throws Exception {
        // When & Then
        mockMvc.perform(get("/health/system/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.availabilityRate").isNumber())
                .andExpect(jsonPath("$.availableApis").isNumber())
                .andExpect(jsonPath("$.totalApis").isNumber());
    }

    @Test
    @DisplayName("수동 헬스체크 실행 통합 테스트")
    void triggerManualHealthCheck_Integration() throws Exception {
        // When & Then
        mockMvc.perform(post("/health/check/manual"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Health check initiated"))
                .andExpect(jsonPath("$.status").value("STARTED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("도메인별 가용 API 조회 통합 테스트")
    void getAvailableApisByDomain_Integration() throws Exception {
        // When & Then
        mockMvc.perform(get("/health/available/domain/WEATHER"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].apiId").value("weather-api-test"))
                .andExpect(jsonPath("$[0].apiName").value("테스트 날씨 API"))
                .andExpect(jsonPath("$[0].domain").value("WEATHER"));
    }

    @Test
    @DisplayName("키워드별 가용 API 조회 통합 테스트")
    void getAvailableApisByKeyword_Integration() throws Exception {
        // When & Then
        mockMvc.perform(get("/health/available/keyword/REAL_TIME"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].keyword").value("REAL_TIME"));
    }

    @Test
    @DisplayName("헬스체크 결과 데이터베이스 저장 확인 테스트")
    void healthCheckResultPersistence_Integration() {
        // Given
        String apiId = "persistence-test-api";
        
        ExternalApi testApi = ExternalApi.builder()
                .apiId(apiId)
                .apiName("영속성 테스트 API")
                .apiUrl("https://api.persistence.test.com")
                .apiIssuer("테스트회사")
                .domain(ExternalApi.ApiDomain.OTHER)
                .keyword(ExternalApi.ApiKeyword.REST_API)
                .httpMethod("GET")
                .apiEffectiveness(true)
                .build();

        externalApiRepository.save(testApi);

        HealthCheckResult testResult = HealthCheckResult.builder()
                .apiId(apiId)
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .httpStatusCode(200)
                .responseTimeMs(300L)
                .checkedAt(LocalDateTime.now())
                .consecutiveFailures(0)
                .build();

        // When
        HealthCheckResult savedResult = healthCheckResultRepository.save(testResult);

        // Then
        assertThat(savedResult.getCheckId()).isNotNull();
        assertThat(savedResult.getCreatedAt()).isNotNull();
        assertThat(savedResult.getUpdatedAt()).isNotNull();

        // 데이터베이스에서 조회 확인
        List<HealthCheckResult> results = healthCheckResultRepository.findByApiIdOrderByCheckedAtDesc(apiId);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getApiId()).isEqualTo(apiId);
        assertThat(results.get(0).getStatus()).isEqualTo(HealthCheckResult.HealthStatus.HEALTHY);
    }

    @Test
    @DisplayName("API 효과성 업데이트 통합 테스트")
    void apiEffectivenessUpdate_Integration() {
        // Given
        String apiId = "effectiveness-test-api";
        
        ExternalApi testApi = ExternalApi.builder()
                .apiId(apiId)
                .apiName("효과성 테스트 API")
                .apiUrl("https://api.effectiveness.test.com")
                .apiIssuer("테스트회사")
                .domain(ExternalApi.ApiDomain.OTHER)
                .keyword(ExternalApi.ApiKeyword.REST_API)
                .httpMethod("GET")
                .apiEffectiveness(true)
                .build();

        ExternalApi savedApi = externalApiRepository.save(testApi);
        assertThat(savedApi.getApiEffectiveness()).isTrue();

        // When - API 효과성을 false로 변경
        savedApi.setApiEffectiveness(false);
        ExternalApi updatedApi = externalApiRepository.save(savedApi);

        // Then
        assertThat(updatedApi.getApiEffectiveness()).isFalse();
        assertThat(updatedApi.getUpdatedAt()).isNotNull();

        // 데이터베이스에서 조회 확인
        ExternalApi retrievedApi = externalApiRepository.findById(apiId).orElse(null);
        assertThat(retrievedApi).isNotNull();
        assertThat(retrievedApi.getApiEffectiveness()).isFalse();
    }

    @Test
    @DisplayName("활성화된 API 통계 조회 통합 테스트")
    void activeApiStatistics_Integration() {
        // When
        long totalActiveApis = externalApiRepository.countByApiEffectivenessTrue();
        long totalInactiveApis = externalApiRepository.countInactiveApis();
        List<Object[]> domainStats = externalApiRepository.getApiCountByDomain();

        // Then
        assertThat(totalActiveApis).isEqualTo(1); // weather-api-test만 활성화
        assertThat(totalInactiveApis).isEqualTo(1); // traffic-api-test는 비활성화
        assertThat(domainStats).isNotEmpty();
    }

    @Test
    @DisplayName("헬스체크 통계 쿼리 통합 테스트")
    void healthCheckStatistics_Integration() {
        // Given
        LocalDateTime since = LocalDateTime.now().minusHours(1);

        // When
        List<Object[]> successRateStats = healthCheckResultRepository.getSuccessRateByApi(since);
        List<Object[]> averageResponseTime = healthCheckResultRepository.getAverageResponseTimeByApi(since);
        Object overallStats = healthCheckResultRepository.getOverallHealthStatistics(since);

        // Then
        assertThat(successRateStats).isNotEmpty();
        assertThat(averageResponseTime).isNotEmpty();
        assertThat(overallStats).isNotNull();
        
        // 전체 통계 검증
        Object[] statsArray = (Object[]) overallStats;
        assertThat(statsArray).hasSize(6);
        assertThat(statsArray[0]).isInstanceOf(Long.class); // total count
    }

    @Test
    @DisplayName("연속 실패 횟수 추적 통합 테스트")
    void consecutiveFailuresTracking_Integration() {
        // Given
        String apiId = "failure-tracking-test";
        
        ExternalApi testApi = ExternalApi.builder()
                .apiId(apiId)
                .apiName("실패 추적 테스트 API")
                .apiUrl("https://api.failure.test.com")
                .apiIssuer("테스트회사")
                .domain(ExternalApi.ApiDomain.OTHER)
                .keyword(ExternalApi.ApiKeyword.REST_API)
                .httpMethod("GET")
                .apiEffectiveness(true)
                .build();

        externalApiRepository.save(testApi);

        // 연속된 실패 결과 생성
        HealthCheckResult firstFailure = HealthCheckResult.builder()
                .apiId(apiId)
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.UNHEALTHY)
                .httpStatusCode(500)
                .checkedAt(LocalDateTime.now().minusMinutes(10))
                .consecutiveFailures(1)
                .build();

        HealthCheckResult secondFailure = HealthCheckResult.builder()
                .apiId(apiId)
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.UNHEALTHY)
                .httpStatusCode(500)
                .checkedAt(LocalDateTime.now().minusMinutes(8))
                .consecutiveFailures(2)
                .build();

        HealthCheckResult thirdFailure = HealthCheckResult.builder()
                .apiId(apiId)
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.TIMEOUT)
                .checkedAt(LocalDateTime.now().minusMinutes(6))
                .consecutiveFailures(3)
                .build();

        healthCheckResultRepository.saveAll(List.of(firstFailure, secondFailure, thirdFailure));

        // When
        Integer maxFailures = healthCheckResultRepository.findMaxConsecutiveFailures(apiId);
        List<HealthCheckResult> apiHistory = healthCheckResultRepository.findByApiIdOrderByCheckedAtDesc(apiId);

        // Then
        assertThat(maxFailures).isEqualTo(3);
        assertThat(apiHistory).hasSize(3);
        assertThat(apiHistory.get(0).getConsecutiveFailures()).isEqualTo(3); // 최신이 첫 번째
        assertThat(apiHistory.get(1).getConsecutiveFailures()).isEqualTo(2);
        assertThat(apiHistory.get(2).getConsecutiveFailures()).isEqualTo(1);
    }

    @Test
    @DisplayName("헬스체크 우선순위 기반 조회 통합 테스트")
    void healthCheckPriorityBased_Integration() {
        // Given
        ExternalApi highPriorityApi = ExternalApi.builder()
                .apiId("high-priority-api")
                .apiName("높은 우선순위 API")
                .apiUrl("https://api.high.priority.com")
                .apiIssuer("우선순위회사")
                .domain(ExternalApi.ApiDomain.WEATHER)
                .keyword(ExternalApi.ApiKeyword.REAL_TIME) // HIGH 우선순위
                .httpMethod("GET")
                .apiEffectiveness(true)
                .build();

        ExternalApi lowPriorityApi = ExternalApi.builder()
                .apiId("low-priority-api")
                .apiName("낮은 우선순위 API")
                .apiUrl("https://api.low.priority.com")
                .apiIssuer("우선순위회사")
                .domain(ExternalApi.ApiDomain.CULTURE)
                .keyword(ExternalApi.ApiKeyword.BATCH) // LOW 우선순위
                .httpMethod("GET")
                .apiEffectiveness(true)
                .build();

        externalApiRepository.saveAll(List.of(highPriorityApi, lowPriorityApi));

        // When
        List<ExternalApi> prioritizedApis = externalApiRepository.findAllOrderedByPriority();

        // Then
        assertThat(prioritizedApis).hasSizeGreaterThanOrEqualTo(2);
        
        // 우선순위 검증
        ExternalApi.HealthCheckPriority highPriority = highPriorityApi.getHealthCheckPriority();
        ExternalApi.HealthCheckPriority lowPriority = lowPriorityApi.getHealthCheckPriority();
        
        assertThat(highPriority).isEqualTo(ExternalApi.HealthCheckPriority.HIGH);
        assertThat(lowPriority).isEqualTo(ExternalApi.HealthCheckPriority.LOW);
        assertThat(highPriority.getIntervalSeconds()).isLessThan(lowPriority.getIntervalSeconds());
    }
}