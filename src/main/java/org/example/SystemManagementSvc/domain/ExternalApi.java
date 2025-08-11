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
 * 외부 API 정보를 저장하는 엔티티
 * 헬스체크 대상이 되는 외부 API들의 메타데이터 관리
 */
@Entity
@Table(name = "external_api")
@EntityListeners(AuditingEntityListener.class)
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalApi {

    /** 외부 API의 고유 식별자 */
    @Id
    @Column(name = "api_id", length = 36)
    private String apiId;

    /** API의 이름 */
    @Column(name = "api_name", nullable = false)
    private String apiName;

    /** API의 URL 주소 */
    @Column(name = "api_url", nullable = false, length = 500)
    private String apiUrl;

    /** API 발급처 */
    @Column(name = "api_issuer", nullable = false)
    private String apiIssuer;

    /** API를 추가한 사람 */
    @Column(name = "api_owner", length = 36)
    private String apiOwner;

    /** API 분류 도메인 */
    @Enumerated(EnumType.STRING)
    @Column(name = "api_domain", nullable = false)
    private ApiDomain apiDomain;

    /** API 세부분류용 키워드 */
    @Enumerated(EnumType.STRING)
    @Column(name = "api_keyword", nullable = false)
    private ApiKeyword apiKeyword;

    /** API의 HTTP 메소드 */
    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    /** API에 대한 상세 설명 */
    @Column(name = "api_description", columnDefinition = "TEXT")
    private String apiDescription;

    /** API 유효성 상태 (헬스체크 결과 반영) */
    @Column(name = "api_effectiveness", nullable = false)
    @Builder.Default
    private Boolean apiEffectiveness = true;

    /** 엔티티 생성 일시 */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 엔티티 마지막 수정 일시 */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * API 분류 도메인 열거형
     */
    public enum ApiDomain {
        WEATHER("날씨"),
        TRAFFIC("교통"),
        PUBLIC_FACILITY("공공시설"),
        NEWS("뉴스"),
        FINANCE("금융"),
        HEALTHCARE("보건의료"),
        EDUCATION("교육"),
        CULTURE("문화"),
        ENVIRONMENT("환경"),
        DISASTER("재난안전"),
        OTHER("기타");

        private final String description;

        ApiDomain(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * API 세부분류 키워드 열거형
     */
    public enum ApiKeyword {
        REAL_TIME("실시간"),
        DAILY("일별"),
        HOURLY("시간별"),
        WEEKLY("주간"),
        MONTHLY("월간"),
        BATCH("배치"),
        STREAMING("스트리밍"),
        SEARCH("검색"),
        LIST("목록"),
        DETAIL("상세정보"),
        STATISTICS("통계"),
        FORECAST("예보"),
        ALERT("알림"),
        LOCATION_BASED("위치기반"),
        USER_SPECIFIC("사용자맞춤"),
        PUBLIC_DATA("공공데이터"),
        OPEN_API("오픈API"),
        REST_API("REST"),
        SOAP_API("SOAP"),
        GRAPHQL("GraphQL");

        private final String description;

        ApiKeyword(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * API가 현재 정상 작동 중인지 확인
     */
    public boolean isHealthy() {
        return Boolean.TRUE.equals(this.apiEffectiveness);
    }

    /**
     * 헬스체크 우선순위 결정
     * 도메인과 키워드에 따라 체크 빈도 결정
     */
    public HealthCheckPriority getHealthCheckPriority() {
        // 실시간 데이터는 높은 우선순위
        if (apiKeyword == ApiKeyword.REAL_TIME || apiKeyword == ApiKeyword.STREAMING) {
            return HealthCheckPriority.HIGH;
        }
        
        // 중요한 도메인들은 중간 우선순위
        if (apiDomain == ApiDomain.WEATHER || 
            apiDomain == ApiDomain.TRAFFIC || 
            apiDomain == ApiDomain.DISASTER) {
            return HealthCheckPriority.MEDIUM;
        }
        
        // 나머지는 낮은 우선순위
        return HealthCheckPriority.LOW;
    }

    /**
     * 헬스체크 우선순위 열거형
     */
    public enum HealthCheckPriority {
        HIGH(60),    // 1분마다
        MEDIUM(120), // 2분마다
        LOW(300);    // 5분마다

        private final int intervalSeconds;

        HealthCheckPriority(int intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }

        public int getIntervalSeconds() {
            return intervalSeconds;
        }
    }
}