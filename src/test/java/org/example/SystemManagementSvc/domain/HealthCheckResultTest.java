package org.example.SystemManagementSvc.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HealthCheckResult 도메인 테스트")
class HealthCheckResultTest {

    @Test
    @DisplayName("HealthCheckResult 객체 생성 테스트")
    void createHealthCheckResult() {
        // Given & When
        LocalDateTime checkTime = LocalDateTime.now();
        HealthCheckResult result = HealthCheckResult.builder()
                .checkId("check-123")
                .apiId("api-test")
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .httpStatusCode(200)
                .responseTimeMs(150L)
                .responseSample("{\"status\": \"ok\"}")
                .checkedAt(checkTime)
                .checkedBy("test-server")
                .consecutiveFailures(0)
                .isTimeout(false)
                .sslValid(true)
                .metadata("{\"version\": \"1.0\"}")
                .build();

        // Then
        assertThat(result.getCheckId()).isEqualTo("check-123");
        assertThat(result.getApiId()).isEqualTo("api-test");
        assertThat(result.getCheckType()).isEqualTo(HealthCheckResult.HealthCheckType.STATIC);
        assertThat(result.getStatus()).isEqualTo(HealthCheckResult.HealthStatus.HEALTHY);
        assertThat(result.getHttpStatusCode()).isEqualTo(200);
        assertThat(result.getResponseTimeMs()).isEqualTo(150L);
        assertThat(result.getResponseSample()).isEqualTo("{\"status\": \"ok\"}");
        assertThat(result.getCheckedAt()).isEqualTo(checkTime);
        assertThat(result.getCheckedBy()).isEqualTo("test-server");
        assertThat(result.getConsecutiveFailures()).isEqualTo(0);
        assertThat(result.isTimeout()).isFalse();
        assertThat(result.getSslValid()).isTrue();
        assertThat(result.getMetadata()).isEqualTo("{\"version\": \"1.0\"}");
    }

    @Test
    @DisplayName("기본값 설정 테스트")
    void defaultValues() {
        // Given & When
        HealthCheckResult result = HealthCheckResult.builder()
                .apiId("api-test")
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .build();

        // Then
        assertThat(result.getConsecutiveFailures()).isEqualTo(0); // 기본값 0
        assertThat(result.isTimeout()).isFalse(); // 기본값 false
    }

    @Test
    @DisplayName("성공 상태 확인 테스트")
    void isSuccess() {
        // Given
        HealthCheckResult healthyResult = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .build();

        HealthCheckResult degradedResult = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.DEGRADED)
                .build();

