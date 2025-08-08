package org.example.SystemManagementSvc.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 외부 API 호출 통계 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiCallStatistics {
    
    /** API 이름 */
    private String apiName;
    
    /** API 제공업체 */
    private String apiProvider;
    
    /** 총 호출 횟수 */
    private Long totalCallCount;
    
    /** 성공 호출 횟수 */
    private Long successCallCount;
    
    /** 실패 호출 횟수 */
    private Long failureCallCount;
    
    /** 성공률 */
    private Double successRate;
    
    /** 평균 응답 시간 (밀리초) */
    private Double averageResponseTime;
    
    /** 최대 응답 시간 (밀리초) */
    private Long maxResponseTime;
    
    /** 최근 호출 시간 */
    private String lastCallTime;
    
    /** 순위 */
    private Integer rank;
}