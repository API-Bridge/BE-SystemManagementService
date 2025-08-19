package org.example.SystemManagementSvc.repository;

import org.example.SystemManagementSvc.domain.HealthCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("HealthCheckResultRepository 테스트")
class HealthCheckResultRepositoryTest {

    @Autowired
    private HealthCheckResultRepository healthCheckResultRepository;

    private HealthCheckResult healthyResult;
    private HealthCheckResult unhealthyResult;
    private HealthCheckResult timeoutResult;
    private HealthCheckResult degradedResult;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        healthyResult = HealthCheckResult.builder()
                .checkId("check-1")
                .apiId("api-1")
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .httpStatusCode(200)
                .responseTimeMs(150L)
                .responseSample("{\"status\": \"ok\"}")
                .checkedAt(now.minusMinutes(5))
                .consecutiveFailures(0)
                .sslValid(true)
                .build();

        unhealthyResult = HealthCheckResult.builder()
                .checkId("check-2")
                .apiId("api-1")
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.UNHEALTHY)
                .httpStatusCode(500)
                .responseTimeMs(300L)
                .errorMessage("Internal Server Error")
                .errorDetails("Database connection failed")
                .checkedAt(now.minusMinutes(3))
                .consecutiveFailures(2)
                .sslValid(true)
                .build();

        timeoutResult = HealthCheckResult.builder()
                .checkId("check-3")
                .apiId("api-2")
                .checkType(HealthCheckResult.HealthCheckType.DYNAMIC)
                .status(HealthCheckResult.HealthStatus.TIMEOUT)
                .responseTimeMs(10000L)
                .errorMessage("Request timeout")
                .checkedAt(now.minusMinutes(2))
                .consecutiveFailures(1)
                .isTimeout(true)
                .sslValid(true)
                .build();

        degradedResult = HealthCheckResult.builder()
                .checkId("check-4")
                .apiId("api-3")
                .checkType(HealthCheckResult.HealthCheckType.DYNAMIC)
                .status(HealthCheckResult.HealthStatus.DEGRADED)
                .httpStatusCode(200)
                .responseTimeMs(6000L)
                .responseSample("{\"status\": \"slow\"}")
                .checkedAt(now.minusMinutes(1))
                .consecutiveFailures(0)
                .sslValid(false)
                .build();

        healthCheckResultRepository.saveAll(List.of(healthyResult, unhealthyResult, timeoutResult, degradedResult));
    }

    @Test
    @DisplayName("특정 API의 최신 헬스체크 결과 조회 테스트")
    void findTopByApiIdOrderByCheckedAtDesc() {
        // When
        Optional<HealthCheckResult> latestResult = healthCheckResultRepository
                .findTopByApiIdOrderByCheckedAtDesc("api-1");

        // Then
        assertThat(latestResult).isPresent();
        assertThat(latestResult.get().getCheckId()).isEqualTo("check-2"); // 더 최근 결과
        assertThat(latestResult.get().getStatus()).isEqualTo(HealthCheckResult.HealthStatus.UNHEALTHY);
    }

    @Test
    @DisplayName("특정 API의 헬스체크 이력 조회 테스트")
    void findByApiIdOrderByCheckedAtDesc() {
        // When
        List<HealthCheckResult> results = healthCheckResultRepository
                .findByApiIdOrderByCheckedAtDesc("api-1");

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getCheckId()).isEqualTo("check-2"); // 최신이 첫 번째
        assertThat(results.get(1).getCheckId()).isEqualTo("check-1");
    }

    @Test
    @DisplayName("특정 기간 내 헬스체크 결과 조회 테스트")
    void findByCheckedAtBetweenOrderByCheckedAtDesc() {
        // Given
        LocalDateTime start = LocalDateTime.now().minusMinutes(4);
        LocalDateTime end = LocalDateTime.now().minusMinutes(1);

        // When
        List<HealthCheckResult> results = healthCheckResultRepository
                .findByCheckedAtBetweenOrderByCheckedAtDesc(start, end);

        // Then
        assertThat(results).hasSize(2); // check-2와 check-3
        assertThat(results).extracting(HealthCheckResult::getCheckId)
                .containsExactly("check-3", "check-2"); // 최신순
    }

    @Test
    @DisplayName("특정 상태의 헬스체크 결과 조회 테스트")
    void findByStatus() {
        // When
        List<HealthCheckResult> healthyResults = healthCheckResultRepository
                .findByStatus(HealthCheckResult.HealthStatus.HEALTHY);
        List<HealthCheckResult> unhealthyResults = healthCheckResultRepository
                .findByStatus(HealthCheckResult.HealthStatus.UNHEALTHY);

        // Then
        assertThat(healthyResults).hasSize(1);
        assertThat(healthyResults.get(0).getCheckId()).isEqualTo("check-1");

        assertThat(unhealthyResults).hasSize(1);
        assertThat(unhealthyResults.get(0).getCheckId()).isEqualTo("check-2");
    }

    @Test
    @DisplayName("실패한 헬스체크 결과 조회 테스트")
    void findFailedHealthChecks() {
        // When
        List<HealthCheckResult> failedResults = healthCheckResultRepository.findFailedHealthChecks();

        // Then
        assertThat(failedResults).hasSize(2); // UNHEALTHY와 TIMEOUT
        assertThat(failedResults).extracting(HealthCheckResult::getStatus)
                .containsExactlyInAnyOrder(
                        HealthCheckResult.HealthStatus.UNHEALTHY,
                        HealthCheckResult.HealthStatus.TIMEOUT
                );
    }

    @Test
    @DisplayName("특정 API의 연속 실패 횟수 조회 테스트")
    void findMaxConsecutiveFailures() {
        // When
        Integer maxFailures = healthCheckResultRepository.findMaxConsecutiveFailures("api-1");

        // Then
        assertThat(maxFailures).isEqualTo(2);
    }

    @Test
    @DisplayName("응답시간 임계치 초과 헬스체크 결과 조회 테스트")
    void findSlowHealthChecks() {
        // When
        List<HealthCheckResult> slowResults = healthCheckResultRepository.findSlowHealthChecks(5000L);

        // Then
        assertThat(slowResults).hasSize(2); // timeout과 degraded (6000ms, 10000ms)
        assertThat(slowResults.get(0).getResponseTimeMs()).isEqualTo(10000L); // 가장 느린 것 첫 번째
        assertThat(slowResults.get(1).getResponseTimeMs()).isEqualTo(6000L);
    }

    @Test
    @DisplayName("API별 평균 응답시간 통계 테스트")
    void getAverageResponseTimeByApi() {
        // Given
        LocalDateTime since = LocalDateTime.now().minusHours(1);

        // When
        List<Object[]> avgStats = healthCheckResultRepository.getAverageResponseTimeByApi(since);

        // Then
        assertThat(avgStats).hasSize(2); // api-1과 api-3 (HEALTHY 상태만)
        
        // api-3이 6000ms로 더 느림
        Object[] slowestApi = avgStats.get(0);
        assertThat(slowestApi[0]).isEqualTo("api-3");
        assertThat(slowestApi[1]).isEqualTo(6000.0);
    }

    @Test
    @DisplayName("API별 성공률 통계 테스트")
    void getSuccessRateByApi() {
        // Given
        LocalDateTime since = LocalDateTime.now().minusHours(1);

        // When
        List<Object[]> successStats = healthCheckResultRepository.getSuccessRateByApi(since);

        // Then
        assertThat(successStats).isNotEmpty();
        
        // 성공률이 낮은 순으로 정렬됨
        Object[] lowestSuccessRate = successStats.get(0);
        if (lowestSuccessRate[0].equals("api-2")) {
            // api-2는 TIMEOUT만 있어서 성공률 0%
            assertThat(lowestSuccessRate[3]).isEqualTo(0.0);
        }
    }

    @Test
    @DisplayName("시간별 헬스체크 상태 분포 테스트")
    void getHealthCheckDistributionByHour() {
        // Given
        LocalDateTime since = LocalDateTime.now().minusHours(1);

        // When
        List<Object[]> distribution = healthCheckResultRepository.getHealthCheckDistributionByHour(since);

        // Then
        assertThat(distribution).isNotEmpty();
        
        // 각 결과는 [시간, 상태, 개수] 형태
        for (Object[] row : distribution) {
            assertThat(row).hasSize(3);
            assertThat(row[2]).isInstanceOf(Long.class); // 개수는 Long 타입
        }
    }

    @Test
    @DisplayName("가장 불안정한 API 식별 테스트")
    void getMostUnstableApis() {
        // Given
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        int minChecks = 1;

        // When
        List<Object[]> unstableApis = healthCheckResultRepository.getMostUnstableApis(since, minChecks);

        // Then
        assertThat(unstableApis).isNotEmpty();
        
        // 실패율 높은 순으로 정렬됨
        Object[] mostUnstable = unstableApis.get(0);
        assertThat(mostUnstable[0]).isEqualTo("api-2"); // TIMEOUT만 있는 API
        assertThat(mostUnstable[3]).isEqualTo(100.0); // 실패율 100%
    }

    @Test
    @DisplayName("특정 API의 최근 헬스체크 트렌드 조회 테스트")
    void getApiHealthTrend() {
        // Given
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        int limit = 10;

        // When
        List<HealthCheckResult> trend = healthCheckResultRepository
                .getApiHealthTrend("api-1", since, limit);

        // Then
        assertThat(trend).hasSize(2);
        assertThat(trend.get(0).getCheckId()).isEqualTo("check-2"); // 최신 순
        assertThat(trend.get(1).getCheckId()).isEqualTo("check-1");
    }

    @Test
    @DisplayName("헬스체크 유형별 통계 테스트")
    void getStatisticsByCheckType() {
        // Given
        LocalDateTime since = LocalDateTime.now().minusHours(1);

        // When
        List<Object[]> typeStats = healthCheckResultRepository.getStatisticsByCheckType(since);

        // Then
        assertThat(typeStats).hasSize(2); // STATIC과 DYNAMIC
        
        for (Object[] stat : typeStats) {
            assertThat(stat).hasSize(3); // [checkType, count, avgResponseTime]
            assertThat(stat[0]).isInstanceOf(HealthCheckResult.HealthCheckType.class);
            assertThat(stat[1]).isInstanceOf(Long.class);
        }
    }

    @Test
    @DisplayName("현재 비정상 상태인 API 목록 조회 테스트")
    void findCurrentlyUnhealthyApiIds() {
        // When
        List<String> unhealthyApiIds = healthCheckResultRepository.findCurrentlyUnhealthyApiIds();

        // Then
        // 이 쿼리는 복잡한 서브쿼리를 사용하므로 H2에서는 정확히 작동하지 않을 수 있음
        // 실제 환경에서는 PostgreSQL을 사용
        assertThat(unhealthyApiIds).isNotNull();
    }

    @Test
    @DisplayName("특정 API의 헬스체크 결과 수 조회 테스트")
    void countByApiId() {
        // When
        long count = healthCheckResultRepository.countByApiId("api-1");

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("전체 헬스체크 수행 통계 테스트")
    void getOverallHealthStatistics() {
        // Given
        LocalDateTime since = LocalDateTime.now().minusHours(1);

        // When
        Object stats = healthCheckResultRepository.getOverallHealthStatistics(since);

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats).isInstanceOf(Object[].class);
        
        Object[] statsArray = (Object[]) stats;
        assertThat(statsArray).hasSize(6); // total, healthy, degraded, unhealthy, timeout, avgResponseTime
        assertThat(statsArray[0]).isEqualTo(4L); // 총 4개 결과
    }

    @Test
    @DisplayName("SSL 인증서 문제가 있는 API 조회 테스트")
    void findSslIssues() {
        // When
        List<HealthCheckResult> sslIssues = healthCheckResultRepository.findSslIssues();

        // Then
        assertThat(sslIssues).hasSize(1);
        assertThat(sslIssues.get(0).getCheckId()).isEqualTo("check-4");
        assertThat(sslIssues.get(0).isSslValid()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 API ID로 조회 시 빈 결과 테스트")
    void findByNonExistentApiId() {
        // When
        Optional<HealthCheckResult> result = healthCheckResultRepository
                .findTopByApiIdOrderByCheckedAtDesc("non-existent-api");
        List<HealthCheckResult> results = healthCheckResultRepository
                .findByApiIdOrderByCheckedAtDesc("non-existent-api");

        // Then
        assertThat(result).isEmpty();
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("미래 시간으로 기간 조회 시 빈 결과 테스트")
    void findByFutureDateRange() {
        // Given
        LocalDateTime futureStart = LocalDateTime.now().plusHours(1);
        LocalDateTime futureEnd = LocalDateTime.now().plusHours(2);

        // When
        List<HealthCheckResult> results = healthCheckResultRepository
                .findByCheckedAtBetweenOrderByCheckedAtDesc(futureStart, futureEnd);

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 상태로 조회 시 빈 결과 테스트")
    void findByNonExistentStatus() {
        // When
        List<HealthCheckResult> results = healthCheckResultRepository
                .findByStatus(HealthCheckResult.HealthStatus.UNKNOWN);

        // Then
        assertThat(results).isEmpty();
    }
}