package org.example.SystemManagementSvc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.SystemManagementSvc.dto.analytics.ApiCallStatistics;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ELK 스택 기반 외부 API 호출 분석 서비스 (간소화 버전)
 * 실제 프로덕션에서는 Elasticsearch 연동을 통해 구현
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCallAnalyticsService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * 자주 호출되는 외부 API 순위를 계산
     * 현재는 Mock 데이터로 구현되어 있으며, 실제로는 Elasticsearch에서 데이터를 조회해야 합니다.
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

        log.info("Generating API call ranking for period: {} to {}", startTime, endTime);
        
        try {
            // Mock 데이터 생성 (실제로는 Elasticsearch 쿼리 결과)
            List<ApiCallStatistics> mockData = createMockApiCallStatistics();
            
            // limit 적용
            return mockData.stream()
                .limit(limit)
                .toList();
                
        } catch (Exception e) {
            log.error("Failed to generate API call analytics", e);
            return Collections.emptyList();
        }
    }

    /**
     * Mock API 호출 통계 데이터 생성
     * TODO: 실제 Elasticsearch 쿼리로 대체
     */
    private List<ApiCallStatistics> createMockApiCallStatistics() {
        List<ApiCallStatistics> mockData = new ArrayList<>();
        
        mockData.add(ApiCallStatistics.builder()
            .apiName("weather-api")
            .apiProvider("기상청")
            .totalCallCount(1250L)
            .successCallCount(1200L)
            .failureCallCount(50L)
            .successRate(96.0)
            .averageResponseTime(250.5)
            .maxResponseTime(1500L)
            .lastCallTime(LocalDateTime.now().minusMinutes(5).format(ISO_FORMATTER))
            .rank(1)
            .build());
            
        mockData.add(ApiCallStatistics.builder()
            .apiName("traffic-api")
            .apiProvider("국토교통부")
            .totalCallCount(890L)
            .successCallCount(850L)
            .failureCallCount(40L)
            .successRate(95.5)
            .averageResponseTime(180.3)
            .maxResponseTime(2100L)
            .lastCallTime(LocalDateTime.now().minusMinutes(8).format(ISO_FORMATTER))
            .rank(2)
            .build());
            
        mockData.add(ApiCallStatistics.builder()
            .apiName("facility-api")
            .apiProvider("서울시청")
            .totalCallCount(456L)
            .successCallCount(440L)
            .failureCallCount(16L)
            .successRate(96.5)
            .averageResponseTime(320.1)
            .maxResponseTime(3200L)
            .lastCallTime(LocalDateTime.now().minusMinutes(15).format(ISO_FORMATTER))
            .rank(3)
            .build());
            
        mockData.add(ApiCallStatistics.builder()
            .apiName("news-api")
            .apiProvider("한국언론진흥재단")
            .totalCallCount(234L)
            .successCallCount(220L)
            .failureCallCount(14L)
            .successRate(94.0)
            .averageResponseTime(450.8)
            .maxResponseTime(4800L)
            .lastCallTime(LocalDateTime.now().minusMinutes(30).format(ISO_FORMATTER))
            .rank(4)
            .build());
            
        mockData.add(ApiCallStatistics.builder()
            .apiName("public-transport-api")
            .apiProvider("국토교통부")
            .totalCallCount(189L)
            .successCallCount(185L)
            .failureCallCount(4L)
            .successRate(97.9)
            .averageResponseTime(150.2)
            .maxResponseTime(800L)
            .lastCallTime(LocalDateTime.now().minusMinutes(12).format(ISO_FORMATTER))
            .rank(5)
            .build());
            
        return mockData;
    }
}