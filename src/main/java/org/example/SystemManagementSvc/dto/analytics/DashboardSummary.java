package org.example.SystemManagementSvc.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 대시보드 전체 통계 요약 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummary {
    
    /** 총 에러 발생 횟수 */
    private Long totalErrors;
    
    /** 총 API 호출 횟수 */
    private Long totalApiCalls;
    
    /** 전체 API 평균 성공률 */
    private Double overallSuccessRate;
    
    /** 활성 서비스 수 */
    private Integer activeServiceCount;
    
    /** 모니터링 중인 외부 API 수 */
    private Integer monitoredApiCount;
    
    /** 시스템 상태 (HEALTHY, WARNING, CRITICAL) */
    private String systemStatus;
}