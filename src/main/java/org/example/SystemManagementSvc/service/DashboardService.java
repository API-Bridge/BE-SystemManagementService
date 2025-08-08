package org.example.SystemManagementSvc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.SystemManagementSvc.dto.analytics.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 대시보드 통합 분석 서비스
 * ELK 기반 에러 분석과 API 호출 분석을 통합하여 대시보드 데이터 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ErrorAnalyticsService errorAnalyticsService;
    private final ApiCallAnalyticsService apiCallAnalyticsService;
    
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * 전체 대시보드 분석 데이터를 조회
     *
     * @param hours 분석할 시간 범위 (시간 단위, 기본 24시간)
     * @param limit 순위별로 반환할 최대 개수 (기본 10개)
     * @return 대시보드 통합 분석 결과
     */
    public DashboardResponse getDashboardAnalytics(Integer hours, Integer limit) {
        // 기본값 설정
        int analysisHours = hours != null ? hours : 24;
        int resultLimit = limit != null ? limit : 10;
        
        // 분석 시간 범위 계산
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(analysisHours);
        
        log.info("Generating dashboard analytics for period: {} to {}", startTime, endTime);
        
        try {
            // 에러 순위 분석
            List<ErrorStatistics> errorRanking = errorAnalyticsService
                .getServiceErrorRanking(startTime, endTime, resultLimit);
            
            // API 호출 순위 분석
            List<ApiCallStatistics> apiCallRanking = apiCallAnalyticsService
                .getApiCallRanking(startTime, endTime, resultLimit);
            
            // 전체 통계 요약 계산
            DashboardSummary summary = calculateDashboardSummary(errorRanking, apiCallRanking);
            
            // 분석 기간 정보 생성
            AnalysisPeriod analysisPeriod = createAnalysisPeriod(startTime, endTime, analysisHours);
            
            return DashboardResponse.builder()
                .errorRanking(errorRanking)
                .apiCallRanking(apiCallRanking)
                .summary(summary)
                .analysisPeriod(analysisPeriod)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to generate dashboard analytics", e);
            return createEmptyDashboardResponse(startTime, endTime, analysisHours);
        }
    }

    /**
     * 대시보드 전체 통계 요약 계산
     */
    private DashboardSummary calculateDashboardSummary(List<ErrorStatistics> errorRanking, 
                                                       List<ApiCallStatistics> apiCallRanking) {
        
        // 총 에러 발생 횟수 계산
        long totalErrors = errorRanking.stream()
            .mapToLong(ErrorStatistics::getTotalErrorCount)
            .sum();
        
        // 총 API 호출 횟수 계산
        long totalApiCalls = apiCallRanking.stream()
            .mapToLong(ApiCallStatistics::getTotalCallCount)
            .sum();
        
        // 전체 성공률 계산 (가중 평균)
        double overallSuccessRate = apiCallRanking.isEmpty() ? 0.0 : 
            apiCallRanking.stream()
                .mapToDouble(api -> api.getSuccessRate() * api.getTotalCallCount())
                .sum() / totalApiCalls;
        
        // 시스템 상태 결정
        String systemStatus = determineSystemStatus(errorRanking, apiCallRanking, overallSuccessRate);
        
        return DashboardSummary.builder()
            .totalErrors(totalErrors)
            .totalApiCalls(totalApiCalls)
            .overallSuccessRate(Math.round(overallSuccessRate * 100.0) / 100.0)
            .activeServiceCount(errorRanking.size())
            .monitoredApiCount(apiCallRanking.size())
            .systemStatus(systemStatus)
            .build();
    }

    /**
     * 시스템 전체 상태 결정
     */
    private String determineSystemStatus(List<ErrorStatistics> errorRanking, 
                                       List<ApiCallStatistics> apiCallRanking, 
                                       double overallSuccessRate) {
        
        // 임계치 정의
        final double CRITICAL_SUCCESS_RATE = 90.0;
        final double WARNING_SUCCESS_RATE = 95.0;
        final long CRITICAL_ERROR_THRESHOLD = 1000;
        final long WARNING_ERROR_THRESHOLD = 500;
        
        // 가장 많은 에러 발생 서비스의 에러 수
        long maxErrors = errorRanking.isEmpty() ? 0 : 
            errorRanking.get(0).getTotalErrorCount();
        
        // Critical 조건 체크
        if (overallSuccessRate < CRITICAL_SUCCESS_RATE || maxErrors > CRITICAL_ERROR_THRESHOLD) {
            return "CRITICAL";
        }
        
        // Warning 조건 체크
        if (overallSuccessRate < WARNING_SUCCESS_RATE || maxErrors > WARNING_ERROR_THRESHOLD) {
            return "WARNING";
        }
        
        return "HEALTHY";
    }

    /**
     * 분석 기간 정보 생성
     */
    private AnalysisPeriod createAnalysisPeriod(LocalDateTime startTime, LocalDateTime endTime, int hours) {
        String description = createPeriodDescription(hours);
        
        return AnalysisPeriod.builder()
            .startTime(startTime.format(ISO_FORMATTER))
            .endTime(endTime.format(ISO_FORMATTER))
            .durationHours((long) hours)
            .description(description)
            .build();
    }

    /**
     * 분석 기간 설명 생성
     */
    private String createPeriodDescription(int hours) {
        if (hours <= 1) return "최근 1시간";
        if (hours <= 24) return "최근 " + hours + "시간";
        if (hours <= 168) return "최근 " + (hours / 24) + "일";
        if (hours <= 720) return "최근 " + (hours / 168) + "주";
        return "최근 " + (hours / 720) + "개월";
    }

    /**
     * 빈 대시보드 응답 생성 (오류 발생 시)
     */
    private DashboardResponse createEmptyDashboardResponse(LocalDateTime startTime, LocalDateTime endTime, int hours) {
        DashboardSummary emptySummary = DashboardSummary.builder()
            .totalErrors(0L)
            .totalApiCalls(0L)
            .overallSuccessRate(0.0)
            .activeServiceCount(0)
            .monitoredApiCount(0)
            .systemStatus("UNKNOWN")
            .build();
            
        AnalysisPeriod analysisPeriod = createAnalysisPeriod(startTime, endTime, hours);
        
        return DashboardResponse.builder()
            .errorRanking(List.of())
            .apiCallRanking(List.of())
            .summary(emptySummary)
            .analysisPeriod(analysisPeriod)
            .build();
    }
}