package org.example.SystemManagementSvc.service;

import org.example.SystemManagementSvc.dto.analytics.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService 테스트")
class DashboardServiceTest {

    @Mock
    private Optional<ErrorAnalyticsService> errorAnalyticsService;

    @Mock
    private Optional<ApiCallAnalyticsService> apiCallAnalyticsService;

    @Mock
    private ErrorAnalyticsService mockErrorAnalyticsService;

    @Mock
    private ApiCallAnalyticsService mockApiCallAnalyticsService;

    @InjectMocks
    private DashboardService dashboardService;

    private List<ErrorStatistics> mockErrorRanking;
    private List<ApiCallStatistics> mockApiCallRanking;

    @BeforeEach
    void setUp() {
        // 에러 통계 Mock 데이터
        mockErrorRanking = List.of(
                ErrorStatistics.builder()
                        .serviceName("user-service")
                        .totalErrorCount(100L)
                        .errorRate(5.0)
                        .rank(1)
                        .mostFrequentErrorType("VALIDATION_ERROR")
                        .build(),
                ErrorStatistics.builder()
                        .serviceName("payment-service")
                        .totalErrorCount(75L)
                        .errorRate(3.5)
                        .rank(2)
                        .mostFrequentErrorType("DATABASE_ERROR")
                        .build()
        );

        // API 호출 통계 Mock 데이터
        mockApiCallRanking = List.of(
                ApiCallStatistics.builder()
                        .apiName("weather-api")
                        .apiProvider("기상청")
                        .totalCallCount(1000L)
                        .successCallCount(950L)
                        .failureCallCount(50L)
                        .successRate(95.0)
                        .averageResponseTime(200.5)
                        .rank(1)
                        .build(),
                ApiCallStatistics.builder()
                        .apiName("traffic-api")
                        .apiProvider("국토교통부")
                        .totalCallCount(800L)
                        .successCallCount(760L)
                        .failureCallCount(40L)
                        .successRate(95.0)
                        .averageResponseTime(150.3)
                        .rank(2)
                        .build()
        );
    }

    @Test
    @DisplayName("대시보드 분석 데이터 조회 성공 테스트")
    void getDashboardAnalytics_Success() {
        // Given
        when(errorAnalyticsService.map(any())).thenReturn(mockErrorRanking);
        when(apiCallAnalyticsService.map(any())).thenReturn(mockApiCallRanking);

        // When
        DashboardResponse result = dashboardService.getDashboardAnalytics(24, 10);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getErrorRanking()).hasSize(2);
        assertThat(result.getApiCallRanking()).hasSize(2);
        
        // 요약 데이터 검증
        DashboardSummary summary = result.getSummary();
        assertThat(summary.getTotalErrors()).isEqualTo(175L); // 100 + 75
        assertThat(summary.getTotalApiCalls()).isEqualTo(1800L); // 1000 + 800
        assertThat(summary.getActiveServiceCount()).isEqualTo(2);
        assertThat(summary.getMonitoredApiCount()).isEqualTo(2);
        assertThat(summary.getSystemStatus()).isEqualTo("HEALTHY");
        
