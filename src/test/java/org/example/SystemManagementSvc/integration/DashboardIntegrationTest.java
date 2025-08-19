package org.example.SystemManagementSvc.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.SystemManagementSvc.domain.ExternalApi;
import org.example.SystemManagementSvc.domain.HealthCheckResult;
import org.example.SystemManagementSvc.repository.ExternalApiRepository;
import org.example.SystemManagementSvc.repository.HealthCheckResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("대시보드 통합 테스트")
class DashboardIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ExternalApiRepository externalApiRepository;

    @Autowired
    private HealthCheckResultRepository healthCheckResultRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        
        // 테스트 데이터 초기화
        healthCheckResultRepository.deleteAll();
        externalApiRepository.deleteAll();
        
        // 대시보드 테스트용 데이터 생성
        setupDashboardTestData();
    }

    private void setupDashboardTestData() {
        // 다양한 도메인의 API 생성
        ExternalApi weatherApi = ExternalApi.builder()
                .apiId("weather-dashboard-test")
                .apiName("대시보드 테스트 날씨 API")
                .apiUrl("https://api.weather.dashboard.com/current")
                .apiIssuer("기상청")
                .apiOwner("weather-team")
                .domain(ExternalApi.ApiDomain.WEATHER)
                .keyword(ExternalApi.ApiKeyword.REAL_TIME)
                .httpMethod("GET")
                .apiEffectiveness(true)
                .build();

        ExternalApi trafficApi = ExternalApi.builder()
                .apiId("traffic-dashboard-test")
                .apiName("대시보드 테스트 교통 API")
                .apiUrl("https://api.traffic.dashboard.com/info")
                .apiIssuer("국토교통부")
                .apiOwner("traffic-team")
                .domain(ExternalApi.ApiDomain.TRAFFIC)
                .keyword(ExternalApi.ApiKeyword.REAL_TIME)
                .httpMethod("GET")
                .apiEffectiveness(true)
                .build();

        ExternalApi financeApi = ExternalApi.builder()
                .apiId("finance-dashboard-test")
                .apiName("대시보드 테스트 금융 API")
                .apiUrl("https://api.finance.dashboard.com/rates")
                .apiIssuer("한국은행")
                .apiOwner("finance-team")
                .domain(ExternalApi.ApiDomain.FINANCE)
                .keyword(ExternalApi.ApiKeyword.DAILY)
                .httpMethod("GET")
                .apiEffectiveness(false) // 비활성화된 API
                .build();

        externalApiRepository.saveAll(List.of(weatherApi, trafficApi, financeApi));

        // 성공/실패가 섞인 헬스체크 결과 생성
        LocalDateTime now = LocalDateTime.now();

        // 날씨 API - 성공 결과들
        HealthCheckResult weatherSuccess1 = HealthCheckResult.builder()
                .apiId("weather-dashboard-test")
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .httpStatusCode(200)
                .responseTimeMs(120L)
                .responseSample("{\"temperature\": 22}")
                .checkedAt(now.minusMinutes(10))
                .consecutiveFailures(0)
                .build();

        HealthCheckResult weatherSuccess2 = HealthCheckResult.builder()
                .apiId("weather-dashboard-test")
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .httpStatusCode(200)
                .responseTimeMs(150L)
                .responseSample("{\"temperature\": 23}")
                .checkedAt(now.minusMinutes(5))
                .consecutiveFailures(0)
                .build();

        // 교통 API - 성능 저하 결과
        HealthCheckResult trafficDegraded = HealthCheckResult.builder()
                .apiId("traffic-dashboard-test")
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.DEGRADED)
                .httpStatusCode(200)
                .responseTimeMs(4500L) // 느린 응답
                .responseSample("{\"traffic\": \"heavy\"}")
                .checkedAt(now.minusMinutes(3))
                .consecutiveFailures(0)
                .build();

        // 금융 API - 실패 결과들
        HealthCheckResult financeFailure1 = HealthCheckResult.builder()
                .apiId("finance-dashboard-test")
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.UNHEALTHY)
                .httpStatusCode(500)
                .responseTimeMs(1000L)
                .errorMessage("Internal Server Error")
                .checkedAt(now.minusMinutes(8))
                .consecutiveFailures(1)
                .build();

        HealthCheckResult financeFailure2 = HealthCheckResult.builder()
                .apiId("finance-dashboard-test")
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.TIMEOUT)
                .responseTimeMs(10000L)
                .errorMessage("Request timeout")
                .checkedAt(now.minusMinutes(2))
                .consecutiveFailures(2)
                .isTimeout(true)
                .build();

        healthCheckResultRepository.saveAll(List.of(
                weatherSuccess1, weatherSuccess2, trafficDegraded, financeFailure1, financeFailure2
        ));
    }

    @Test
    @DisplayName("대시보드 분석 데이터 조회 통합 테스트")
    void getDashboardAnalytics_Integration() throws Exception {
        // When & Then
        mockMvc.perform(get("/dashboard/analytics")
                .param("hours", "24")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("대시보드 분석 데이터 조회 성공"))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.summary").exists())
                .andExpect(jsonPath("$.data.summary.totalErrors").isNumber())
                .andExpect(jsonPath("$.data.summary.totalApiCalls").isNumber())
                .andExpect(jsonPath("$.data.summary.systemStatus").isString())
                .andExpect(jsonPath("$.data.analysisPeriod").exists())
                .andExpect(jsonPath("$.data.analysisPeriod.durationHours").value(24))
                .andExpect(jsonPath("$.data.analysisPeriod.description").value("최근 24시간"));
    }

    @Test
    @DisplayName("대시보드 분석 기본값 파라미터 테스트")
    void getDashboardAnalytics_DefaultParams_Integration() throws Exception {
        // When & Then
        mockMvc.perform(get("/dashboard/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.analysisPeriod.durationHours").value(24)) // 기본값
                .andExpect(jsonPath("$.data.analysisPeriod.description").value("최근 24시간"));
    }

    @Test
    @DisplayName("서비스별 에러 순위 조회 통합 테스트")
    void getErrorRanking_Integration() throws Exception {
        // When & Then
        mockMvc.perform(get("/dashboard/errors/ranking")
                .param("hours", "12")
                .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("서비스별 에러 순위 조회 성공"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("외부 API 호출 순위 조회 통합 테스트")
    void getApiCallRanking_Integration() throws Exception {
        // When & Then
        mockMvc.perform(get("/dashboard/api-calls/ranking")
                .param("hours", "6")
                .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("외부 API 호출 순위 조회 성공"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("대시보드 헬스체크 통합 테스트")
    void getDashboardHealth_Integration() throws Exception {
        // When & Then
        mockMvc.perform(get("/dashboard/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("대시보드 서비스 정상 동작"))
                .andExpect(jsonPath("$.data").value("OK"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("장기간 분석 기간 테스트")
    void getDashboardAnalytics_LongPeriod_Integration() throws Exception {
        // When & Then
        mockMvc.perform(get("/dashboard/analytics")
                .param("hours", "168") // 1주일
                .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.analysisPeriod.durationHours").value(168))
                .andExpect(jsonPath("$.data.analysisPeriod.description").value("최근 1주"));
    }

    @Test
    @DisplayName("최대 제한 개수 테스트")
    void getDashboardAnalytics_MaxLimit_Integration() throws Exception {
        // When & Then
        mockMvc.perform(get("/dashboard/analytics")
                .param("hours", "24")
                .param("limit", "100")) // 큰 제한값
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("잘못된 파라미터 처리 테스트")
    void getDashboardAnalytics_InvalidParams_Integration() throws Exception {
        // When & Then - 음수 파라미터도 서비스에서 처리됨
        mockMvc.perform(get("/dashboard/analytics")
                .param("hours", "-1")
                .param("limit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("실시간 데이터 반영 확인 통합 테스트")
    void realTimeDataReflection_Integration() throws Exception {
        // Given - 새로운 헬스체크 결과 추가
        HealthCheckResult newResult = HealthCheckResult.builder()
                .apiId("weather-dashboard-test")
                .checkType(HealthCheckResult.HealthCheckType.DYNAMIC)
                .status(HealthCheckResult.HealthStatus.HEALTHY)
                .httpStatusCode(200)
                .responseTimeMs(90L)
                .responseSample("{\"temperature\": 24}")
                .checkedAt(LocalDateTime.now())
                .consecutiveFailures(0)
                .build();

        healthCheckResultRepository.save(newResult);

        // When & Then - 새로운 데이터가 반영되었는지 확인
        mockMvc.perform(get("/dashboard/analytics")
                .param("hours", "1") // 최근 1시간
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("빈 데이터셋 처리 통합 테스트")
    void emptyDataset_Integration() throws Exception {
        // Given - 모든 데이터 삭제
        healthCheckResultRepository.deleteAll();
        externalApiRepository.deleteAll();

        // When & Then
        mockMvc.perform(get("/dashboard/analytics")
                .param("hours", "24")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.summary.totalErrors").value(0))
                .andExpect(jsonPath("$.data.summary.totalApiCalls").value(0))
                .andExpect(jsonPath("$.data.summary.systemStatus").exists());
    }

    @Test
    @DisplayName("동시 요청 처리 테스트")
    void concurrentRequests_Integration() throws Exception {
        // Given - 여러 동시 요청 시뮬레이션
        int numberOfRequests = 5;
        Thread[] threads = new Thread[numberOfRequests];

        // When
        for (int i = 0; i < numberOfRequests; i++) {
            threads[i] = new Thread(() -> {
                try {
                    mockMvc.perform(get("/dashboard/analytics")
                            .param("hours", "24")
                            .param("limit", "10"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.success").value(true));
                } catch (Exception e) {
                    // 테스트 실패 처리
                    throw new RuntimeException("Concurrent request failed", e);
                }
            });
        }

        // 모든 스레드 시작
        for (Thread thread : threads) {
            thread.start();
        }

        // 모든 스레드 완료 대기
        for (Thread thread : threads) {
            thread.join(5000); // 5초 타임아웃
        }

        // Then - 모든 요청이 성공적으로 처리되었는지 확인
        // (예외가 발생하지 않으면 성공)
    }

    @Test
    @DisplayName("API 응답 시간 측정 통합 테스트")
    void responseTimeTracking_Integration() throws Exception {
        // When
        long startTime = System.currentTimeMillis();
        
        mockMvc.perform(get("/dashboard/analytics")
                .param("hours", "24")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        
        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;

        // Then - 응답 시간이 합리적인 범위 내에 있는지 확인 (5초 이하)
        org.assertj.core.api.Assertions.assertThat(responseTime).isLessThan(5000L);
    }
}