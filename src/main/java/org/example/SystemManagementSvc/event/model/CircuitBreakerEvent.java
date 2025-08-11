package org.example.SystemManagementSvc.event.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 서킷브레이커 상태 변경 이벤트
 * API 호출량 임계치 초과 또는 장애 감지 시 발생하는 이벤트
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CircuitBreakerEvent extends BaseEvent {

    /** 대상 API ID */
    private String apiId;
    
    /** 대상 API 이름 */
    private String apiName;
    
    /** API 제공업체 */
    private String apiProvider;
    
    /** 서킷브레이커 상태 */
    private CircuitBreakerState state;
    
    /** 이전 상태 */
    private CircuitBreakerState previousState;
    
    /** 상태 변경 원인 */
    private String reason;
    
    /** 상태 변경 트리거 */
    private StateChangeTrigger trigger;
    
    /** 현재 호출량 (분당) */
    private long currentCallRate;
    
    /** 임계치 호출량 */
    private long thresholdCallRate;
    
    /** 현재 실패율 (%) */
    private double currentFailureRate;
    
    /** 임계치 실패율 (%) */
    private double thresholdFailureRate;
    
    /** 연속 실패 횟수 */
    private int consecutiveFailures;
    
    /** 평균 응답시간 (ms) */
    private long averageResponseTime;
    
    /** 서킷브레이커 오픈 지속시간 (초) */
    private long openDurationSeconds;
    
    /** 다음 재시도 시간 */
    private LocalDateTime nextRetryTime;
    
    /** 추가 메타데이터 */
    private Map<String, Object> metadata;
    
    /** 심각도 레벨 */
    private SeverityLevel severity;
    
    /** 자동 복구 가능 여부 */
    private boolean autoRecoverable;
    
    /** 관리자 개입 필요 여부 */
    private boolean requiresManualIntervention;

    /**
     * 서킷브레이커 상태 열거형
     */
    public enum CircuitBreakerState {
        CLOSED("정상", "모든 요청이 정상적으로 처리됨"),
        OPEN("차단", "임계치 초과로 인한 요청 차단"),
        HALF_OPEN("반열림", "제한적 요청으로 상태 확인 중"),
        FORCE_OPEN("강제차단", "관리자에 의한 수동 차단"),
        DEGRADED("성능저하", "성능 저하 상태이나 요청은 허용");

        private final String displayName;
        private final String description;

        CircuitBreakerState(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public boolean isBlocking() {
            return this == OPEN || this == FORCE_OPEN;
        }

        public boolean allowsRequests() {
            return this == CLOSED || this == HALF_OPEN || this == DEGRADED;
        }
    }

    /**
     * 상태 변경 트리거 열거형
     */
    public enum StateChangeTrigger {
        EXCESSIVE_CALLS("과도한 호출량"),
        HIGH_FAILURE_RATE("높은 실패율"),
        CONSECUTIVE_FAILURES("연속 실패"),
        SLOW_RESPONSE("응답 지연"),
        MANUAL_OVERRIDE("수동 제어"),
        AUTO_RECOVERY("자동 복구"),
        HEALTH_CHECK_FAILURE("헬스체크 실패"),
        TIMEOUT("타임아웃"),
        DEPENDENCY_FAILURE("의존성 실패");

        private final String description;

        StateChangeTrigger(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 심각도 레벨 열거형
     */
    public enum SeverityLevel {
        LOW("낮음", 1),
        MEDIUM("보통", 2),
        HIGH("높음", 3),
        CRITICAL("심각", 4),
        EMERGENCY("긴급", 5);

        private final String displayName;
        private final int level;

        SeverityLevel(String displayName, int level) {
            this.displayName = displayName;
            this.level = level;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getLevel() {
            return level;
        }

        public boolean isHighPriority() {
            return this.level >= HIGH.level;
        }
    }

    /**
     * 이벤트 생성 시점에서의 기본 설정
     */
    public static CircuitBreakerEvent create(String apiId, String apiName, String apiProvider) {
        CircuitBreakerEvent event = CircuitBreakerEvent.builder()
            .apiId(apiId)
            .apiName(apiName)
            .apiProvider(apiProvider)
            .build();
        
        // BaseEvent 필드 설정
        event.setEventId(java.util.UUID.randomUUID().toString());
        event.setEventType("CIRCUIT_BREAKER_STATE_CHANGE");
        event.setTimestamp(LocalDateTime.now());
        event.setSourceService("systemmanagement-svc");
        
        return event;
    }

    /**
     * 상태 변경 이벤트 생성 헬퍼
     */
    public static CircuitBreakerEvent stateChange(String apiId, String apiName, String apiProvider,
                                                 CircuitBreakerState newState, CircuitBreakerState oldState,
                                                 StateChangeTrigger trigger, String reason) {
        return create(apiId, apiName, apiProvider)
            .toBuilder()
            .state(newState)
            .previousState(oldState)
            .trigger(trigger)
            .reason(reason)
            .severity(determineSeverity(newState, trigger))
            .autoRecoverable(isAutoRecoverable(newState, trigger))
            .requiresManualIntervention(requiresManualIntervention(newState, trigger))
            .build();
    }

    /**
     * 심각도 자동 결정
     */
    private static SeverityLevel determineSeverity(CircuitBreakerState state, StateChangeTrigger trigger) {
        if (state == CircuitBreakerState.FORCE_OPEN || trigger == StateChangeTrigger.DEPENDENCY_FAILURE) {
            return SeverityLevel.CRITICAL;
        }
        
        if (state == CircuitBreakerState.OPEN) {
            return switch (trigger) {
                case EXCESSIVE_CALLS -> SeverityLevel.HIGH;
                case HIGH_FAILURE_RATE -> SeverityLevel.HIGH;
                case CONSECUTIVE_FAILURES -> SeverityLevel.MEDIUM;
                case TIMEOUT -> SeverityLevel.MEDIUM;
                default -> SeverityLevel.MEDIUM;
            };
        }
        
        if (state == CircuitBreakerState.DEGRADED) {
            return SeverityLevel.MEDIUM;
        }
        
        return SeverityLevel.LOW;
    }

    /**
     * 자동 복구 가능 여부 판단
     */
    private static boolean isAutoRecoverable(CircuitBreakerState state, StateChangeTrigger trigger) {
        return state != CircuitBreakerState.FORCE_OPEN && 
               trigger != StateChangeTrigger.MANUAL_OVERRIDE &&
               trigger != StateChangeTrigger.DEPENDENCY_FAILURE;
    }

    /**
     * 수동 개입 필요 여부 판단
     */
    private static boolean requiresManualIntervention(CircuitBreakerState state, StateChangeTrigger trigger) {
        return state == CircuitBreakerState.FORCE_OPEN ||
               trigger == StateChangeTrigger.DEPENDENCY_FAILURE ||
               trigger == StateChangeTrigger.MANUAL_OVERRIDE;
    }

    /**
     * 이벤트 요약 정보 생성
     */
    public String getSummary() {
        return String.format("[%s] %s: %s → %s (%s)", 
                           severity.getDisplayName(),
                           apiName,
                           previousState != null ? previousState.getDisplayName() : "알수없음",
                           state.getDisplayName(),
                           trigger.getDescription());
    }

    /**
     * 알림 메시지 생성
     */
    public String getNotificationMessage() {
        StringBuilder message = new StringBuilder();
        
        message.append(String.format("🚨 API 서킷브레이커 상태 변경\n\n"));
        message.append(String.format("📍 API: %s (%s)\n", apiName, apiProvider));
        message.append(String.format("🔄 상태: %s → %s\n", 
                                    previousState != null ? previousState.getDisplayName() : "알수없음",
                                    state.getDisplayName()));
        message.append(String.format("⚠️ 원인: %s\n", trigger.getDescription()));
        message.append(String.format("📊 심각도: %s\n", severity.getDisplayName()));
        
        if (reason != null && !reason.isEmpty()) {
            message.append(String.format("💬 상세: %s\n", reason));
        }
        
        if (currentCallRate > 0) {
            message.append(String.format("📈 현재 호출량: %d/min (임계치: %d/min)\n", 
                                        currentCallRate, thresholdCallRate));
        }
        
        if (currentFailureRate > 0) {
            message.append(String.format("❌ 실패율: %.1f%% (임계치: %.1f%%)\n", 
                                        currentFailureRate, thresholdFailureRate));
        }
        
        if (nextRetryTime != null) {
            message.append(String.format("⏰ 다음 재시도: %s\n", nextRetryTime));
        }
        
        message.append(String.format("🔧 자동복구: %s\n", autoRecoverable ? "가능" : "불가"));
        message.append(String.format("👨‍💻 수동개입: %s\n", requiresManualIntervention ? "필요" : "불필요"));
        
        return message.toString();
    }

    /**
     * 이벤트 중요도 점수 계산 (0-100)
     */
    public int calculatePriorityScore() {
        int baseScore = severity.getLevel() * 20;  // 심각도 기본 점수
        
        // 상태별 가중치
        int stateWeight = switch (state) {
            case FORCE_OPEN -> 30;
            case OPEN -> 25;
            case HALF_OPEN -> 15;
            case DEGRADED -> 10;
            case CLOSED -> 5;
        };
        
        // 트리거별 가중치
        int triggerWeight = switch (trigger) {
            case DEPENDENCY_FAILURE -> 20;
            case EXCESSIVE_CALLS -> 15;
            case HIGH_FAILURE_RATE -> 15;
            case CONSECUTIVE_FAILURES -> 10;
            case SLOW_RESPONSE -> 8;
            case TIMEOUT -> 8;
            case HEALTH_CHECK_FAILURE -> 5;
            default -> 3;
        };
        
        return Math.min(100, baseScore + stateWeight + triggerWeight);
    }
}