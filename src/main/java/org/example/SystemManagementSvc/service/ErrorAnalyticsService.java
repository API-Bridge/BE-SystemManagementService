package org.example.SystemManagementSvc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.SystemManagementSvc.dto.analytics.ErrorStatistics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * ELK 스택 기반 에러 분석 서비스
 * Elasticsearch와 연동하여 실제 로그 데이터에서 서비스별 에러 통계 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class ErrorAnalyticsService {

    private final ElasticsearchService elasticsearchService;

    /**
     * 서비스별 에러 발생 순위를 계산
     * Elasticsearch에서 로그 데이터를 조회하여 실제 에러 통계를 제공
     *
     * @param startTime 분석 시작 시간
     * @param endTime   분석 종료 시간
     * @param limit     반환할 최대 개수
     * @return 에러 발생 순위 리스트 (에러 횟수 내림차순)
     */
    public List<ErrorStatistics> getServiceErrorRanking(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        // 유효성 검사
        if (startTime.isAfter(endTime)) {
            log.warn("Invalid time range: startTime {} is after endTime {}", startTime, endTime);
            return Collections.emptyList();
        }

        log.info("Generating error ranking from Elasticsearch for period: {} to {}", startTime, endTime);
        
        try {
            // Elasticsearch에서 실제 에러 통계 조회
            return elasticsearchService.getServiceErrorStatistics(startTime, endTime, limit);
                
        } catch (Exception e) {
            log.error("Failed to generate error analytics from Elasticsearch", e);
            return Collections.emptyList();
        }
    }

    /**
     * 특정 서비스의 상세 에러 분석
     * 
     * @param serviceName 분석할 서비스명
     * @param startTime   분석 시작 시간
     * @param endTime     분석 종료 시간
     * @return 해당 서비스의 에러 통계
     */
    public ErrorStatistics getServiceErrorDetail(String serviceName, LocalDateTime startTime, LocalDateTime endTime) {
        log.info("Getting error details for service: {} from {} to {}", serviceName, startTime, endTime);
        
        List<ErrorStatistics> allStats = elasticsearchService.getServiceErrorStatistics(startTime, endTime, 100);
        
        return allStats.stream()
            .filter(stat -> stat.getServiceName().equals(serviceName))
            .findFirst()
            .orElse(null);
    }

    /**
     * 전체 시스템의 에러 트렌드 분석
     * 시간대별 에러 발생 패턴을 분석하여 피크 시간대와 안정 시간대 식별
     * 
     * @param hours 분석할 시간 범위 (시간 단위)
     * @return 시간대별 에러 발생 통계
     */
    public List<ErrorStatistics> getErrorTrend(int hours) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);
        
        log.info("Analyzing error trend for last {} hours", hours);
        
        return elasticsearchService.getServiceErrorStatistics(startTime, endTime, 20);
    }
}