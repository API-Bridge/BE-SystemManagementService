package org.example.SystemManagementSvc.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExternalApi 도메인 테스트")
class ExternalApiTest {

    @Test
    @DisplayName("ExternalApi 객체 생성 테스트")
    void createExternalApi() {
        // Given & When
        ExternalApi api = ExternalApi.builder()
                .apiId("test-api-1")
                .apiName("테스트 API")
                .apiUrl("https://api.test.com/v1/data")
                .apiIssuer("테스트회사")
                .apiOwner("dev-team")
                .domain(ExternalApi.ApiDomain.WEATHER)
                .keyword(ExternalApi.ApiKeyword.REAL_TIME)
                .httpMethod("GET")
                .apiDescription("실시간 날씨 데이터 제공")
                .apiEffectiveness(true)
                .build();

        // Then
        assertThat(api.getApiId()).isEqualTo("test-api-1");
        assertThat(api.getApiName()).isEqualTo("테스트 API");
        assertThat(api.getApiUrl()).isEqualTo("https://api.test.com/v1/data");
        assertThat(api.getApiIssuer()).isEqualTo("테스트회사");
        assertThat(api.getApiOwner()).isEqualTo("dev-team");
        assertThat(api.getDomain()).isEqualTo(ExternalApi.ApiDomain.WEATHER);
        assertThat(api.getKeyword()).isEqualTo(ExternalApi.ApiKeyword.REAL_TIME);
        assertThat(api.getHttpMethod()).isEqualTo("GET");
        assertThat(api.getApiDescription()).isEqualTo("실시간 날씨 데이터 제공");
        assertThat(api.getApiEffectiveness()).isTrue();
    }

    @Test
    @DisplayName("기본값 설정 테스트")
    void defaultValues() {
        // Given & When
        ExternalApi api = ExternalApi.builder()
                .apiId("test-api")
                .apiName("테스트 API")
                .apiUrl("https://api.test.com")
                .apiIssuer("테스트회사")
                .domain(ExternalApi.ApiDomain.OTHER)
                .keyword(ExternalApi.ApiKeyword.REST_API)
                .httpMethod("GET")
                .build();

        // Then
        assertThat(api.getApiEffectiveness()).isTrue(); // 기본값 true
    }

