package org.example.SystemManagementSvc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.SystemManagementSvc.dto.analytics.*;
import org.example.SystemManagementSvc.service.ApiCallAnalyticsService;
import org.example.SystemManagementSvc.service.DashboardService;
import org.example.SystemManagementSvc.service.ErrorAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = DashboardController.class)
@Import(org.example.SystemManagementSvc.config.TestConfig.class)
@ActiveProfiles("test") 
@DisplayName("DashboardController 테스트")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private ErrorAnalyticsService errorAnalyticsService;

    @MockBean
    private ApiCallAnalyticsService apiCallAnalyticsService;

    private DashboardResponse mockDashboardResponse;
    private List<ErrorStatistics> mockErrorRanking;
    private List<ApiCallStatistics> mockApiCallRanking;

    @BeforeEach
    void setUp() {
        // Mock data 준비
        mockErrorRanking = List.of(
            ErrorStatistics.builder()
                .serviceName("auth-service")
                .totalErrorCount(150L)
                .rank(1)
                .errorRate(5.2)
                .mostFrequentErrorType("VALIDATION_ERROR")
                .build(),
            ErrorStatistics.builder()
                .serviceName("payment-service")
                .totalErrorCount(89L)
                .rank(2)
                .errorRate(3.1)
                .mostFrequentErrorType("DATABASE_ERROR")
                .build()
        );

        mockApiCallRanking = List.of(
            ApiCallStatistics.builder()
                .apiName("weather-api")
                .apiProvider("기상청")
                .totalCallCount(1250L)
                .successCallCount(1200L)
                .failureCallCount(50L)
                .successRate(96.0)
                .averageResponseTime(250.5)
                .rank(1)
                .build(),
            ApiCallStatistics.builder()
                .apiName("traffic-api")
                .apiProvider("국토교통부")
                .totalCallCount(890L)
                .successCallCount(850L)
                .failureCallCount(40L)
                .successRate(95.5)
                .averageResponseTime(180.3)
                .rank(2)
                .build()
        );

        DashboardSummary summary = DashboardSummary.builder()
            .totalErrors(239L)
            .totalApiCalls(2140L)
            .overallSuccessRate(95.8)
            .activeServiceCount(2)
            .monitoredApiCount(2)
            .systemStatus("HEALTHY")
            .build();

        AnalysisPeriod analysisPeriod = AnalysisPeriod.builder()
            .startTime("2024-01-01T00:00:00")
            .endTime("2024-01-02T00:00:00")
            .durationHours(24L)
            .description("최근 24시간")
            .build();

        mockDashboardResponse = DashboardResponse.builder()
            .errorRanking(mockErrorRanking)
            .apiCallRanking(mockApiCallRanking)
            .summary(summary)
            .analysisPeriod(analysisPeriod)
            .build();
    }

    @Test
    @DisplayName("통합 대시보드 분석 데이터 조회 API 테스트")
    void shouldReturnDashboardAnalytics() throws Exception {
        // Given
        when(dashboardService.getDashboardAnalytics(anyInt(), anyInt()))
            .thenReturn(mockDashboardResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/dashboard/analytics")
                .param("hours", "24")
                .param("limit", "10")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("대시보드 분석 데이터 조회 성공"))
            .andExpect(jsonPath("$.data").exists())
            .andExpect(jsonPath("$.data.errorRanking").isArray())
            .andExpect(jsonPath("$.data.errorRanking[0].serviceName").value("auth-service"))
            .andExpect(jsonPath("$.data.errorRanking[0].totalErrorCount").value(150))
            .andExpect(jsonPath("$.data.apiCallRanking").isArray())
            .andExpect(jsonPath("$.data.apiCallRanking[0].apiName").value("weather-api"))
            .andExpect(jsonPath("$.data.apiCallRanking[0].totalCallCount").value(1250))
            .andExpect(jsonPath("$.data.summary.systemStatus").value("HEALTHY"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("서비스별 에러 순위 조회 API 테스트")
    void shouldReturnErrorRanking() throws Exception {
        // Given
        when(errorAnalyticsService.getServiceErrorRanking(any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
            .thenReturn(mockErrorRanking);

        // When & Then
        mockMvc.perform(get("/api/v1/dashboard/errors/ranking")
                .param("hours", "24")
                .param("limit", "10")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("서비스별 에러 순위 조회 성공"))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].serviceName").value("auth-service"))
            .andExpect(jsonPath("$.data[0].totalErrorCount").value(150))
            .andExpect(jsonPath("$.data[0].rank").value(1))
            .andExpect(jsonPath("$.data[1].serviceName").value("payment-service"))
            .andExpect(jsonPath("$.data[1].totalErrorCount").value(89))
            .andExpect(jsonPath("$.data[1].rank").value(2))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("외부 API 호출 순위 조회 API 테스트")
    void shouldReturnApiCallRanking() throws Exception {
        // Given
        when(apiCallAnalyticsService.getApiCallRanking(any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
            .thenReturn(mockApiCallRanking);

        // When & Then
        mockMvc.perform(get("/api/v1/dashboard/api-calls/ranking")
                .param("hours", "24")
                .param("limit", "10")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("외부 API 호출 순위 조회 성공"))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].apiName").value("weather-api"))
            .andExpect(jsonPath("$.data[0].totalCallCount").value(1250))
            .andExpect(jsonPath("$.data[0].successRate").value(96.0))
            .andExpect(jsonPath("$.data[0].rank").value(1))
            .andExpect(jsonPath("$.data[1].apiName").value("traffic-api"))
            .andExpect(jsonPath("$.data[1].totalCallCount").value(890))
            .andExpect(jsonPath("$.data[1].rank").value(2))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("시스템 헬스체크 API 테스트")
    void shouldReturnHealthStatus() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/dashboard/health")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("대시보드 서비스 정상 동작"))
            .andExpect(jsonPath("$.data").value("OK"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("기본값으로 대시보드 분석 데이터 조회 API 테스트")
    void shouldReturnDashboardAnalyticsWithDefaultValues() throws Exception {
        // Given
        when(dashboardService.getDashboardAnalytics(anyInt(), anyInt()))
            .thenReturn(mockDashboardResponse);

        // When & Then
        mockMvc.perform(get("/api/v1/dashboard/analytics")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").exists());
    }
}