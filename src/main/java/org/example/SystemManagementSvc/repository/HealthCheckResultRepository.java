package org.example.SystemManagementSvc.repository;

import org.example.SystemManagementSvc.domain.HealthCheckResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 헬스체크 결과 조회를 위한 Repository
 * 헬스체크 이력 관리 및 통계 분석에 활용
 */
@Repository
public interface HealthCheckResultRepository extends JpaRepository<HealthCheckResult, String> {

    /**
     * 특정 API의 최신 헬스체크 결과 조회
     */
    Optional<HealthCheckResult> findTopByApiIdOrderByCheckedAtDesc(String apiId);

    /**
     * 특정 API의 헬스체크 이력 조회 (최신순)
     */
    List<HealthCheckResult> findByApiIdOrderByCheckedAtDesc(String apiId);

    /**
     * 특정 기간 내 헬스체크 결과 조회
     */
    List<HealthCheckResult> findByCheckedAtBetweenOrderByCheckedAtDesc(
        LocalDateTime startTime, 
        LocalDateTime endTime
    );

    /**
     * 특정 상태의 헬스체크 결과 조회
     */
    List<HealthCheckResult> findByStatus(HealthCheckResult.HealthStatus status);

    /**
     * 실패한 헬스체크 결과 조회 (최신순)
     */
    @Query("SELECT h FROM HealthCheckResult h " +
           "WHERE h.status IN ('UNHEALTHY', 'TIMEOUT') " +
           "ORDER BY h.checkedAt DESC")
    List<HealthCheckResult> findFailedHealthChecks();

    /**
     * 특정 API의 연속 실패 횟수 조회
     */
    @Query("SELECT MAX(h.consecutiveFailures) FROM HealthCheckResult h " +
           "WHERE h.apiId = :apiId")
    Integer findMaxConsecutiveFailures(@Param("apiId") String apiId);

    /**
     * 응답시간이 임계치를 초과하는 헬스체크 결과 조회
     */
    @Query("SELECT h FROM HealthCheckResult h " +
           "WHERE h.responseTimeMs > :thresholdMs " +
           "ORDER BY h.responseTimeMs DESC")
    List<HealthCheckResult> findSlowHealthChecks(@Param("thresholdMs") Long thresholdMs);

    /**
     * API별 평균 응답시간 통계
     */
    @Query("SELECT h.apiId, AVG(h.responseTimeMs) FROM HealthCheckResult h " +
           "WHERE h.checkedAt >= :since AND h.status = 'HEALTHY' " +
           "GROUP BY h.apiId " +
           "ORDER BY AVG(h.responseTimeMs) DESC")
    List<Object[]> getAverageResponseTimeByApi(@Param("since") LocalDateTime since);

    /**
     * API별 성공률 통계
     */
    @Query("SELECT h.apiId, " +
           "COUNT(*) as total, " +
           "SUM(CASE WHEN h.status IN ('HEALTHY', 'DEGRADED') THEN 1 ELSE 0 END) as success, " +
           "(SUM(CASE WHEN h.status IN ('HEALTHY', 'DEGRADED') THEN 1 ELSE 0 END) * 100.0 / COUNT(*)) as successRate " +
           "FROM HealthCheckResult h " +
           "WHERE h.checkedAt >= :since " +
           "GROUP BY h.apiId " +
           "ORDER BY successRate ASC")
    List<Object[]> getSuccessRateByApi(@Param("since") LocalDateTime since);

    /**
     * 시간별 헬스체크 상태 분포
     */
    @Query("SELECT DATE_TRUNC('hour', h.checkedAt) as hour, " +
           "h.status, COUNT(*) " +
           "FROM HealthCheckResult h " +
           "WHERE h.checkedAt >= :since " +
           "GROUP BY DATE_TRUNC('hour', h.checkedAt), h.status " +
           "ORDER BY hour DESC")
    List<Object[]> getHealthCheckDistributionByHour(@Param("since") LocalDateTime since);

