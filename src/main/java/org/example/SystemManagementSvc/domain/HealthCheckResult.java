package org.example.SystemManagementSvc.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 외부 API 헬스체크 결과를 저장하는 엔티티
 * 헬스체크 이력 관리 및 통계 분석에 활용
 */
@Entity
@Table(name = "health_check_result")
@EntityListeners(AuditingEntityListener.class)
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckResult {

    /** 헬스체크 결과 고유 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "check_id", length = 36)
    private String checkId;

    /** 체크 대상 API ID */
    @Column(name = "api_id", nullable = false, length = 36)
    private String apiId;

    /** 헬스체크 유형 */
    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false)
    private HealthCheckType checkType;

    /** 헬스체크 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private HealthStatus status;

    /** HTTP 응답 상태 코드 */
    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    /** 응답 시간 (밀리초) */
    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    /** 에러 메시지 */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 에러 상세 정보 */
    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    /** 응답 본문 샘플 (처음 500자) */
    @Column(name = "response_sample", length = 500)
    private String responseSample;

    /** 헬스체크 실행 시간 */
    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    /** 헬스체크 실행 서버 */
    @Column(name = "checked_by", length = 100)
    private String checkedBy;

    /** 연속 실패 횟수 */
    @Column(name = "consecutive_failures")
    @Builder.Default
    private Integer consecutiveFailures = 0;

    /** 헬스체크 타임아웃 여부 */
    @Column(name = "is_timeout")
    @Builder.Default
    private Boolean isTimeout = false;

    /** SSL 인증서 유효성 */
    @Column(name = "ssl_valid")
    private Boolean sslValid;

    /** 추가 메타데이터 (JSON 형태로 저장) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    /** 성공 여부 (계산된 필드) */
    @Transient
    private Boolean isSuccess;
    
    /** 추가 정보 (일시적 필드) */
    @Transient
    private String additionalInfo;
    
    /**
     * Timeout 여부 조회
     */
    public Boolean isTimeout() {
        return isTimeout;
    }
    
    /**
     * 추가 정보 설정
     */
    public void setAdditionalInfo(String additionalInfo) {
        this.additionalInfo = additionalInfo;
    }

    /** 엔티티 생성 일시 */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 엔티티 마지막 수정 일시 */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 헬스체크 유형 열거형
     */
    public enum HealthCheckType {
        STATIC("정적 헬스체크", "단순 상태 코드 확인"),
        DYNAMIC("동적 헬스체크", "실제 API 호출 및 응답 검증"),
        DEEP("심층 헬스체크", "응답 스키마 검증 포함"),
        SYNTHETIC("합성 헬스체크", "가상 트랜잭션 테스트");

        private final String displayName;
        private final String description;

        HealthCheckType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 헬스체크 상태 열거형
     */
    public enum HealthStatus {
        HEALTHY("정상", "#28a745"),
        DEGRADED("성능저하", "#fd7e14"),
        UNHEALTHY("비정상", "#dc3545"),
        TIMEOUT("타임아웃", "#6f42c1"),
        UNKNOWN("알수없음", "#6c757d");

        private final String displayName;
        private final String colorCode;

        HealthStatus(String displayName, String colorCode) {
            this.displayName = displayName;
            this.colorCode = colorCode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColorCode() {
            return colorCode;
        }

        public boolean isHealthy() {
            return this == HEALTHY;
        }

        public boolean isUnhealthy() {
            return this == UNHEALTHY || this == TIMEOUT;
        }
    }

    /**
     * 헬스체크가 성공했는지 확인
     */
    public boolean isSuccess() {
        return status == HealthStatus.HEALTHY || status == HealthStatus.DEGRADED;
    }

    /**
     * 헬스체크가 실패했는지 확인
     */
    public boolean isFailure() {
        return !isSuccess();
    }

    /**
     * 응답시간이 느린지 확인 (임계치: 3초)
     */
    public boolean isSlow() {
        return responseTimeMs != null && responseTimeMs > 3000;
    }

    /**
     * 헬스체크 점수 계산 (0-100점)
     * 상태, 응답시간, 연속 실패 등을 종합적으로 고려
     */
    public int calculateHealthScore() {
        int baseScore = switch (status) {
            case HEALTHY -> 100;
            case DEGRADED -> 70;
            case UNHEALTHY -> 30;
            case TIMEOUT -> 20;
            case UNKNOWN -> 0;
        };

        // 응답시간 점수 차감
        if (responseTimeMs != null) {
            if (responseTimeMs > 5000) baseScore -= 20;  // 5초 이상
            else if (responseTimeMs > 3000) baseScore -= 10;  // 3초 이상
            else if (responseTimeMs > 1000) baseScore -= 5;   // 1초 이상
        }

        // 연속 실패 점수 차감
        if (consecutiveFailures != null && consecutiveFailures > 0) {
            baseScore -= Math.min(consecutiveFailures * 10, 30);
        }

        return Math.max(0, baseScore);
    }

    /**
     * 헬스체크 결과 요약 생성
     */
    public String getResultSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append(status.getDisplayName());
        
        if (responseTimeMs != null) {
            summary.append(" (").append(responseTimeMs).append("ms)");
        }
        
        if (consecutiveFailures != null && consecutiveFailures > 0) {
            summary.append(" [연속실패: ").append(consecutiveFailures).append("회]");
        }
        
        return summary.toString();
    }

    /**
     * Pre-persist 콜백 - 저장 전 기본값 설정
     */
    @PrePersist
    protected void onCreate() {
        if (checkedAt == null) {
            checkedAt = LocalDateTime.now();
        }
        if (checkedBy == null) {
            try {
                checkedBy = java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                checkedBy = "unknown";
            }
        }
    }
}