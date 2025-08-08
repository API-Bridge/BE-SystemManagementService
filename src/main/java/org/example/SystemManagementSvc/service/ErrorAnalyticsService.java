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
     * 
     * 실제 구현 시 Elasticsearch 쿼리 예시:
     * - 시간 범위 필터: range query on @timestamp
     * - 서비스별 그룹핑: terms aggregation on service.name
     * - 에러 카운트: count aggregation
     * - 에러 타입별 분석: terms aggregation on error.type
     */
    private List<ErrorStatistics> createMockErrorStatistics() {
        List<ErrorStatistics> mockData = new ArrayList<>();
        
        // 각 서비스별로 실제 운영 환경에서 발생할 수 있는 대표적인 에러 패턴을 모사
        
        // 인증 서비스: 가장 많은 에러 발생 (입력값 검증 실패가 주요 원인)
        mockData.add(ErrorStatistics.builder()
            .serviceName("auth-service")
            .totalErrorCount(150L)  // 24시간 기준 에러 발생 횟수
            .errorRate(5.2)         // 전체 요청 대비 에러 비율 (%)
            .mostFrequentErrorType("VALIDATION_ERROR")  // 가장 빈번한 에러 타입
            .lastErrorTime(LocalDateTime.now().minusMinutes(10).format(ISO_FORMATTER))
            .rank(1)                // 에러 발생량 기준 순위
            .build());
            
        // 결제 서비스: DB 연결 문제로 인한 에러가 주요 원인
        mockData.add(ErrorStatistics.builder()
            .serviceName("payment-service")
            .totalErrorCount(89L)
            .errorRate(3.1)
            .mostFrequentErrorType("DATABASE_ERROR")  // DB 트랜잭션 실패, 연결 타임아웃 등
            .lastErrorTime(LocalDateTime.now().minusMinutes(25).format(ISO_FORMATTER))
            .rank(2)
            .build());
            
        // 사용자 서비스: 외부 API 호출 시 네트워크 에러
        mockData.add(ErrorStatistics.builder()
            .serviceName("user-service")
            .totalErrorCount(45L)
            .errorRate(1.8)
            .mostFrequentErrorType("NETWORK_ERROR")  // 외부 서비스 연동 실패
            .lastErrorTime(LocalDateTime.now().minusMinutes(45).format(ISO_FORMATTER))
            .rank(3)
            .build());
            
        // 알림 서비스: 메시지 큐 처리 지연으로 인한 타임아웃
        mockData.add(ErrorStatistics.builder()
            .serviceName("notification-service")
            .totalErrorCount(32L)
            .errorRate(2.5)
            .mostFrequentErrorType("TIMEOUT_ERROR")  // 메시지 브로커 연결 지연
            .lastErrorTime(LocalDateTime.now().minusHours(1).format(ISO_FORMATTER))
            .rank(4)
            .build());
            
        // API 게이트웨이: 속도 제한으로 인한 에러 (비교적 안정적)
        mockData.add(ErrorStatistics.builder()
            .serviceName("api-gateway")
            .totalErrorCount(18L)
            .errorRate(0.9)         // 가장 낮은 에러율 (잘 관리되고 있음)
            .mostFrequentErrorType("RATE_LIMIT_ERROR")  // 클라이언트 호출 제한 초과
            .lastErrorTime(LocalDateTime.now().minusHours(2).format(ISO_FORMATTER))
            .rank(5)
            .build());
            
        return mockData;
    }
}