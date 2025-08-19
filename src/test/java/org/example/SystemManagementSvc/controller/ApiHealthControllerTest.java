package org.example.SystemManagementSvc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.SystemManagementSvc.domain.ExternalApi;
import org.example.SystemManagementSvc.dto.ApiAvailabilityResponse;
import org.example.SystemManagementSvc.dto.ApiStatusSummary;
import org.example.SystemManagementSvc.service.AdvancedHealthCheckService;
import org.example.SystemManagementSvc.service.ApiStatusManager;
import org.example.SystemManagementSvc.service.RedisHealthStateManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiHealthController.class)
@DisplayName("ApiHealthController 테스트")
class ApiHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiStatusManager apiStatusManager;

    @MockBean
    private AdvancedHealthCheckService advancedHealthCheckService;

    @MockBean
    private RedisHealthStateManager redisHealthStateManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("전체 API 상태 요약 조회 성공 테스트")
    void getApiStatusSummary_Success() throws Exception {
        // Given
        ApiStatusSummary mockSummary = ApiStatusSummary.builder()
                .availableApis(8)
                .effectiveApis(10)
                .availabilityRate(80.0)
                .systemHealth(ApiStatusSummary.SystemHealth.HEALTHY)
                .availabilityStatusText("시스템 정상 운영 중")
                .lastUpdated(LocalDateTime.now())
                .build();

        when(apiStatusManager.getApiStatusSummary()).thenReturn(mockSummary);

        // When & Then
        mockMvc.perform(get("/health/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableApis").value(8))
                .andExpect(jsonPath("$.effectiveApis").value(10))
                .andExpect(jsonPath("$.availabilityRate").value(80.0))
                .andExpect(jsonPath("$.systemHealth").value("HEALTHY"));
    }

    @Test
    @DisplayName("특정 API 상태 조회 성공 테스트")
    void getApiStatus_Success() throws Exception {
        // Given
        String apiId = "weather-api";
        ApiAvailabilityResponse mockResponse = ApiAvailabilityResponse.builder()
                .apiId(apiId)
                .apiName("날씨 API")
                .isAvailable(true)
                .statusCode(200)
                .responseTime(250L)
                .lastChecked(LocalDateTime.now())
                .build();

        when(apiStatusManager.getApiStatusDetails(apiId)).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/health/status/{apiId}", apiId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiId").value(apiId))
                .andExpect(jsonPath("$.apiName").value("날씨 API"))
                .andExpect(jsonPath("$.isAvailable").value(true))
                .andExpect(jsonPath("$.statusCode").value(200));
    }

    @Test
    @DisplayName("다중 API 가용성 확인 성공 테스트")
    void checkMultipleApiAvailability_Success() throws Exception {
        // Given
        List<String> apiIds = Arrays.asList("weather-api", "traffic-api", "payment-api");
        Map<String, Boolean> mockAvailability = Map.of(
                "weather-api", true,
                "traffic-api", true,
                "payment-api", false
        );

        when(apiStatusManager.checkApisAvailability(apiIds)).thenReturn(mockAvailability);

        // When & Then
        mockMvc.perform(post("/health/availability/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(apiIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['weather-api']").value(true))
                .andExpect(jsonPath("$.['traffic-api']").value(true))
                .andExpect(jsonPath("$.['payment-api']").value(false));
    }

    @Test
    @DisplayName("도메인별 가용 API 조회 성공 테스트")
    void getAvailableApisByDomain_Success() throws Exception {
        // Given
        ExternalApi.ApiDomain domain = ExternalApi.ApiDomain.GOVERNMENT_DATA;
        List<ExternalApi> mockApis = List.of(
                ExternalApi.builder()
                        .apiId("gov-api-1")
                        .apiName("공공데이터 API 1")
                        .domain(domain)
                        .build(),
                ExternalApi.builder()
                        .apiId("gov-api-2")
                        .apiName("공공데이터 API 2")
                        .domain(domain)
                        .build()
        );

        when(apiStatusManager.getAvailableApisByDomain(domain)).thenReturn(mockApis);

        // When & Then
        mockMvc.perform(get("/health/available/domain/{domain}", domain.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].apiId").value("gov-api-1"))
                .andExpect(jsonPath("$[1].apiId").value("gov-api-2"));
    }

    @Test
    @DisplayName("키워드별 가용 API 조회 성공 테스트")
    void getAvailableApisByKeyword_Success() throws Exception {
        // Given
        ExternalApi.ApiKeyword keyword = ExternalApi.ApiKeyword.WEATHER;
        List<ExternalApi> mockApis = List.of(
                ExternalApi.builder()
                        .apiId("weather-api")
                        .apiName("기상청 날씨 API")
                        .keyword(keyword)
                        .build()
        );

        when(apiStatusManager.getAvailableApisByKeyword(keyword)).thenReturn(mockApis);

        // When & Then
        mockMvc.perform(get("/health/available/keyword/{keyword}", keyword.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].apiId").value("weather-api"));
    }

    @Test
    @DisplayName("우선순위별 가용 API 조회 성공 테스트")
    void getAvailableApisByPriority_Success() throws Exception {
        // Given
        Map<ExternalApi.HealthCheckPriority, List<ExternalApi>> mockPriorityApis = Map.of(
                ExternalApi.HealthCheckPriority.HIGH, List.of(
                        ExternalApi.builder()
                                .apiId("critical-api")
                                .apiName("중요 API")
                                .priority(ExternalApi.HealthCheckPriority.HIGH)
                                .build()
                ),
                ExternalApi.HealthCheckPriority.MEDIUM, List.of(
                        ExternalApi.builder()
                                .apiId("normal-api")
                                .apiName("일반 API")
                                .priority(ExternalApi.HealthCheckPriority.MEDIUM)
                                .build()
                )
        );

        when(apiStatusManager.getAvailableApisByPriority()).thenReturn(mockPriorityApis);

        // When & Then
        mockMvc.perform(get("/health/available/priority"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.HIGH").isArray())
                .andExpect(jsonPath("$.HIGH[0].apiId").value("critical-api"))
                .andExpect(jsonPath("$.MEDIUM").isArray())
                .andExpect(jsonPath("$.MEDIUM[0].apiId").value("normal-api"));
    }

    @Test
    @DisplayName("불가용 API 목록 조회 성공 테스트")
    void getUnavailableApis_Success() throws Exception {
        // Given
        List<String> mockUnavailableApis = Arrays.asList("failed-api-1", "failed-api-2");

        when(apiStatusManager.getUnavailableApiIds()).thenReturn(mockUnavailableApis);

        // When & Then
        mockMvc.perform(get("/health/unavailable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").value("failed-api-1"))
                .andExpect(jsonPath("$[1]").value("failed-api-2"));
    }

    @Test
    @DisplayName("복구 예상 시간 조회 성공 테스트")
    void getRecoveryEstimates_Success() throws Exception {
        // Given
        Map<String, Long> mockEstimates = Map.of(
                "failed-api-1", 300L,  // 5분
                "failed-api-2", 600L   // 10분
        );

        when(apiStatusManager.getRecoveryEstimates()).thenReturn(mockEstimates);

        // When & Then
        mockMvc.perform(get("/health/recovery-estimates"))
                .andExpect(status().isOk())
                .andExpected(jsonPath("$.['failed-api-1']").value(300))
                .andExpected(jsonPath("$.['failed-api-2']").value(600));
    }

    @Test
    @DisplayName("수동 헬스체크 실행 성공 테스트")
    void triggerManualHealthCheck_Success() throws Exception {
        // Given
        when(advancedHealthCheckService.performIntelligentHealthCheck())
                .thenReturn(CompletableFuture.completedFuture(Map.of("result", "success")));

        // When & Then
        mockMvc.perform(post("/health/check/manual"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Health check initiated"))
                .andExpect(jsonPath("$.status").value("STARTED"));
    }

    @Test
    @DisplayName("특정 API 헬스체크 실행 성공 테스트")
    void triggerSingleApiHealthCheck_Success() throws Exception {
        // Given
        String apiId = "test-api";

        // When & Then
        mockMvc.perform(post("/health/check/manual/{apiId}", apiId))
                .andExpect(status().isOk())
                .andExpected(jsonPath("$.message").value("Health check initiated for API: " + apiId))
                .andExpected(jsonPath("$.apiId").value(apiId))
                .andExpected(jsonPath("$.status").value("STARTED"));
    }

    @Test
    @DisplayName("캐시 상태 조회 성공 테스트")
    void getCacheStatus_Success() throws Exception {
        // Given
        Map<String, Object> mockFailureStats = Map.of(
                "totalFailures", 5,
                "failedApis", Arrays.asList("api1", "api2")
        );

        when(redisHealthStateManager.getFailureStatistics()).thenReturn(mockFailureStats);

        // When & Then
        mockMvc.perform(get("/health/cache/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unhealthyApis").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("시스템 건강도 조회 성공 테스트")
    void getSystemHealth_Success() throws Exception {
        // Given
        ApiStatusSummary mockSummary = ApiStatusSummary.builder()
                .availableApis(9)
                .effectiveApis(10)
                .availabilityRate(90.0)
                .systemHealth(ApiStatusSummary.SystemHealth.HEALTHY)
                .availabilityStatusText("시스템 정상")
                .lastUpdated(LocalDateTime.now())
                .build();

        when(apiStatusManager.getApiStatusSummary()).thenReturn(mockSummary);

        // When & Then
        mockMvc.perform(get("/health/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.availabilityRate").value(90.0))
                .andExpect(jsonPath("$.availableApis").value(9))
                .andExpect(jsonPath("$.totalApis").value(10));
    }

    @Test
    @DisplayName("API 상태 요약 조회 실패 테스트")
    void getApiStatusSummary_Failure() throws Exception {
        // Given
        when(apiStatusManager.getApiStatusSummary())
                .thenThrow(new RuntimeException("Redis 연결 실패"));

        // When & Then
        mockMvc.perform(get("/health/summary"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("존재하지 않는 API 상태 조회 테스트")
    void getApiStatus_NotFound() throws Exception {
        // Given
        String apiId = "non-existent-api";
        when(apiStatusManager.getApiStatusDetails(apiId))
                .thenThrow(new RuntimeException("API를 찾을 수 없습니다"));

        // When & Then
        mockMvc.perform(get("/health/status/{apiId}", apiId))
                .andExpect(status().isInternalServerError());
    }
}