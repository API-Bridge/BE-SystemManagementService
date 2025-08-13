package org.example.SystemManagementSvc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.SystemManagementSvc.domain.ExternalApi;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * API 상태 요약 정보 DTO
 * 전체 시스템의 API 가용성 현황을 요약
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiStatusSummary {
    
    /** 전체 API 수 */
    private Long totalApis;
    
    /** 활성화된 API 수 */
    private Long effectiveApis;
    
    /** 현재 가용한 API 수 */
    private Long availableApis;
    
    /** 현재 불가용한 API 수 */
    private Long unavailableApis;
    
    /** 가용성 비율 (%) */
    private Double availabilityRate;
    
    /** 도메인별 가용 API 통계 */
    private Map<ExternalApi.ApiDomain, Long> domainStats;
    
    /** 우선순위별 가용 API 통계 */
    private Map<ExternalApi.HealthCheckPriority, Long> priorityStats;
    
    /** 마지막 업데이트 시간 */
    private LocalDateTime lastUpdated;
    
    /** 최근 24시간 장애 발생 횟수 */
    private Long incidentsLast24Hours;
    
    /** 평균 복구 시간 (분) */
    private Double averageRecoveryTimeMinutes;
    
    /** 시스템 전체 건강도 */
    private SystemHealth systemHealth;
    
    /**
     * 시스템 건강도 계산
     */
    public SystemHealth getSystemHealth() {
        if (effectiveApis == 0) {
            return SystemHealth.UNKNOWN;
        }
        
        double rate = availabilityRate != null ? availabilityRate : 0.0;
        
        if (rate >= 99.0) {
            return SystemHealth.EXCELLENT;
        } else if (rate >= 95.0) {
            return SystemHealth.GOOD;
        } else if (rate >= 90.0) {
            return SystemHealth.FAIR;
        } else if (rate >= 80.0) {
            return SystemHealth.POOR;
        } else {
            return SystemHealth.CRITICAL;
        }
    }
    
    /**
     * 가용성 상태 텍스트
     */
    public String getAvailabilityStatusText() {
        SystemHealth health = getSystemHealth();
        return switch (health) {
            case EXCELLENT -> "매우 양호";
            case GOOD -> "양호";
            case FAIR -> "보통";
            case POOR -> "주의";
            case CRITICAL -> "심각";
            case UNKNOWN -> "알 수 없음";
        };
    }
    
    /**
     * 도메인별 장애율 계산
     */
    public Map<ExternalApi.ApiDomain, Double> getDomainFailureRates() {
        if (domainStats == null || domainStats.isEmpty()) {
            return Map.of();
        }
        
        return domainStats.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    long totalInDomain = getTotalApisByDomain(entry.getKey());
                    if (totalInDomain == 0) return 0.0;
                    
                    long availableInDomain = entry.getValue();
                    return (double) (totalInDomain - availableInDomain) / totalInDomain * 100;
                }
            ));
    }
    
    /**
     * 우선순위별 가용성 비율
     */
    public Map<ExternalApi.HealthCheckPriority, Double> getPriorityAvailabilityRates() {
        if (priorityStats == null || priorityStats.isEmpty()) {
            return Map.of();
        }
        
        return priorityStats.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    long totalInPriority = getTotalApisByPriority(entry.getKey());
                    if (totalInPriority == 0) return 100.0;
                    
                    long availableInPriority = entry.getValue();
                    return (double) availableInPriority / totalInPriority * 100;
                }
            ));
    }
    
    /**
     * 간단한 상태 정보 (대시보드용)
     */
    public String getSimpleStatus() {
        return String.format("가용: %d/%d (%.1f%%)", 
            availableApis, effectiveApis, availabilityRate);
    }
    
    /**
     * 상세 상태 정보 (리포트용)
     */
    public String getDetailedStatus() {
        return String.format(
            "전체 API: %d개, 활성: %d개, 가용: %d개, 불가용: %d개 (가용률: %.2f%%)",
            totalApis, effectiveApis, availableApis, unavailableApis, availabilityRate
        );
    }
    
    // Helper methods for calculating domain/priority totals
    // These would need to be implemented based on your specific requirements
    private long getTotalApisByDomain(ExternalApi.ApiDomain domain) {
        // This would be implemented by injecting repository or service
        return 1; // Placeholder
    }
    
    private long getTotalApisByPriority(ExternalApi.HealthCheckPriority priority) {
        // This would be implemented by injecting repository or service
        return 1; // Placeholder
    }
    
    /**
     * 시스템 건강도 열거형
     */
    public enum SystemHealth {
        EXCELLENT("최고", "#28a745"),
        GOOD("양호", "#17a2b8"),
        FAIR("보통", "#ffc107"),
        POOR("주의", "#fd7e14"),
        CRITICAL("심각", "#dc3545"),
        UNKNOWN("알 수 없음", "#6c757d");
        
        private final String description;
        private final String colorCode;
        
        SystemHealth(String description, String colorCode) {
            this.description = description;
            this.colorCode = colorCode;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getColorCode() {
            return colorCode;
        }
    }
}