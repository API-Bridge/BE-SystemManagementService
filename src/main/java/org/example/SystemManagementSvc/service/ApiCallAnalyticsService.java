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
     * 
     * 실제 구현 시 Elasticsearch 쿼리 예시:
     * - API 별 그룹핑: terms aggregation on api.name
     * - 성공/실패 나누기: filter aggregation on response.status
     * - 응답시간 통계: avg/max aggregation on response.time
     * - 시간대별 추이: date_histogram aggregation
     */
    private List<ApiCallStatistics> createMockApiCallStatistics() {
        List<ApiCallStatistics> mockData = new ArrayList<>();
        
        // 주요 공공데이터 API들의 실제 호출 패턴을 모사하여 생성
        
        // 날씨 API: 가장 빈번하게 호출되는 API (실시간 날씨 정보 제공)
        mockData.add(ApiCallStatistics.builder()
            .apiName("weather-api")
            .apiProvider("기상청")             // 공공데이터 제공처
            .totalCallCount(1250L)        // 24시간 기준 총 호출 횟수
            .successCallCount(1200L)      // 성공한 호출 횟수
            .failureCallCount(50L)        // 실패한 호출 횟수
            .successRate(96.0)            // 성공률 (%)
            .averageResponseTime(250.5)   // 평균 응답시간 (ms)
            .maxResponseTime(1500L)       // 최대 응답시간 (ms)
            .lastCallTime(LocalDateTime.now().minusMinutes(5).format(ISO_FORMATTER))  // 마지막 호출 시각
            .rank(1)                      // 호출량 기준 순위
            .build());
            
        // 교통정보 API: 출퇴근 시간대에 호출량 증가
        mockData.add(ApiCallStatistics.builder()
            .apiName("traffic-api")
            .apiProvider("국토교통부")       // TOPIS 등 교통정보 API
            .totalCallCount(890L)
            .successCallCount(850L)
            .failureCallCount(40L)
            .successRate(95.5)
            .averageResponseTime(180.3)   // 비교적 빠른 응답속도
            .maxResponseTime(2100L)       // 피크 시간대 지연 발생
            .lastCallTime(LocalDateTime.now().minusMinutes(8).format(ISO_FORMATTER))
            .rank(2)
            .build());
            
        // 시설정보 API: 지역별 공공시설 정보 제공
        mockData.add(ApiCallStatistics.builder()
            .apiName("facility-api")
            .apiProvider("서울시청")         // 지자체 열린데이터 포탈
            .totalCallCount(456L)
            .successCallCount(440L)
            .failureCallCount(16L)
            .successRate(96.5)            // 높은 성공률 유지
            .averageResponseTime(320.1)   // 상대적으로 느린 응답 (데이터 량 많음)
            .maxResponseTime(3200L)       // 대용량 데이터로 인한 지연
            .lastCallTime(LocalDateTime.now().minusMinutes(15).format(ISO_FORMATTER))
            .rank(3)
            .build());
            
        // 뉴스 API: 주기적으로 호출되는 콘텐츠 API
        mockData.add(ApiCallStatistics.builder()
            .apiName("news-api")
            .apiProvider("한국언론진흥재단")  // 언론진흥재단 BIG뉴스 API
            .totalCallCount(234L)
            .successCallCount(220L)
            .failureCallCount(14L)
            .successRate(94.0)            // 변동성이 있는 성공률
            .averageResponseTime(450.8)   // 콘텐츠 처리로 인한 느린 응답
            .maxResponseTime(4800L)       // 콘텐츠 양에 따른 지연 발생 가능
            .lastCallTime(LocalDateTime.now().minusMinutes(30).format(ISO_FORMATTER))
            .rank(4)
            .build());
            
        // 대중교통 API: 가장 안정적인 API (성능도 우수)
        mockData.add(ApiCallStatistics.builder()
            .apiName("public-transport-api")
            .apiProvider("국토교통부")       // 버스 지하철 실시간 정보
            .totalCallCount(189L)         // 상대적으로 적은 호출량
            .successCallCount(185L)
            .failureCallCount(4L)         // 매우 낮은 실패율
            .successRate(97.9)            // 가장 높은 성공률 (안정적인 인프라)
            .averageResponseTime(150.2)   // 가장 빠른 평균 응답시간
            .maxResponseTime(800L)        // 낮은 최대 응답시간 (안정적)
            .lastCallTime(LocalDateTime.now().minusMinutes(12).format(ISO_FORMATTER))
            .rank(5)
            .build());
            
        return mockData;
    }
}