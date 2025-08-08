package org.example.SystemManagementSvc.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 분석 기간 정보 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisPeriod {
    
    /** 분석 시작 시간 */
    private String startTime;
    
    /** 분석 종료 시간 */
    private String endTime;
    
    /** 분석 기간 (시간 단위) */
    private Long durationHours;
    
    /** 분석 기간 설명 (예: "최근 24시간", "지난 주", "지난 한 달") */
    private String description;
}