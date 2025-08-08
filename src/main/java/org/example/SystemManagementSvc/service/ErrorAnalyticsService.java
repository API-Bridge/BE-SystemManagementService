package org.example.SystemManagementSvc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.SystemManagementSvc.dto.analytics.ErrorStatistics;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ELK 스택 기반 에러 분석 서비스 (간소화 버전)
 * 실제 프로덕션에서는 Elasticsearch 연동을 통해 구현
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorAnalyticsService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * 서비스별 에러 발생 순위를 계산
     * 현재는 Mock 데이터로 구현되어 있으며, 실제로는 Elasticsearch에서 데이터를 조회해야 합니다.
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

        log.info("Generating error ranking for period: {} to {}", startTime, endTime);
        
        try {
            // Mock 데이터 생성 (실제로는 Elasticsearch 쿼리 결과)
            List<ErrorStatistics> mockData = createMockErrorStatistics();
            
            // limit 적용
            return mockData.stream()
                .limit(limit)
                .toList();
                
        } catch (Exception e) {
            log.error("Failed to generate error analytics", e);
            return Collections.emptyList();
        }
    }

    /**
     * Mock 에러 통계 데이터 생성
     * TODO: 실제 Elasticsearch 쿼리로 대체
     */
    private List<ErrorStatistics> createMockErrorStatistics() {
        List<ErrorStatistics> mockData = new ArrayList<>();
        
        mockData.add(ErrorStatistics.builder()
            .serviceName("auth-service")
            .totalErrorCount(150L)
            .errorRate(5.2)
            .mostFrequentErrorType("VALIDATION_ERROR")
            .lastErrorTime(LocalDateTime.now().minusMinutes(10).format(ISO_FORMATTER))
            .rank(1)
            .build());
            
        mockData.add(ErrorStatistics.builder()
            .serviceName("payment-service")
            .totalErrorCount(89L)
            .errorRate(3.1)
            .mostFrequentErrorType("DATABASE_ERROR")
            .lastErrorTime(LocalDateTime.now().minusMinutes(25).format(ISO_FORMATTER))
            .rank(2)
            .build());
            
        mockData.add(ErrorStatistics.builder()
            .serviceName("user-service")
            .totalErrorCount(45L)
            .errorRate(1.8)
            .mostFrequentErrorType("NETWORK_ERROR")
            .lastErrorTime(LocalDateTime.now().minusMinutes(45).format(ISO_FORMATTER))
            .rank(3)
            .build());
            
        mockData.add(ErrorStatistics.builder()
            .serviceName("notification-service")
            .totalErrorCount(32L)
            .errorRate(2.5)
            .mostFrequentErrorType("TIMEOUT_ERROR")
            .lastErrorTime(LocalDateTime.now().minusHours(1).format(ISO_FORMATTER))
            .rank(4)
            .build());
            
        mockData.add(ErrorStatistics.builder()
            .serviceName("api-gateway")
            .totalErrorCount(18L)
            .errorRate(0.9)
            .mostFrequentErrorType("RATE_LIMIT_ERROR")
            .lastErrorTime(LocalDateTime.now().minusHours(2).format(ISO_FORMATTER))
            .rank(5)
            .build());
            
        return mockData;
    }
}