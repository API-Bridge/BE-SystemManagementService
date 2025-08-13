package org.example.SystemManagementSvc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.SystemManagementSvc.dto.analytics.ApiCallStatistics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ELK 스택 기반 외부 API 호출 분석 서비스
 * Elasticsearch와 연동하여 실제 API 호출 로그에서 통계 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class ApiCallAnalyticsService {

    private final ElasticsearchService elasticsearchService;

    /**
     * 자주 호출되는 외부 API 순위를 계산
     * Elasticsearch에서 로그 데이터를 조회하여 실제 API 호출 통계를 제공
     *
     * @param startTime 분석 시작 시간
     * @param endTime   분석 종료 시간
     * @param limit     반환할 최대 개수
     * @return API 호출 순위 리스트 (호출 횟수 내림차순)
     */
    public List<ApiCallStatistics> getApiCallRanking(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        // 유효성 검사
        if (startTime.isAfter(endTime)) {
            log.warn("Invalid time range: startTime {} is after endTime {}", startTime, endTime);
            return Collections.emptyList();
        }

        log.info("Generating API call ranking from Elasticsearch for period: {} to {}", startTime, endTime);
        
        try {
            // Elasticsearch에서 실제 API 호출 통계 조회
            return elasticsearchService.getApiCallStatistics(startTime, endTime, limit);
                
        } catch (Exception e) {
            log.error("Failed to generate API call analytics from Elasticsearch", e);
            return Collections.emptyList();
        }
    }

    /**
     * 특정 API의 상세 호출 분석
     * 
     * @param apiName   분석할 API명
     * @param startTime 분석 시작 시간
     * @param endTime   분석 종료 시간
     * @return 해당 API의 호출 통계
     */
    public ApiCallStatistics getApiCallDetail(String apiName, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("Getting API call details for: {} from {} to {}", apiName, startTime, endTime);
        
        List<ApiCallStatistics> allStats = elasticsearchService.getApiCallStatistics(startTime, endTime, 100);
        
        return allStats.stream()
            .filter(stat -> stat.getApiName().equals(apiName))
            .findFirst()
            .orElse(null);
    }

    /**
     * API 제공업체별 호출 통계 분석
     * 
     * @param startTime 분석 시작 시간
     * @param endTime   분석 종료 시간
     * @return 제공업체별 호출량 집계
     */
    public Map<String, Long> getApiCallsByProvider(LocalDateTime startTime, LocalDateTime endTime) {
        log.info("Analyzing API calls by provider from {} to {}", startTime, endTime);
        
        List<ApiCallStatistics> allStats = elasticsearchService.getApiCallStatistics(startTime, endTime, 50);
        
        return allStats.stream()
            .collect(Collectors.groupingBy(
                ApiCallStatistics::getApiProvider,
                Collectors.summingLong(ApiCallStatistics::getTotalCallCount)
            ));
    }

    /**
     * 성능 문제가 있는 API 식별
     * 평균 응답시간이 임계치를 초과하는 API들을 식별
     * 
     * @param startTime           분석 시작 시간
     * @param endTime             분석 종료 시간
     * @param responseTimeThreshold 응답시간 임계치 (ms)
     * @return 성능 문제 API 리스트
     */
    public List<ApiCallStatistics> getSlowApis(LocalDateTime startTime, LocalDateTime endTime, double responseTimeThreshold) {
        log.info("Identifying slow APIs with response time > {}ms", responseTimeThreshold);
        
        List<ApiCallStatistics> allStats = elasticsearchService.getApiCallStatistics(startTime, endTime, 50);
        
        return allStats.stream()
            .filter(stat -> stat.getAverageResponseTime() > responseTimeThreshold)
            .sorted((a, b) -> Double.compare(b.getAverageResponseTime(), a.getAverageResponseTime()))
            .collect(Collectors.toList());
    }

    /**
     * 신뢰도가 낮은 API 식별
     * 성공률이 임계치 미만인 API들을 식별
     * 
     * @param startTime           분석 시작 시간
     * @param endTime             분석 종료 시간
     * @param successRateThreshold 성공률 임계치 (%)
     * @return 신뢰도가 낮은 API 리스트
     */
    public List<ApiCallStatistics> getUnreliableApis(LocalDateTime startTime, LocalDateTime endTime, double successRateThreshold) {
        log.info("Identifying unreliable APIs with success rate < {}%", successRateThreshold);
        
        List<ApiCallStatistics> allStats = elasticsearchService.getApiCallStatistics(startTime, endTime, 50);
        
        return allStats.stream()
            .filter(stat -> stat.getSuccessRate() < successRateThreshold)
            .sorted((a, b) -> Double.compare(a.getSuccessRate(), b.getSuccessRate()))
            .collect(Collectors.toList());
    }

    /**
     * API 호출량 트렌드 분석
     * 시간대별 API 호출 패턴을 분석하여 피크 시간대 식별
     * 
     * @param hours 분석할 시간 범위 (시간 단위)
     * @return API 호출량 통계
     */
    public List<ApiCallStatistics> getApiCallTrend(int hours) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);
        
        log.info("Analyzing API call trend for last {} hours", hours);
        
        return elasticsearchService.getApiCallStatistics(startTime, endTime, 20);
    }

    /**
     * 과도한 호출이 발생하고 있는 API 감지
     * 호출량이 평균 대비 비정상적으로 높은 API들을 식별
     * 
     * @param startTime     분석 시작 시간
     * @param endTime       분석 종료 시간
     * @param thresholdMultiplier 평균 대비 배수 (예: 2.0 = 평균의 2배)
     * @return 과도한 호출이 발생한 API 리스트
     */
    public List<ApiCallStatistics> getExcessiveCallApis(LocalDateTime startTime, LocalDateTime endTime, double thresholdMultiplier) {
        log.info("Detecting excessive API calls with threshold multiplier: {}", thresholdMultiplier);
        
        List<ApiCallStatistics> allStats = elasticsearchService.getApiCallStatistics(startTime, endTime, 50);
        
        if (allStats.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 평균 호출량 계산
        double averageCallCount = allStats.stream()
            .mapToLong(ApiCallStatistics::getTotalCallCount)
            .average()
            .orElse(0.0);
        
        double threshold = averageCallCount * thresholdMultiplier;
        
        return allStats.stream()
            .filter(stat -> stat.getTotalCallCount() > threshold)
            .sorted((a, b) -> Long.compare(b.getTotalCallCount(), a.getTotalCallCount()))
            .collect(Collectors.toList());
    }
}