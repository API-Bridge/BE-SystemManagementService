package org.example.SystemManagementSvc.repository;

import org.example.SystemManagementSvc.domain.ExternalApi;
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
@DisplayName("ExternalApiRepository 테스트")
class ExternalApiRepositoryTest {

    @Autowired
    private ExternalApiRepository externalApiRepository;

    private ExternalApi activeWeatherApi;
    private ExternalApi inactiveTrafficApi;
    private ExternalApi activePaymentApi;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 생성
        activeWeatherApi = ExternalApi.builder()
                .apiId("weather-api-1")
                .apiName("기상청 날씨 API")
                .apiUrl("https://api.weather.go.kr/openapi/weather")
                .apiIssuer("기상청")
                .apiOwner("gov-team")
                .domain(ExternalApi.ApiDomain.GOVERNMENT_DATA)
                .keyword(ExternalApi.ApiKeyword.WEATHER)
                .priority(ExternalApi.HealthCheckPriority.HIGH)
                .httpMethod("GET")
                .apiEffectiveness(true)
                .build();

        inactiveTrafficApi = ExternalApi.builder()
                .apiId("traffic-api-1")
                .apiName("교통정보 API")
                .apiUrl("https://api.traffic.go.kr/openapi/traffic")
                .apiIssuer("국토교통부")
                .apiOwner("traffic-team")
                .domain(ExternalApi.ApiDomain.GOVERNMENT_DATA)
                .keyword(ExternalApi.ApiKeyword.TRAFFIC)
                .priority(ExternalApi.HealthCheckPriority.MEDIUM)
                .httpMethod("GET")
                .apiEffectiveness(false)
                .build();

        activePaymentApi = ExternalApi.builder()
                .apiId("payment-api-1")
                .apiName("결제 API")
                .apiUrl("https://api.payment.com/v1/payments")
                .apiIssuer("결제회사")
                .apiOwner("payment-team")
                .domain(ExternalApi.ApiDomain.PAYMENT_SERVICE)
                .keyword(ExternalApi.ApiKeyword.PAYMENT)
                .priority(ExternalApi.HealthCheckPriority.HIGH)
                .httpMethod("POST")
                .apiEffectiveness(true)
                .build();

