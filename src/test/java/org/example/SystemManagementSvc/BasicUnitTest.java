package org.example.SystemManagementSvc;

import org.example.SystemManagementSvc.config.ElasticsearchConfig;
import org.example.SystemManagementSvc.domain.ExternalApi;
import org.example.SystemManagementSvc.domain.HealthCheckResult;
import org.example.SystemManagementSvc.event.model.CircuitBreakerEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 기본 단위 테스트 - Spring 컨텍스트 없이 실행
 * 주요 도메인 객체와 이벤트 모델의 기본 기능 검증
 */
public class BasicUnitTest {

    @Test
    public void testElasticsearchConfigCanBeInstantiated() {
        ElasticsearchConfig config = new ElasticsearchConfig();
        assertNotNull(config, "ElasticsearchConfig should be instantiatable");
    }

    @Test
    public void testCircuitBreakerEventCanBeCreated() {
        CircuitBreakerEvent event = CircuitBreakerEvent
            .create("test-api-1", "Test API", "Test Provider");
        
        assertNotNull(event, "CircuitBreakerEvent should be created");
        assertEquals("test-api-1", event.getApiId());
        assertEquals("Test API", event.getApiName());
        assertEquals("Test Provider", event.getApiProvider());
        assertEquals("CIRCUIT_BREAKER_STATE_CHANGE", event.getEventType());
        assertNotNull(event.getTimestamp());
        assertNotNull(event.getEventId());
        assertEquals("systemmanagement-svc", event.getSourceService());
    }

    @Test
    public void testCircuitBreakerStateChangeEventCanBeCreated() {
        CircuitBreakerEvent event = CircuitBreakerEvent.stateChange(
            "test-api-1", 
            "Test API", 
            "Test Provider",
            CircuitBreakerEvent.CircuitBreakerState.OPEN,
            CircuitBreakerEvent.CircuitBreakerState.CLOSED,
            CircuitBreakerEvent.StateChangeTrigger.EXCESSIVE_CALLS,
            "Too many calls detected"
        );
        
        assertNotNull(event);
        assertEquals(CircuitBreakerEvent.CircuitBreakerState.OPEN, event.getState());
        assertEquals(CircuitBreakerEvent.CircuitBreakerState.CLOSED, event.getPreviousState());
        assertEquals(CircuitBreakerEvent.StateChangeTrigger.EXCESSIVE_CALLS, event.getTrigger());
        assertEquals("Too many calls detected", event.getReason());
        assertEquals(CircuitBreakerEvent.SeverityLevel.HIGH, event.getSeverity());
        assertTrue(event.isAutoRecoverable());
        assertFalse(event.isRequiresManualIntervention());
    }

    @Test
    public void testExternalApiEntityCanBeCreated() {
        ExternalApi api = ExternalApi.builder()
            .apiId("test-api-1")
            .apiName("Test API")
            .apiUrl("https://api.test.com")
            .apiIssuer("Test Company")
            .apiDomain(ExternalApi.ApiDomain.OTHER)
            .apiKeyword(ExternalApi.ApiKeyword.REST_API)
            .httpMethod("GET")
            .apiDescription("Test API description")
            .build();
        
        assertNotNull(api);
        assertEquals("test-api-1", api.getApiId());
        assertEquals("Test API", api.getApiName());
        assertEquals("https://api.test.com", api.getApiUrl());
        assertTrue(api.isHealthy());
        assertEquals(ExternalApi.HealthCheckPriority.LOW, api.getHealthCheckPriority());
    }

    @Test
    public void testExternalApiHealthCheckPriority() {
        // 실시간 API는 높은 우선순위
        ExternalApi realTimeApi = ExternalApi.builder()
            .apiKeyword(ExternalApi.ApiKeyword.REAL_TIME)
            .apiDomain(ExternalApi.ApiDomain.OTHER)
            .build();
        assertEquals(ExternalApi.HealthCheckPriority.HIGH, realTimeApi.getHealthCheckPriority());

        // 날씨 API는 중간 우선순위
        ExternalApi weatherApi = ExternalApi.builder()
            .apiKeyword(ExternalApi.ApiKeyword.DAILY)
            .apiDomain(ExternalApi.ApiDomain.WEATHER)
            .build();
        assertEquals(ExternalApi.HealthCheckPriority.MEDIUM, weatherApi.getHealthCheckPriority());

        // 일반 API는 낮은 우선순위
        ExternalApi normalApi = ExternalApi.builder()
            .apiKeyword(ExternalApi.ApiKeyword.LIST)
            .apiDomain(ExternalApi.ApiDomain.OTHER)
            .build();
        assertEquals(ExternalApi.HealthCheckPriority.LOW, normalApi.getHealthCheckPriority());
    }

