package org.example.SystemManagementSvc.repository;

import org.example.SystemManagementSvc.domain.ExternalApi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 외부 API 정보 조회를 위한 Repository
 * 헬스체크 시스템에서 사용할 API 목록 관리
 */
@Repository
public interface ExternalApiRepository extends JpaRepository<ExternalApi, String> {

    /**
     * 활성화된 (유효한) API 목록 조회
     * 헬스체크 대상이 되는 API들을 조회
     */
    List<ExternalApi> findByApiEffectivenessTrue();

    /**
     * 비활성화된 (무효한) API 목록 조회
     * 장애가 발생한 API들을 조회
     */
    List<ExternalApi> findByApiEffectivenessFalse();

    /**
     * 도메인별 API 목록 조회
     * 특정 도메인의 API들만 필터링
     */
    List<ExternalApi> findByApiDomain(ExternalApi.ApiDomain domain);

    /**
     * 제공업체별 API 목록 조회
     * 특정 제공업체의 API들만 필터링
     */
    List<ExternalApi> findByApiIssuer(String apiIssuer);

    /**
     * HTTP 메소드별 API 목록 조회
     */
    List<ExternalApi> findByHttpMethod(String httpMethod);

    /**
     * API 이름으로 검색 (부분 일치)
     */
    List<ExternalApi> findByApiNameContainingIgnoreCase(String apiName);

    /**
     * 우선순위별 API 목록 조회
     * 높은 우선순위의 API들을 먼저 체크하기 위함
     */
    @Query("SELECT a FROM ExternalApi a WHERE a.apiEffectiveness = true " +
           "ORDER BY " +
           "CASE WHEN a.apiKeyword IN ('REAL_TIME', 'STREAMING') THEN 1 " +
           "     WHEN a.apiDomain IN ('WEATHER', 'TRAFFIC', 'DISASTER') THEN 2 " +
           "     ELSE 3 END, " +
           "a.apiName")
    List<ExternalApi> findAllOrderedByPriority();

    /**
     * 최근 업데이트된 API 목록 조회
     * 설정이 변경된 API들을 우선적으로 체크
     */
    @Query("SELECT a FROM ExternalApi a WHERE a.apiEffectiveness = true " +
           "ORDER BY a.updatedAt DESC")
    List<ExternalApi> findAllOrderedByUpdatedDesc();

    /**
     * 특정 키워드를 가진 API 목록 조회
     */
    List<ExternalApi> findByApiKeyword(ExternalApi.ApiKeyword keyword);

    /**
     * 도메인과 키워드 조합으로 API 조회
     * 세밀한 필터링이 필요한 경우
     */
    List<ExternalApi> findByApiDomainAndApiKeyword(
        ExternalApi.ApiDomain domain, 
        ExternalApi.ApiKeyword keyword
    );

    /**
     * 소유자별 API 목록 조회
     */
    List<ExternalApi> findByApiOwner(String apiOwner);

    /**
     * API URL로 중복 체크
     * 동일한 URL의 API가 이미 등록되어 있는지 확인
     */
    Optional<ExternalApi> findByApiUrl(String apiUrl);

    /**
     * 활성화된 API 수 조회
     * 전체 시스템 상태 모니터링용
     */
    @Query("SELECT COUNT(a) FROM ExternalApi a WHERE a.apiEffectiveness = true")
    long countActiveApis();

    /**
     * 비활성화된 API 수 조회
     */
    @Query("SELECT COUNT(a) FROM ExternalApi a WHERE a.apiEffectiveness = false")
    long countInactiveApis();

    /**
     * 도메인별 API 수 통계 조회
     */
    @Query("SELECT a.apiDomain, COUNT(a) FROM ExternalApi a " +
           "WHERE a.apiEffectiveness = true " +
           "GROUP BY a.apiDomain " +
           "ORDER BY COUNT(a) DESC")
    List<Object[]> getApiCountByDomain();

    /**
     * 제공업체별 API 수 통계 조회
     */
    @Query("SELECT a.apiIssuer, COUNT(a) FROM ExternalApi a " +
           "WHERE a.apiEffectiveness = true " +
           "GROUP BY a.apiIssuer " +
           "ORDER BY COUNT(a) DESC")
    List<Object[]> getApiCountByIssuer();

    /**
     * 배치 크기만큼 API 목록 조회
     * 병렬 헬스체크를 위한 배치 처리용
     */
    @Query(value = "SELECT * FROM external_api " +
           "WHERE api_effectiveness = true " +
           "ORDER BY api_name " +
           "LIMIT :batchSize OFFSET :offset", 
           nativeQuery = true)
    List<ExternalApi> findApisBatch(@Param("batchSize") int batchSize, @Param("offset") int offset);

    /**
     * 특정 시간 이후 업데이트되지 않은 API 조회
     * 오래된 API 설정을 찾기 위함
     */
    @Query("SELECT a FROM ExternalApi a WHERE a.updatedAt < :thresholdTime")
    List<ExternalApi> findApisNotUpdatedSince(@Param("thresholdTime") java.time.LocalDateTime thresholdTime);
}