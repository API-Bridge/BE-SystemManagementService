package org.example.SystemManagementSvc.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 대시보드 전체 분석 데이터 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    
    /** 서비스별 에러 발생 순위 */
    private List<ErrorStatistics> errorRanking;
    
    /** 외부 API 호출 순위 */
    private List<ApiCallStatistics> apiCallRanking;
    
    /** 전체 통계 요약 */
    private DashboardSummary summary;
    
    /** 분석 기간 정보 */
    private AnalysisPeriod analysisPeriod;
}