    @Test
    public void testHealthCheckResultEntityCanBeCreated() {
        HealthCheckResult result = HealthCheckResult.builder()
            .checkId("test-check-1")
            .apiId("test-api-1")
            .checkType(HealthCheckResult.HealthCheckType.STATIC)
            .status(HealthCheckResult.HealthStatus.HEALTHY)
            .httpStatusCode(200)
            .responseTimeMs(100L)
            .checkedAt(LocalDateTime.now())
            .build();
        
        assertNotNull(result);
        assertEquals("test-check-1", result.getCheckId());
        assertEquals("test-api-1", result.getApiId());
        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
        assertFalse(result.isSlow());
        assertEquals(100, result.calculateHealthScore());
    }

    @Test
    public void testHealthCheckResultScoring() {
        // 건강한 상태 + 빠른 응답
        HealthCheckResult healthyFast = HealthCheckResult.builder()
            .status(HealthCheckResult.HealthStatus.HEALTHY)
            .responseTimeMs(500L)
            .consecutiveFailures(0)
            .build();
        assertEquals(100, healthyFast.calculateHealthScore());

        // 성능 저하 + 느린 응답
        HealthCheckResult degradedSlow = HealthCheckResult.builder()
            .status(HealthCheckResult.HealthStatus.DEGRADED)
            .responseTimeMs(4000L)  // 4초 - 느림
            .consecutiveFailures(2)
            .build();
        assertEquals(40, degradedSlow.calculateHealthScore()); // 70 - 10(3~5초) - 20(연속실패)

        // 비정상 상태
        HealthCheckResult unhealthy = HealthCheckResult.builder()
            .status(HealthCheckResult.HealthStatus.UNHEALTHY)
            .responseTimeMs(1000L)
            .consecutiveFailures(0)
            .build();
        assertEquals(30, unhealthy.calculateHealthScore()); // 30 (1000ms exactly is not penalized)
    }

    @Test
    public void testCircuitBreakerEventPriorityCalculation() {
        // 높은 우선순위 이벤트 (강제 차단)
        CircuitBreakerEvent criticalEvent = CircuitBreakerEvent.stateChange(
            "api-1", "API 1", "Provider 1",
            CircuitBreakerEvent.CircuitBreakerState.FORCE_OPEN,
            CircuitBreakerEvent.CircuitBreakerState.CLOSED,
            CircuitBreakerEvent.StateChangeTrigger.DEPENDENCY_FAILURE,
            "Dependency failed"
        );
        
        int criticalScore = criticalEvent.calculatePriorityScore();
        assertTrue(criticalScore >= 90, "Critical events should have high priority score");

        // 낮은 우선순위 이벤트 (복구)
        CircuitBreakerEvent lowEvent = CircuitBreakerEvent.stateChange(
            "api-3", "API 3", "Provider 3", 
            CircuitBreakerEvent.CircuitBreakerState.CLOSED,
            CircuitBreakerEvent.CircuitBreakerState.HALF_OPEN,
            CircuitBreakerEvent.StateChangeTrigger.AUTO_RECOVERY,
            "Auto recovery completed"
        );
        
        int lowScore = lowEvent.calculatePriorityScore();
        assertTrue(lowScore < criticalScore, "Recovery events should have lower priority than critical");
        assertTrue(lowScore <= 50, "Recovery events should have low priority score");
    }

    @Test
    public void testCircuitBreakerEventNotificationMessage() {
        CircuitBreakerEvent event = CircuitBreakerEvent.stateChange(
            "weather-api", "날씨 API", "기상청",
            CircuitBreakerEvent.CircuitBreakerState.OPEN,
            CircuitBreakerEvent.CircuitBreakerState.CLOSED,
            CircuitBreakerEvent.StateChangeTrigger.EXCESSIVE_CALLS,
            "분당 호출량이 임계치를 초과했습니다"
        );
        
        event.setCurrentCallRate(1200);
        event.setThresholdCallRate(1000);

        String message = event.getNotificationMessage();
        
        assertNotNull(message);
        assertTrue(message.contains("날씨 API"));
        assertTrue(message.contains("기상청"));
        assertTrue(message.contains("정상 → 차단"));
        assertTrue(message.contains("과도한 호출량"));
        assertTrue(message.contains("1200/min"));
        assertTrue(message.contains("1000/min"));
    }
}