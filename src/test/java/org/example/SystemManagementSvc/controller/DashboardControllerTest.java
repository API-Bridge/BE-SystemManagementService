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
        // 테스트용 Mock 데이터 준비 - 실제 서비스와 동일한 구조
        mockErrorRanking = List.of(
            // 1순위: 인증 서비스 - 가장 많은 에러 발생 예상 패턴
            ErrorStatistics.builder()
                .serviceName("auth-service")
                .totalErrorCount(150L)  // 테스트 예상 에러 수
                .rank(1)
                .errorRate(5.2)
                .mostFrequentErrorType("VALIDATION_ERROR")
                .build(),
            // 2순위: 결제 서비스 - DB 관련 에러
            ErrorStatistics.builder()
                .serviceName("payment-service")
                .totalErrorCount(89L)   // 두 번째로 많은 에러
                .rank(2)
                .errorRate(3.1)
                .mostFrequentErrorType("DATABASE_ERROR")
                .build()
        );

        // API 호출 순위 Mock 데이터 - 주요 공공데이터 API
        mockApiCallRanking = List.of(
            // 1순위: 날씨 API - 가장 빈번하게 호출되는 API
            ApiCallStatistics.builder()
                .apiName("weather-api")
                .apiProvider("기상청")
                .totalCallCount(1250L)        // 테스트 예상 호출 수
                .successCallCount(1200L)      // 성공 호출 수
                .failureCallCount(50L)        // 실패 호출 수
                .successRate(96.0)            // 예상 성공률
                .averageResponseTime(250.5)   // 평균 응답시간
                .rank(1)
                .build(),
            // 2순위: 교통 API - 두 번째로 많이 호출되는 API
            ApiCallStatistics.builder()
                .apiName("traffic-api")
                .apiProvider("국토교통부")
                .totalCallCount(890L)         // 날씨 API보다 적은 호출량
                .successCallCount(850L)
                .failureCallCount(40L)
                .successRate(95.5)
                .averageResponseTime(180.3)   // 날씨 API보다 빠른 응답
                .rank(2)
                .build()
        );

        // 대시보드 전체 요약 데이터 (위 데이터들의 집계 결과)
        DashboardSummary summary = DashboardSummary.builder()
            .totalErrors(239L)              // 전체 에러 수 (150 + 89 = 239)
            .totalApiCalls(2140L)           // 전체 API 호출 수 (1250 + 890 = 2140)
            .overallSuccessRate(95.8)       // 전체 성공률 (가중평균)
            .activeServiceCount(2)          // 모니터링 중인 서비스 수
            .monitoredApiCount(2)           // 모니터링 중인 API 수
            .systemStatus("HEALTHY")       // 예상 시스템 상태 (에러율이 임계치 이하)
            .build();

        // 분석 기간 정보 - 테스트에서는 고정된 시간 범위 사용
        AnalysisPeriod analysisPeriod = AnalysisPeriod.builder()
            .startTime("2024-01-01T00:00:00")  // 분석 시작 시각 (고정값)
            .endTime("2024-01-02T00:00:00")    // 분석 종료 시각 (고정값)
            .durationHours(24L)                // 분석 기간 (24시간)
            .description("최근 24시간")         // 사용자에게 보여줄 설명
            .build();

        // 최종 대시보드 응답 객체 - 모든 데이터를 통합
        mockDashboardResponse = DashboardResponse.builder()
            .errorRanking(mockErrorRanking)      // 에러 순위 데이터
            .apiCallRanking(mockApiCallRanking)  // API 호출 순위 데이터
            .summary(summary)                    // 전체 요약 데이터
            .analysisPeriod(analysisPeriod)      // 분석 기간 정보
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