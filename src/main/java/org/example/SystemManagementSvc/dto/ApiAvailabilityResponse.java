package org.example.SystemManagementSvc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API 가용성 상태 응답 DTO
 * 개별 API의 현재 상태와 상세 정보를 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiAvailabilityResponse {
    
    /** API 고유 식별자 */
    private String apiId;
    
    /** 현재 가용 여부 */
    private boolean isAvailable;
    
    /** 상태 (HEALTHY, UNHEALTHY, DEGRADED, TIMEOUT, UNKNOWN) */
    private String status;
    
    /** 상태 메시지 */
    private String message;
    
    /** 응답 시간 (밀리초) */
    private Long responseTimeMs;
    
    /** 연속 실패 횟수 */
    private Integer consecutiveFailures;
    
    /** 마지막 체크 시간 */
    private LocalDateTime checkedAt;
    
    /** Redis TTL 남은 시간 (초) */
    private Long ttlSeconds;
    
    /** 예상 복구 시간 */
    private LocalDateTime estimatedRecoveryTime;
    
    /** 추가 정보 */
    private String additionalInfo;
    
    /**
     * 복구 예상 시간 계산
     */
    public LocalDateTime getEstimatedRecoveryTime() {
        if (isAvailable || ttlSeconds == null || ttlSeconds <= 0) {
            return null;
        }
        return LocalDateTime.now().plusSeconds(ttlSeconds);
    }
    
    /**
     * 장애 지속 시간 계산 (분)
     */
    public Long getOutageDurationMinutes() {
        if (isAvailable || checkedAt == null) {
            return 0L;
        }
        return java.time.Duration.between(checkedAt, LocalDateTime.now()).toMinutes();
    }
    
    /**
     * 상태에 따른 심각도 레벨
     */
    public SeverityLevel getSeverityLevel() {
        if (isAvailable) {
            return SeverityLevel.NONE;
        }
        
        return switch (status) {
            case "TIMEOUT" -> SeverityLevel.HIGH;
            case "UNHEALTHY" -> SeverityLevel.CRITICAL;
            case "DEGRADED" -> SeverityLevel.MEDIUM;
            case "UNKNOWN" -> SeverityLevel.LOW;
            default -> SeverityLevel.LOW;
        };
    }
    
    /**
     * 심각도 레벨 열거형
     */
    public enum SeverityLevel {
        NONE("정상"),
        LOW("낮음"),
        MEDIUM("보통"),
        HIGH("높음"),
        CRITICAL("심각");
        
        private final String description;
        
        SeverityLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}