    @Test
    @DisplayName("API 정상 작동 상태 확인 테스트")
    void isHealthy() {
        // Given
        ExternalApi healthyApi = ExternalApi.builder()
                .apiEffectiveness(true)
                .build();

        ExternalApi unhealthyApi = ExternalApi.builder()
                .apiEffectiveness(false)
                .build();

        ExternalApi nullEffectivenessApi = ExternalApi.builder()
                .apiEffectiveness(null)
                .build();

        // When & Then
        assertThat(healthyApi.isHealthy()).isTrue();
        assertThat(unhealthyApi.isHealthy()).isFalse();
        assertThat(nullEffectivenessApi.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("실시간 키워드 기반 높은 우선순위 헬스체크 테스트")
    void getHealthCheckPriority_RealTime() {
        // Given
        ExternalApi realTimeApi = ExternalApi.builder()
                .keyword(ExternalApi.ApiKeyword.REAL_TIME)
                .domain(ExternalApi.ApiDomain.OTHER)
                .build();

        ExternalApi streamingApi = ExternalApi.builder()
                .keyword(ExternalApi.ApiKeyword.STREAMING)
                .domain(ExternalApi.ApiDomain.OTHER)
                .build();

        // When & Then
        assertThat(realTimeApi.getHealthCheckPriority()).isEqualTo(ExternalApi.HealthCheckPriority.HIGH);
        assertThat(streamingApi.getHealthCheckPriority()).isEqualTo(ExternalApi.HealthCheckPriority.HIGH);
    }

    @Test
    @DisplayName("중요 도메인 기반 중간 우선순위 헬스체크 테스트")
    void getHealthCheckPriority_ImportantDomains() {
        // Given
        ExternalApi weatherApi = ExternalApi.builder()
                .keyword(ExternalApi.ApiKeyword.LIST)
                .domain(ExternalApi.ApiDomain.WEATHER)
                .build();

        ExternalApi trafficApi = ExternalApi.builder()
                .keyword(ExternalApi.ApiKeyword.SEARCH)
                .domain(ExternalApi.ApiDomain.TRAFFIC)
                .build();

        ExternalApi disasterApi = ExternalApi.builder()
                .keyword(ExternalApi.ApiKeyword.ALERT)
                .domain(ExternalApi.ApiDomain.DISASTER)
                .build();

        // When & Then
        assertThat(weatherApi.getHealthCheckPriority()).isEqualTo(ExternalApi.HealthCheckPriority.MEDIUM);
        assertThat(trafficApi.getHealthCheckPriority()).isEqualTo(ExternalApi.HealthCheckPriority.MEDIUM);
        assertThat(disasterApi.getHealthCheckPriority()).isEqualTo(ExternalApi.HealthCheckPriority.MEDIUM);
    }

    @Test
    @DisplayName("기타 도메인 기반 낮은 우선순위 헬스체크 테스트")
    void getHealthCheckPriority_LowPriority() {
        // Given
        ExternalApi otherApi = ExternalApi.builder()
                .keyword(ExternalApi.ApiKeyword.BATCH)
                .domain(ExternalApi.ApiDomain.CULTURE)
                .build();

        ExternalApi financeApi = ExternalApi.builder()
                .keyword(ExternalApi.ApiKeyword.DAILY)
                .domain(ExternalApi.ApiDomain.FINANCE)
                .build();

        // When & Then
        assertThat(otherApi.getHealthCheckPriority()).isEqualTo(ExternalApi.HealthCheckPriority.LOW);
        assertThat(financeApi.getHealthCheckPriority()).isEqualTo(ExternalApi.HealthCheckPriority.LOW);
    }

    @Test
    @DisplayName("실시간 키워드가 도메인보다 우선순위가 높음 테스트")
    void getHealthCheckPriority_KeywordOverridesDomain() {
        // Given
        ExternalApi realTimeFinanceApi = ExternalApi.builder()
                .keyword(ExternalApi.ApiKeyword.REAL_TIME)
                .domain(ExternalApi.ApiDomain.FINANCE) // 일반적으로 낮은 우선순위
                .build();

        // When & Then
        assertThat(realTimeFinanceApi.getHealthCheckPriority()).isEqualTo(ExternalApi.HealthCheckPriority.HIGH);
    }

    @ParameterizedTest
    @EnumSource(ExternalApi.ApiDomain.class)
    @DisplayName("모든 API 도메인 열거형 테스트")
    void apiDomainEnum(ExternalApi.ApiDomain domain) {
        // Then
        assertThat(domain.getDescription()).isNotEmpty();
        assertThat(domain.name()).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(ExternalApi.ApiKeyword.class)
    @DisplayName("모든 API 키워드 열거형 테스트")
    void apiKeywordEnum(ExternalApi.ApiKeyword keyword) {
        // Then
        assertThat(keyword.getDescription()).isNotEmpty();
        assertThat(keyword.name()).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(ExternalApi.HealthCheckPriority.class)
    @DisplayName("모든 헬스체크 우선순위 열거형 테스트")
    void healthCheckPriorityEnum(ExternalApi.HealthCheckPriority priority) {
        // Then
        assertThat(priority.getIntervalSeconds()).isGreaterThan(0);
        assertThat(priority.name()).isNotEmpty();
    }

    @Test
    @DisplayName("헬스체크 우선순위별 간격 시간 테스트")
    void healthCheckPriorityIntervals() {
        // When & Then
        assertThat(ExternalApi.HealthCheckPriority.HIGH.getIntervalSeconds()).isEqualTo(60);
        assertThat(ExternalApi.HealthCheckPriority.MEDIUM.getIntervalSeconds()).isEqualTo(120);
        assertThat(ExternalApi.HealthCheckPriority.LOW.getIntervalSeconds()).isEqualTo(300);
    }

    @Test
    @DisplayName("엔티티 동등성 테스트")
    void entityEquality() {
        // Given
        ExternalApi api1 = ExternalApi.builder()
                .apiId("test-api")
                .apiName("테스트 API")
                .apiUrl("https://api.test.com")
                .apiIssuer("테스트회사")
                .domain(ExternalApi.ApiDomain.WEATHER)
                .keyword(ExternalApi.ApiKeyword.REAL_TIME)
                .httpMethod("GET")
                .build();

        ExternalApi api2 = ExternalApi.builder()
                .apiId("test-api")
                .apiName("테스트 API")
                .apiUrl("https://api.test.com")
                .apiIssuer("테스트회사")
                .domain(ExternalApi.ApiDomain.WEATHER)
                .keyword(ExternalApi.ApiKeyword.REAL_TIME)
                .httpMethod("GET")
                .build();

        ExternalApi api3 = ExternalApi.builder()
                .apiId("different-api")
                .apiName("다른 API")
                .apiUrl("https://api.different.com")
                .apiIssuer("다른회사")
                .domain(ExternalApi.ApiDomain.TRAFFIC)
                .keyword(ExternalApi.ApiKeyword.BATCH)
                .httpMethod("POST")
                .build();

        // When & Then
        assertThat(api1).isEqualTo(api2);
        assertThat(api1).isNotEqualTo(api3);
        assertThat(api1.hashCode()).isEqualTo(api2.hashCode());
        assertThat(api1.hashCode()).isNotEqualTo(api3.hashCode());
    }

    @Test
    @DisplayName("Builder 패턴 테스트")
    void builderPattern() {
        // Given & When
        ExternalApi api = ExternalApi.builder()
                .apiId("builder-test")
                .apiName("빌더 테스트")
                .apiUrl("https://builder.test.com")
                .apiIssuer("빌더회사")
                .apiOwner("builder-team")
                .domain(ExternalApi.ApiDomain.EDUCATION)
                .keyword(ExternalApi.ApiKeyword.SEARCH)
                .httpMethod("GET")
                .apiDescription("빌더 패턴 테스트")
                .apiEffectiveness(false)
                .build();

        // Then
        assertThat(api.getApiId()).isEqualTo("builder-test");
        assertThat(api.getApiEffectiveness()).isFalse();
    }

    @Test
    @DisplayName("toString 메서드 테스트")
    void toStringMethod() {
        // Given
        ExternalApi api = ExternalApi.builder()
                .apiId("toString-test")
                .apiName("toString 테스트")
                .apiUrl("https://tostring.test.com")
                .apiIssuer("toString회사")
                .domain(ExternalApi.ApiDomain.NEWS)
                .keyword(ExternalApi.ApiKeyword.LIST)
                .httpMethod("GET")
                .build();

        // When
        String apiString = api.toString();

        // Then
        assertThat(apiString).contains("toString-test");
        assertThat(apiString).contains("toString 테스트");
        assertThat(apiString).contains("NEWS");
        assertThat(apiString).contains("LIST");
    }

    @Test
    @DisplayName("Audit 필드 설정 테스트")
    void auditFields() {
        // Given
        ExternalApi api = ExternalApi.builder()
                .apiId("audit-test")
                .apiName("Audit 테스트")
                .apiUrl("https://audit.test.com")
                .apiIssuer("Audit회사")
                .domain(ExternalApi.ApiDomain.HEALTHCARE)
                .keyword(ExternalApi.ApiKeyword.DETAIL)
                .httpMethod("GET")
                .build();

        LocalDateTime testTime = LocalDateTime.now();
        api.setCreatedAt(testTime);
        api.setUpdatedAt(testTime);

        // When & Then
        assertThat(api.getCreatedAt()).isEqualTo(testTime);
        assertThat(api.getUpdatedAt()).isEqualTo(testTime);
    }

    @Test
    @DisplayName("API 도메인 설명 확인 테스트")
    void apiDomainDescriptions() {
        // Then
        assertThat(ExternalApi.ApiDomain.WEATHER.getDescription()).isEqualTo("날씨");
        assertThat(ExternalApi.ApiDomain.TRAFFIC.getDescription()).isEqualTo("교통");
        assertThat(ExternalApi.ApiDomain.DISASTER.getDescription()).isEqualTo("재난안전");
        assertThat(ExternalApi.ApiDomain.FINANCE.getDescription()).isEqualTo("금융");
        assertThat(ExternalApi.ApiDomain.OTHER.getDescription()).isEqualTo("기타");
    }

    @Test
    @DisplayName("API 키워드 설명 확인 테스트")
    void apiKeywordDescriptions() {
        // Then
        assertThat(ExternalApi.ApiKeyword.REAL_TIME.getDescription()).isEqualTo("실시간");
        assertThat(ExternalApi.ApiKeyword.STREAMING.getDescription()).isEqualTo("스트리밍");
        assertThat(ExternalApi.ApiKeyword.BATCH.getDescription()).isEqualTo("배치");
        assertThat(ExternalApi.ApiKeyword.REST_API.getDescription()).isEqualTo("REST");
        assertThat(ExternalApi.ApiKeyword.GRAPHQL.getDescription()).isEqualTo("GraphQL");
    }
}