        externalApiRepository.saveAll(List.of(activeWeatherApi, inactiveTrafficApi, activePaymentApi));
    }

    @Test
    @DisplayName("활성화된 API 목록 조회 테스트")
    void findByApiEffectivenessTrue() {
        // When
        List<ExternalApi> activeApis = externalApiRepository.findByApiEffectivenessTrue();

        // Then
        assertThat(activeApis).hasSize(2);
        assertThat(activeApis).extracting(ExternalApi::getApiId)
                .containsExactlyInAnyOrder("weather-api-1", "payment-api-1");
        assertThat(activeApis).allMatch(ExternalApi::getApiEffectiveness);
    }

    @Test
    @DisplayName("비활성화된 API 목록 조회 테스트")
    void findByApiEffectivenessFalse() {
        // When
        List<ExternalApi> inactiveApis = externalApiRepository.findByApiEffectivenessFalse();

        // Then
        assertThat(inactiveApis).hasSize(1);
        assertThat(inactiveApis.get(0).getApiId()).isEqualTo("traffic-api-1");
        assertThat(inactiveApis.get(0).getApiEffectiveness()).isFalse();
    }

    @Test
    @DisplayName("도메인별 API 목록 조회 테스트")
    void findByApiDomain() {
        // When
        List<ExternalApi> govApis = externalApiRepository.findByApiDomain(ExternalApi.ApiDomain.GOVERNMENT_DATA);
        List<ExternalApi> paymentApis = externalApiRepository.findByApiDomain(ExternalApi.ApiDomain.PAYMENT_SERVICE);

        // Then
        assertThat(govApis).hasSize(2);
        assertThat(govApis).extracting(ExternalApi::getApiId)
                .containsExactlyInAnyOrder("weather-api-1", "traffic-api-1");

        assertThat(paymentApis).hasSize(1);
        assertThat(paymentApis.get(0).getApiId()).isEqualTo("payment-api-1");
    }

    @Test
    @DisplayName("도메인별 활성화된 API 목록 조회 테스트")
    void findByApiDomainAndApiEffectivenessTrue() {
        // When
        List<ExternalApi> activeGovApis = externalApiRepository
                .findByApiDomainAndApiEffectivenessTrue(ExternalApi.ApiDomain.GOVERNMENT_DATA);

        // Then
        assertThat(activeGovApis).hasSize(1);
        assertThat(activeGovApis.get(0).getApiId()).isEqualTo("weather-api-1");
        assertThat(activeGovApis.get(0).getApiEffectiveness()).isTrue();
    }

    @Test
    @DisplayName("제공업체별 API 목록 조회 테스트")
    void findByApiIssuer() {
        // When
        List<ExternalApi> weatherApis = externalApiRepository.findByApiIssuer("기상청");

        // Then
        assertThat(weatherApis).hasSize(1);
        assertThat(weatherApis.get(0).getApiId()).isEqualTo("weather-api-1");
        assertThat(weatherApis.get(0).getApiIssuer()).isEqualTo("기상청");
    }

    @Test
    @DisplayName("HTTP 메소드별 API 목록 조회 테스트")
    void findByHttpMethod() {
        // When
        List<ExternalApi> getApis = externalApiRepository.findByHttpMethod("GET");
        List<ExternalApi> postApis = externalApiRepository.findByHttpMethod("POST");

        // Then
        assertThat(getApis).hasSize(2);
        assertThat(postApis).hasSize(1);
        assertThat(postApis.get(0).getApiId()).isEqualTo("payment-api-1");
    }

    @Test
    @DisplayName("API 이름으로 검색 테스트")
    void findByApiNameContainingIgnoreCase() {
        // When
        List<ExternalApi> weatherApis = externalApiRepository.findByApiNameContainingIgnoreCase("날씨");
        List<ExternalApi> apiApis = externalApiRepository.findByApiNameContainingIgnoreCase("API");

        // Then
        assertThat(weatherApis).hasSize(1);
        assertThat(weatherApis.get(0).getApiId()).isEqualTo("weather-api-1");

        assertThat(apiApis).hasSize(3); // 모든 API 이름에 "API"가 포함됨
    }

    @Test
    @DisplayName("키워드별 API 목록 조회 테스트")
    void findByApiKeyword() {
        // When
        List<ExternalApi> weatherApis = externalApiRepository.findByApiKeyword(ExternalApi.ApiKeyword.WEATHER);
        List<ExternalApi> paymentApis = externalApiRepository.findByApiKeyword(ExternalApi.ApiKeyword.PAYMENT);

        // Then
        assertThat(weatherApis).hasSize(1);
        assertThat(weatherApis.get(0).getApiId()).isEqualTo("weather-api-1");

        assertThat(paymentApis).hasSize(1);
        assertThat(paymentApis.get(0).getApiId()).isEqualTo("payment-api-1");
    }

    @Test
    @DisplayName("키워드별 활성화된 API 목록 조회 테스트")
    void findByApiKeywordAndApiEffectivenessTrue() {
        // When
        List<ExternalApi> activeWeatherApis = externalApiRepository
                .findByApiKeywordAndApiEffectivenessTrue(ExternalApi.ApiKeyword.WEATHER);
        List<ExternalApi> activeTrafficApis = externalApiRepository
                .findByApiKeywordAndApiEffectivenessTrue(ExternalApi.ApiKeyword.TRAFFIC);

        // Then
        assertThat(activeWeatherApis).hasSize(1);
        assertThat(activeWeatherApis.get(0).getApiId()).isEqualTo("weather-api-1");

        assertThat(activeTrafficApis).isEmpty(); // traffic API는 비활성화 상태
    }

    @Test
    @DisplayName("도메인과 키워드 조합 조회 테스트")
    void findByApiDomainAndApiKeyword() {
        // When
        List<ExternalApi> govWeatherApis = externalApiRepository.findByApiDomainAndApiKeyword(
                ExternalApi.ApiDomain.GOVERNMENT_DATA,
                ExternalApi.ApiKeyword.WEATHER
        );

        // Then
        assertThat(govWeatherApis).hasSize(1);
        assertThat(govWeatherApis.get(0).getApiId()).isEqualTo("weather-api-1");
    }

    @Test
    @DisplayName("소유자별 API 목록 조회 테스트")
    void findByApiOwner() {
        // When
        List<ExternalApi> govTeamApis = externalApiRepository.findByApiOwner("gov-team");

        // Then
        assertThat(govTeamApis).hasSize(1);
        assertThat(govTeamApis.get(0).getApiId()).isEqualTo("weather-api-1");
    }

    @Test
    @DisplayName("API URL로 중복 체크 테스트")
    void findByApiUrl() {
        // When
        Optional<ExternalApi> foundApi = externalApiRepository.findByApiUrl("https://api.weather.go.kr/openapi/weather");
        Optional<ExternalApi> notFoundApi = externalApiRepository.findByApiUrl("https://non-existent-api.com");

        // Then
        assertThat(foundApi).isPresent();
        assertThat(foundApi.get().getApiId()).isEqualTo("weather-api-1");

        assertThat(notFoundApi).isEmpty();
    }

    @Test
    @DisplayName("활성화된 API 수 조회 테스트")
    void countActiveApis() {
        // When
        long activeCount = externalApiRepository.countActiveApis();
        long activeCountByMethod = externalApiRepository.countByApiEffectivenessTrue();

        // Then
        assertThat(activeCount).isEqualTo(2);
        assertThat(activeCountByMethod).isEqualTo(2);
    }

    @Test
    @DisplayName("비활성화된 API 수 조회 테스트")
    void countInactiveApis() {
        // When
        long inactiveCount = externalApiRepository.countInactiveApis();

        // Then
        assertThat(inactiveCount).isEqualTo(1);
    }

    @Test
    @DisplayName("도메인별 API 수 통계 조회 테스트")
    void getApiCountByDomain() {
        // When
        List<Object[]> domainStats = externalApiRepository.getApiCountByDomain();

        // Then
        assertThat(domainStats).hasSize(2);
        
        // 결과는 개수 내림차순으로 정렬됨
        Object[] firstResult = domainStats.get(0);
        if (firstResult[0] == ExternalApi.ApiDomain.GOVERNMENT_DATA) {
            assertThat(firstResult[1]).isEqualTo(1L); // 활성화된 정부 API는 1개
        } else {
            assertThat(firstResult[0]).isEqualTo(ExternalApi.ApiDomain.PAYMENT_SERVICE);
            assertThat(firstResult[1]).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("제공업체별 API 수 통계 조회 테스트")
    void getApiCountByIssuer() {
        // When
        List<Object[]> issuerStats = externalApiRepository.getApiCountByIssuer();

        // Then
        assertThat(issuerStats).hasSize(2);
        assertThat(issuerStats).extracting(stat -> stat[0])
                .containsExactlyInAnyOrder("기상청", "결제회사");
    }

    @Test
    @DisplayName("배치 크기만큼 API 목록 조회 테스트")
    void findApisBatch() {
        // When
        List<ExternalApi> firstBatch = externalApiRepository.findApisBatch(2, 0);
        List<ExternalApi> secondBatch = externalApiRepository.findApisBatch(2, 2);

        // Then
        assertThat(firstBatch).hasSize(2);
        assertThat(secondBatch).hasSize(1);
        
        // 중복 없이 모든 활성화된 API가 조회되어야 함
        assertThat(firstBatch).extracting(ExternalApi::getApiId)
                .doesNotContainAnyElementsOf(secondBatch.stream().map(ExternalApi::getApiId).toList());
    }

    @Test
    @DisplayName("특정 시간 이후 업데이트되지 않은 API 조회 테스트")
    void findApisNotUpdatedSince() {
        // Given
        LocalDateTime futureTime = LocalDateTime.now().plusDays(1);

        // When
        List<ExternalApi> oldApis = externalApiRepository.findApisNotUpdatedSince(futureTime);

        // Then
        assertThat(oldApis).hasSize(3); // 모든 API가 미래 시간보다 과거에 업데이트됨
    }

    @Test
    @DisplayName("우선순위별 정렬된 API 목록 조회 테스트")
    void findAllOrderedByPriority() {
        // When
        List<ExternalApi> prioritizedApis = externalApiRepository.findAllOrderedByPriority();

        // Then
        assertThat(prioritizedApis).hasSize(2); // 활성화된 API만
        
        // 날씨 API가 실시간/스트리밍 키워드는 아니지만 WEATHER 도메인으로 우선순위 2
        // 결제 API는 PAYMENT 키워드로 우선순위 3
        // 알파벳 순서로 정렬되므로 payment-api-1, weather-api-1 순서
        assertThat(prioritizedApis).extracting(ExternalApi::getApiId)
                .containsExactly("payment-api-1", "weather-api-1");
    }

    @Test
    @DisplayName("최근 업데이트순 API 목록 조회 테스트")
    void findAllOrderedByUpdatedDesc() {
        // When
        List<ExternalApi> recentApis = externalApiRepository.findAllOrderedByUpdatedDesc();

        // Then
        assertThat(recentApis).hasSize(2); // 활성화된 API만
        
        // 최근 업데이트 순으로 정렬됨 (테스트에서는 생성 순서와 동일)
        assertThat(recentApis).extracting(ExternalApi::getApiId)
                .containsExactly("payment-api-1", "weather-api-1");
    }

    @Test
    @DisplayName("존재하지 않는 키워드로 조회 시 빈 결과 테스트")
    void findByNonExistentKeyword() {
        // When
        List<ExternalApi> newsApis = externalApiRepository.findByApiKeyword(ExternalApi.ApiKeyword.NEWS);

        // Then
        assertThat(newsApis).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 도메인으로 조회 시 빈 결과 테스트")
    void findByNonExistentDomain() {
        // When
        List<ExternalApi> socialApis = externalApiRepository.findByApiDomain(ExternalApi.ApiDomain.SOCIAL_MEDIA);

        // Then
        assertThat(socialApis).isEmpty();
    }
}