        HealthCheckResult unhealthyResult = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.UNHEALTHY)
                .build();

        HealthCheckResult timeoutResult = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.TIMEOUT)
                .build();

        HealthCheckResult unknownResult = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.UNKNOWN)
                .build();

        // When & Then
        assertThat(healthyResult.isSuccess()).isTrue();
        assertThat(degradedResult.isSuccess()).isTrue();
        assertThat(unhealthyResult.isSuccess()).isFalse();
        assertThat(timeoutResult.isSuccess()).isFalse();
        assertThat(unknownResult.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("실패 상태 확인 테스트")
    void isFailure() {
        // Given
        HealthCheckResult healthyResult = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .build();

        HealthCheckResult unhealthyResult = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.UNHEALTHY)
                .build();

        // When & Then
        assertThat(healthyResult.isFailure()).isFalse();
        assertThat(unhealthyResult.isFailure()).isTrue();
    }

    @Test
    @DisplayName("느린 응답 확인 테스트")
    void isSlow() {
        // Given
        HealthCheckResult fastResult = HealthCheckResult.builder()
                .responseTimeMs(1500L)
                .build();

        HealthCheckResult slowResult = HealthCheckResult.builder()
                .responseTimeMs(5000L)
                .build();

        HealthCheckResult nullTimeResult = HealthCheckResult.builder()
                .responseTimeMs(null)
                .build();

        // When & Then
        assertThat(fastResult.isSlow()).isFalse(); // 3초 미만
        assertThat(slowResult.isSlow()).isTrue();  // 3초 초과
        assertThat(nullTimeResult.isSlow()).isFalse(); // null인 경우
    }

    @Test
    @DisplayName("헬스체크 점수 계산 테스트 - HEALTHY 상태")
    void calculateHealthScore_Healthy() {
        // Given
        HealthCheckResult result = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .responseTimeMs(500L)
                .consecutiveFailures(0)
                .build();

        // When
        int score = result.calculateHealthScore();

        // Then
        assertThat(score).isEqualTo(100); // 기본 점수 100점
    }

    @Test
    @DisplayName("헬스체크 점수 계산 테스트 - DEGRADED 상태")
    void calculateHealthScore_Degraded() {
        // Given
        HealthCheckResult result = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.DEGRADED)
                .responseTimeMs(2000L)
                .consecutiveFailures(0)
                .build();

        // When
        int score = result.calculateHealthScore();

        // Then
        assertThat(score).isEqualTo(65); // 70 - 5 (1~3초 응답시간 감점)
    }

    @Test
    @DisplayName("헬스체크 점수 계산 테스트 - 느린 응답시간")
    void calculateHealthScore_SlowResponse() {
        // Given
        HealthCheckResult result = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .responseTimeMs(6000L) // 5초 초과
                .consecutiveFailures(0)
                .build();

        // When
        int score = result.calculateHealthScore();

        // Then
        assertThat(score).isEqualTo(80); // 100 - 20 (5초 이상 응답시간 감점)
    }

    @Test
    @DisplayName("헬스체크 점수 계산 테스트 - 연속 실패")
    void calculateHealthScore_ConsecutiveFailures() {
        // Given
        HealthCheckResult result = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .responseTimeMs(500L)
                .consecutiveFailures(2)
                .build();

        // When
        int score = result.calculateHealthScore();

        // Then
        assertThat(score).isEqualTo(80); // 100 - 20 (연속실패 2회 * 10점)
    }

    @Test
    @DisplayName("헬스체크 점수 계산 테스트 - 최대 연속 실패 차감")
    void calculateHealthScore_MaxConsecutiveFailures() {
        // Given
        HealthCheckResult result = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .responseTimeMs(500L)
                .consecutiveFailures(5) // 5회 이상 실패
                .build();

        // When
        int score = result.calculateHealthScore();

        // Then
        assertThat(score).isEqualTo(70); // 100 - 30 (최대 30점 차감)
    }

    @Test
    @DisplayName("헬스체크 점수 계산 테스트 - 최악의 상황")
    void calculateHealthScore_WorstCase() {
        // Given
        HealthCheckResult result = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.TIMEOUT)
                .responseTimeMs(7000L)
                .consecutiveFailures(10)
                .build();

        // When
        int score = result.calculateHealthScore();

        // Then
        assertThat(score).isEqualTo(0); // 20 - 20 - 30 = -30, 최소 0점
    }

    @ParameterizedTest
    @ValueSource(longs = {500L, 1500L, 2500L, 4000L, 6000L})
    @DisplayName("응답시간별 점수 차감 테스트")
    void calculateHealthScore_ResponseTimePenalties(long responseTime) {
        // Given
        HealthCheckResult result = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .responseTimeMs(responseTime)
                .consecutiveFailures(0)
                .build();

        // When
        int score = result.calculateHealthScore();

        // Then
        if (responseTime <= 1000) {
            assertThat(score).isEqualTo(100); // 차감 없음
        } else if (responseTime <= 3000) {
            assertThat(score).isEqualTo(95);  // 5점 차감
        } else if (responseTime <= 5000) {
            assertThat(score).isEqualTo(90);  // 10점 차감
        } else {
            assertThat(score).isEqualTo(80);  // 20점 차감
        }
    }

    @Test
    @DisplayName("헬스체크 결과 요약 생성 테스트")
    void getResultSummary() {
        // Given
        HealthCheckResult healthyResult = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .responseTimeMs(150L)
                .consecutiveFailures(0)
                .build();

        HealthCheckResult failedResult = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.UNHEALTHY)
                .responseTimeMs(5000L)
                .consecutiveFailures(3)
                .build();

        // When
        String healthySummary = healthyResult.getResultSummary();
        String failedSummary = failedResult.getResultSummary();

        // Then
        assertThat(healthySummary).isEqualTo("정상 (150ms)");
        assertThat(failedSummary).isEqualTo("비정상 (5000ms) [연속실패: 3회]");
    }

    @Test
    @DisplayName("응답시간 없는 경우 요약 테스트")
    void getResultSummary_NoResponseTime() {
        // Given
        HealthCheckResult result = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.TIMEOUT)
                .responseTimeMs(null)
                .consecutiveFailures(1)
                .build();

        // When
        String summary = result.getResultSummary();

        // Then
        assertThat(summary).isEqualTo("타임아웃 [연속실패: 1회]");
    }

    @Test
    @DisplayName("연속 실패 없는 경우 요약 테스트")
    void getResultSummary_NoConsecutiveFailures() {
        // Given
        HealthCheckResult result = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.DEGRADED)
                .responseTimeMs(4000L)
                .consecutiveFailures(0)
                .build();

        // When
        String summary = result.getResultSummary();

        // Then
        assertThat(summary).isEqualTo("성능저하 (4000ms)");
    }

    @ParameterizedTest
    @EnumSource(HealthCheckResult.HealthCheckType.class)
    @DisplayName("모든 헬스체크 타입 열거형 테스트")
    void healthCheckTypeEnum(HealthCheckResult.HealthCheckType checkType) {
        // Then
        assertThat(checkType.getDisplayName()).isNotEmpty();
        assertThat(checkType.getDescription()).isNotEmpty();
        assertThat(checkType.name()).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(HealthCheckResult.HealthStatus.class)
    @DisplayName("모든 헬스체크 상태 열거형 테스트")
    void healthStatusEnum(HealthCheckResult.HealthStatus status) {
        // Then
        assertThat(status.getDisplayName()).isNotEmpty();
        assertThat(status.getColorCode()).isNotEmpty();
        assertThat(status.name()).isNotEmpty();
    }

    @Test
    @DisplayName("헬스체크 타입 설명 테스트")
    void healthCheckTypeDescriptions() {
        // Then
        assertThat(HealthCheckResult.HealthCheckType.STATIC.getDisplayName()).isEqualTo("정적 헬스체크");
        assertThat(HealthCheckResult.HealthCheckType.DYNAMIC.getDisplayName()).isEqualTo("동적 헬스체크");
        assertThat(HealthCheckResult.HealthCheckType.DEEP.getDisplayName()).isEqualTo("심층 헬스체크");
        assertThat(HealthCheckResult.HealthCheckType.SYNTHETIC.getDisplayName()).isEqualTo("합성 헬스체크");
    }

    @Test
    @DisplayName("헬스체크 상태 색상 코드 테스트")
    void healthStatusColorCodes() {
        // Then
        assertThat(HealthCheckResult.HealthStatus.HEALTHY.getColorCode()).isEqualTo("#28a745");
        assertThat(HealthCheckResult.HealthStatus.DEGRADED.getColorCode()).isEqualTo("#fd7e14");
        assertThat(HealthCheckResult.HealthStatus.UNHEALTHY.getColorCode()).isEqualTo("#dc3545");
        assertThat(HealthCheckResult.HealthStatus.TIMEOUT.getColorCode()).isEqualTo("#6f42c1");
        assertThat(HealthCheckResult.HealthStatus.UNKNOWN.getColorCode()).isEqualTo("#6c757d");
    }

    @Test
    @DisplayName("헬스체크 상태별 건강성 확인 테스트")
    void healthStatusHealthiness() {
        // Then
        assertThat(HealthCheckResult.HealthStatus.HEALTHY.isHealthy()).isTrue();
        assertThat(HealthCheckResult.HealthStatus.DEGRADED.isHealthy()).isFalse();
        assertThat(HealthCheckResult.HealthStatus.UNHEALTHY.isHealthy()).isFalse();
        assertThat(HealthCheckResult.HealthStatus.TIMEOUT.isHealthy()).isFalse();
        assertThat(HealthCheckResult.HealthStatus.UNKNOWN.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("헬스체크 상태별 비정상 상태 확인 테스트")
    void healthStatusUnhealthiness() {
        // Then
        assertThat(HealthCheckResult.HealthStatus.HEALTHY.isUnhealthy()).isFalse();
        assertThat(HealthCheckResult.HealthStatus.DEGRADED.isUnhealthy()).isFalse();
        assertThat(HealthCheckResult.HealthStatus.UNHEALTHY.isUnhealthy()).isTrue();
        assertThat(HealthCheckResult.HealthStatus.TIMEOUT.isUnhealthy()).isTrue();
        assertThat(HealthCheckResult.HealthStatus.UNKNOWN.isUnhealthy()).isFalse();
    }

    @Test
    @DisplayName("Transient 필드 테스트")
    void transientFields() {
        // Given
        HealthCheckResult result = HealthCheckResult.builder()
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .build();

        // When
        result.setIsSuccess(true);
        result.setAdditionalInfo("테스트 정보");

        // Then
        assertThat(result.getIsSuccess()).isTrue();
        assertThat(result.getAdditionalInfo()).isEqualTo("테스트 정보");
    }

    @Test
    @DisplayName("엔티티 동등성 테스트")
    void entityEquality() {
        // Given
        LocalDateTime checkTime = LocalDateTime.now();
        
        HealthCheckResult result1 = HealthCheckResult.builder()
                .checkId("check-123")
                .apiId("api-test")
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .checkedAt(checkTime)
                .build();

        HealthCheckResult result2 = HealthCheckResult.builder()
                .checkId("check-123")
                .apiId("api-test")
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .checkedAt(checkTime)
                .build();

        HealthCheckResult result3 = HealthCheckResult.builder()
                .checkId("check-456")
                .apiId("api-different")
                .status(HealthCheckResult.HealthStatus.UNHEALTHY)
                .checkedAt(checkTime)
                .build();

        // When & Then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1).isNotEqualTo(result3);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        assertThat(result1.hashCode()).isNotEqualTo(result3.hashCode());
    }

    @Test
    @DisplayName("toString 메서드 테스트")
    void toStringMethod() {
        // Given
        HealthCheckResult result = HealthCheckResult.builder()
                .checkId("toString-test")
                .apiId("api-toString")
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .responseTimeMs(200L)
                .build();

        // When
        String resultString = result.toString();

        // Then
        assertThat(resultString).contains("toString-test");
        assertThat(resultString).contains("api-toString");
        assertThat(resultString).contains("HEALTHY");
        assertThat(resultString).contains("200");
    }
}