    /**
     * 가장 불안정한 API 식별 (실패율 기준)
     */
    @Query("SELECT h.apiId, " +
           "COUNT(*) as totalChecks, " +
           "SUM(CASE WHEN h.status IN ('UNHEALTHY', 'TIMEOUT') THEN 1 ELSE 0 END) as failures, " +
           "(SUM(CASE WHEN h.status IN ('UNHEALTHY', 'TIMEOUT') THEN 1 ELSE 0 END) * 100.0 / COUNT(*)) as failureRate " +
           "FROM HealthCheckResult h " +
           "WHERE h.checkedAt >= :since " +
           "GROUP BY h.apiId " +
           "HAVING COUNT(*) >= :minChecks " +
           "ORDER BY failureRate DESC")
    List<Object[]> getMostUnstableApis(@Param("since") LocalDateTime since, @Param("minChecks") int minChecks);

    /**
     * 특정 API의 최근 헬스체크 트렌드 조회
     */
    @Query("SELECT h FROM HealthCheckResult h " +
           "WHERE h.apiId = :apiId AND h.checkedAt >= :since " +
           "ORDER BY h.checkedAt DESC " +
           "LIMIT :limit")
    List<HealthCheckResult> getApiHealthTrend(@Param("apiId") String apiId, 
                                            @Param("since") LocalDateTime since, 
                                            @Param("limit") int limit);

    /**
     * 헬스체크 유형별 통계
     */
    @Query("SELECT h.checkType, COUNT(*), AVG(h.responseTimeMs) " +
           "FROM HealthCheckResult h " +
           "WHERE h.checkedAt >= :since " +
           "GROUP BY h.checkType")
    List<Object[]> getStatisticsByCheckType(@Param("since") LocalDateTime since);

    /**
     * 현재 비정상 상태인 API 목록
     */
    @Query("SELECT DISTINCT h.apiId FROM HealthCheckResult h " +
           "WHERE h.checkId IN (" +
           "  SELECT h2.checkId FROM HealthCheckResult h2 " +
           "  WHERE h2.apiId = h.apiId " +
           "  ORDER BY h2.checkedAt DESC " +
           "  LIMIT 1" +
           ") AND h.status IN ('UNHEALTHY', 'TIMEOUT')")
    List<String> findCurrentlyUnhealthyApiIds();

    /**
     * 특정 기간 동안 한 번도 체크되지 않은 API들 식별
     */
    @Query("SELECT DISTINCT a.apiId FROM ExternalApi a " +
           "WHERE a.apiEffectiveness = true " +
           "AND a.apiId NOT IN (" +
           "  SELECT DISTINCT h.apiId FROM HealthCheckResult h " +
           "  WHERE h.checkedAt >= :since" +
           ")")
    List<String> findApisNotCheckedSince(@Param("since") LocalDateTime since);

    /**
     * 오래된 헬스체크 결과 정리 (성능 최적화용)
     */
    @Query("DELETE FROM HealthCheckResult h " +
           "WHERE h.checkedAt < :cutoffDate")
    void deleteOldHealthCheckResults(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * 특정 API의 헬스체크 결과 수 조회
     */
    @Query("SELECT COUNT(h) FROM HealthCheckResult h WHERE h.apiId = :apiId")
    long countByApiId(@Param("apiId") String apiId);

    /**
     * 전체 헬스체크 수행 통계
     */
    @Query("SELECT " +
           "COUNT(*) as total, " +
           "SUM(CASE WHEN h.status = 'HEALTHY' THEN 1 ELSE 0 END) as healthy, " +
           "SUM(CASE WHEN h.status = 'DEGRADED' THEN 1 ELSE 0 END) as degraded, " +
           "SUM(CASE WHEN h.status = 'UNHEALTHY' THEN 1 ELSE 0 END) as unhealthy, " +
           "SUM(CASE WHEN h.status = 'TIMEOUT' THEN 1 ELSE 0 END) as timeout, " +
           "AVG(h.responseTimeMs) as avgResponseTime " +
           "FROM HealthCheckResult h " +
           "WHERE h.checkedAt >= :since")
    Object getOverallHealthStatistics(@Param("since") LocalDateTime since);

    /**
     * SSL 인증서 문제가 있는 API 조회
     */
    @Query("SELECT h FROM HealthCheckResult h " +
           "WHERE h.sslValid = false " +
           "ORDER BY h.checkedAt DESC")
    List<HealthCheckResult> findSslIssues();
}