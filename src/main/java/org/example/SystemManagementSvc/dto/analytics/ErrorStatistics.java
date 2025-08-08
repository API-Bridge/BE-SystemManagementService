package org.example.SystemManagementSvc.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 서비스별 에러 발생 통계 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorStatistics {
    
    /** 서비스명 */
    private String serviceName;
    
    /** 총 에러 발생 횟수 */
    private Long totalErrorCount;
    
    /** 에러 발생률 (전체 요청 대비 에러 비율) */
    private Double errorRate;
    
    /** 가장 빈번한 에러 유형 */
    private String mostFrequentErrorType;
    
    /** 최근 에러 발생 시간 */
    private String lastErrorTime;
    
    /** 순위 */
    private Integer rank;
}