        // 분석 기간 검증
        AnalysisPeriod period = result.getAnalysisPeriod();
        assertThat(period.getDurationHours()).isEqualTo(24L);
        assertThat(period.getDescription()).isEqualTo("최근 24시간");
    }

    @Test
    @DisplayName("빈 데이터로 대시보드 분석 테스트")
    void getDashboardAnalytics_EmptyData() {
        // Given
        when(errorAnalyticsService.map(any())).thenReturn(List.of());
        when(apiCallAnalyticsService.map(any())).thenReturn(List.of());

        // When
        DashboardResponse result = dashboardService.getDashboardAnalytics(24, 10);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getErrorRanking()).isEmpty();
        assertThat(result.getApiCallRanking()).isEmpty();
        
        DashboardSummary summary = result.getSummary();
        assertThat(summary.getTotalErrors()).isEqualTo(0L);
        assertThat(summary.getTotalApiCalls()).isEqualTo(0L);
        assertThat(summary.getOverallSuccessRate()).isEqualTo(0.0);
        assertThat(summary.getActiveServiceCount()).isEqualTo(0);
        assertThat(summary.getMonitoredApiCount()).isEqualTo(0);
        assertThat(summary.getSystemStatus()).isEqualTo("HEALTHY");
    }

    @Test
    @DisplayName("기본값으로 대시보드 분석 테스트")
    void getDashboardAnalytics_DefaultValues() {
        // Given
        when(errorAnalyticsService.map(any())).thenReturn(mockErrorRanking);
        when(apiCallAnalyticsService.map(any())).thenReturn(mockApiCallRanking);

        // When
        DashboardResponse result = dashboardService.getDashboardAnalytics(null, null);

        // Then
        assertThat(result).isNotNull();
        AnalysisPeriod period = result.getAnalysisPeriod();
        assertThat(period.getDurationHours()).isEqualTo(24L); // 기본값
    }

    @Test
    @DisplayName("시스템 상태 CRITICAL 판정 테스트")
    void getDashboardAnalytics_CriticalStatus() {
        // Given
        List<ErrorStatistics> highErrorStats = List.of(
                ErrorStatistics.builder()
                        .serviceName("critical-service")
                        .totalErrorCount(1500L) // 임계치 초과
                        .errorRate(25.0)
                        .rank(1)
                        .build()
        );

        List<ApiCallStatistics> lowSuccessRateStats = List.of(
                ApiCallStatistics.builder()
                        .apiName("failing-api")
                        .totalCallCount(1000L)
                        .successCallCount(800L)
                        .failureCallCount(200L)
                        .successRate(80.0) // 90% 미만
                        .rank(1)
                        .build()
        );

        when(errorAnalyticsService.map(any())).thenReturn(highErrorStats);
        when(apiCallAnalyticsService.map(any())).thenReturn(lowSuccessRateStats);

        // When
        DashboardResponse result = dashboardService.getDashboardAnalytics(24, 10);

        // Then
        assertThat(result.getSummary().getSystemStatus()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("시스템 상태 WARNING 판정 테스트")
    void getDashboardAnalytics_WarningStatus() {
        // Given
        List<ErrorStatistics> moderateErrorStats = List.of(
                ErrorStatistics.builder()
                        .serviceName("warning-service")
                        .totalErrorCount(750L) // WARNING 임계치
                        .errorRate(10.0)
                        .rank(1)
                        .build()
        );

        List<ApiCallStatistics> moderateSuccessRateStats = List.of(
                ApiCallStatistics.builder()
                        .apiName("degraded-api")
                        .totalCallCount(1000L)
                        .successCallCount(930L)
                        .failureCallCount(70L)
                        .successRate(93.0) // 95% 미만
                        .rank(1)
                        .build()
        );

        when(errorAnalyticsService.map(any())).thenReturn(moderateErrorStats);
        when(apiCallAnalyticsService.map(any())).thenReturn(moderateSuccessRateStats);

        // When
        DashboardResponse result = dashboardService.getDashboardAnalytics(24, 10);

        // Then
        assertThat(result.getSummary().getSystemStatus()).isEqualTo("WARNING");
    }

    @Test
    @DisplayName("전체 성공률 가중평균 계산 테스트")
    void getDashboardAnalytics_WeightedAverageSuccessRate() {
        // Given
        List<ApiCallStatistics> mixedSuccessRateStats = List.of(
                ApiCallStatistics.builder()
                        .apiName("high-volume-api")
                        .totalCallCount(2000L) // 높은 호출량
                        .successRate(90.0)
                        .rank(1)
                        .build(),
                ApiCallStatistics.builder()
                        .apiName("low-volume-api")
                        .totalCallCount(100L)  // 낮은 호출량
                        .successRate(99.0)
                        .rank(2)
                        .build()
        );

        when(errorAnalyticsService.map(any())).thenReturn(List.of());
        when(apiCallAnalyticsService.map(any())).thenReturn(mixedSuccessRateStats);

        // When
        DashboardResponse result = dashboardService.getDashboardAnalytics(24, 10);

        // Then
        // 가중평균: (2000*90 + 100*99) / 2100 = 90.43
        DashboardSummary summary = result.getSummary();
        assertThat(summary.getOverallSuccessRate()).isCloseTo(90.43, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    @DisplayName("시간 범위별 설명 생성 테스트")
    void getDashboardAnalytics_PeriodDescriptions() {
        // Given
        when(errorAnalyticsService.map(any())).thenReturn(List.of());
        when(apiCallAnalyticsService.map(any())).thenReturn(List.of());

        // When & Then
        DashboardResponse result1 = dashboardService.getDashboardAnalytics(1, 10);
        assertThat(result1.getAnalysisPeriod().getDescription()).isEqualTo("최근 1시간");

        DashboardResponse result2 = dashboardService.getDashboardAnalytics(12, 10);
        assertThat(result2.getAnalysisPeriod().getDescription()).isEqualTo("최근 12시간");

        DashboardResponse result3 = dashboardService.getDashboardAnalytics(48, 10);
        assertThat(result3.getAnalysisPeriod().getDescription()).isEqualTo("최근 2일");

        DashboardResponse result4 = dashboardService.getDashboardAnalytics(336, 10);
        assertThat(result4.getAnalysisPeriod().getDescription()).isEqualTo("최근 2주");
    }

    @Test
    @DisplayName("서비스 예외 발생 시 빈 대시보드 응답 테스트")
    void getDashboardAnalytics_ServiceException() {
        // Given
        when(errorAnalyticsService.map(any())).thenThrow(new RuntimeException("Elasticsearch 연결 실패"));
        when(apiCallAnalyticsService.map(any())).thenReturn(mockApiCallRanking);

        // When
        DashboardResponse result = dashboardService.getDashboardAnalytics(24, 10);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getErrorRanking()).isEmpty();
        assertThat(result.getApiCallRanking()).isEmpty();
        assertThat(result.getSummary().getSystemStatus()).isEqualTo("UNKNOWN");
        assertThat(result.getSummary().getTotalErrors()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Optional 서비스가 empty인 경우 테스트")
    void getDashboardAnalytics_OptionalServicesEmpty() {
        // Given
        when(errorAnalyticsService.map(any())).thenReturn(List.of());
        when(apiCallAnalyticsService.map(any())).thenReturn(List.of());

        // When
        DashboardResponse result = dashboardService.getDashboardAnalytics(24, 10);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getErrorRanking()).isEmpty();
        assertThat(result.getApiCallRanking()).isEmpty();
        assertThat(result.getSummary().getSystemStatus()).isEqualTo("HEALTHY");
    }

    @Test
    @DisplayName("대용량 시간 범위 처리 테스트")
    void getDashboardAnalytics_LargeTimeRange() {
        // Given
        when(errorAnalyticsService.map(any())).thenReturn(mockErrorRanking);
        when(apiCallAnalyticsService.map(any())).thenReturn(mockApiCallRanking);

        // When
        DashboardResponse result = dashboardService.getDashboardAnalytics(8760, 50); // 1년, 50개 제한

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAnalysisPeriod().getDurationHours()).isEqualTo(8760L);
        assertThat(result.getAnalysisPeriod().getDescription()).isEqualTo("최근 12개월");
    }
}