package org.example.SystemManagementSvc.controller;

import org.example.SystemManagementSvc.event.model.CircuitBreakerEvent;
import org.example.SystemManagementSvc.service.CircuitBreakerMonitoringService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CircuitBreakerController.class)
@DisplayName("CircuitBreakerController 테스트")
class CircuitBreakerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CircuitBreakerMonitoringService circuitBreakerMonitoringService;

    @Test
    @DisplayName("모든 서킷브레이커 상태 조회 성공 테스트")
    void getAllCircuitBreakerStatus_Success() throws Exception {
        // Given
        Map<String, CircuitBreakerEvent.CircuitBreakerState> mockStates = Map.of(
                "api-1", CircuitBreakerEvent.CircuitBreakerState.CLOSED,
                "api-2", CircuitBreakerEvent.CircuitBreakerState.DEGRADED,
                "api-3", CircuitBreakerEvent.CircuitBreakerState.OPEN
        );

        when(circuitBreakerMonitoringService.getAllCircuitBreakerStates())
                .thenReturn(mockStates);

        // When & Then
        mockMvc.perform(get("/circuit-breaker/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.message").value("서킷브레이커 상태 조회 성공: 총 3개 API"))
                .andExpected(jsonPath("$.data.totalApis").value(3))
                .andExpected(jsonPath("$.data.healthyCount").value(1))
                .andExpected(jsonPath("$.data.degradedCount").value(1))
                .andExpected(jsonPath("$.data.openCount").value(1))
                .andExpected(jsonPath("$.data.states").exists());
    }

    @Test
    @DisplayName("서킷브레이커 수동 제어 성공 테스트")
    void controlCircuitBreaker_Success() throws Exception {
        // Given
        String apiId = "test-api";
        String apiName = "테스트 API";
        String apiProvider = "테스트 제공업체";
        String reason = "관리자 수동 조치";
        CircuitBreakerEvent.CircuitBreakerState forcedState = CircuitBreakerEvent.CircuitBreakerState.CLOSED;

        doNothing().when(circuitBreakerMonitoringService)
                .forceCircuitBreakerState(apiId, apiName, apiProvider, forcedState, reason);

        // When & Then
        mockMvc.perform(post("/circuit-breaker/control")
                .param("apiId", apiId)
                .param("apiName", apiName)
                .param("apiProvider", apiProvider)
                .param("forcedState", forcedState.name())
                .param("reason", reason))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.message").value("API '테스트 API'의 서킷브레이커 상태를 '정상'로 변경했습니다."))
                .andExpected(jsonPath("$.data").value(forcedState.name()));
    }

    @Test
    @DisplayName("서킷브레이커 수동 제어 실패 테스트 - 빈 API ID")
    void controlCircuitBreaker_EmptyApiId() throws Exception {
        // When & Then
        mockMvc.perform(post("/circuit-breaker/control")
                .param("apiId", "")
                .param("apiName", "테스트 API")
                .param("apiProvider", "테스트 제공업체")
                .param("forcedState", "CLOSED")
                .param("reason", "테스트"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpected(jsonPath("$.message").value("요청 오류: API ID는 필수 입력값입니다."));
    }

    @Test
    @DisplayName("서킷브레이커 수동 제어 실패 테스트 - 빈 사유")
    void controlCircuitBreaker_EmptyReason() throws Exception {
        // When & Then
        mockMvc.perform(post("/circuit-breaker/control")
                .param("apiId", "test-api")
                .param("apiName", "테스트 API")
                .param("apiProvider", "테스트 제공업체")
                .param("forcedState", "CLOSED")
                .param("reason", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpected(jsonPath("$.message").value("요청 오류: 변경 사유는 필수 입력값입니다."));
    }

    @Test
    @DisplayName("모든 서킷브레이커 리셋 성공 테스트")
    void resetAllCircuitBreakers_Success() throws Exception {
        // Given
        String reason = "정기 점검으로 인한 전체 리셋";
        
        Map<String, CircuitBreakerEvent.CircuitBreakerState> mockStates = Map.of(
                "api-1", CircuitBreakerEvent.CircuitBreakerState.OPEN,
                "api-2", CircuitBreakerEvent.CircuitBreakerState.DEGRADED,
                "api-3", CircuitBreakerEvent.CircuitBreakerState.CLOSED
        );

        when(circuitBreakerMonitoringService.getAllCircuitBreakerStates())
                .thenReturn(mockStates);

        doNothing().when(circuitBreakerMonitoringService)
                .forceCircuitBreakerState(anyString(), anyString(), anyString(), 
                        eq(CircuitBreakerEvent.CircuitBreakerState.CLOSED), anyString());

        // When & Then
        mockMvc.perform(post("/circuit-breaker/reset-all")
                .param("reason", reason))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.message").value("서킷브레이커 전체 리셋 완료: 2개 API 리셋됨"))
                .andExpected(jsonPath("$.data.totalApis").value(3))
                .andExpected(jsonPath("$.data.resetCount").value(2))
                .andExpected(jsonPath("$.data.skippedCount").value(1));
    }

    @Test
    @DisplayName("서킷브레이커 모니터링 수동 실행 성공 테스트")
    void triggerMonitoring_Success() throws Exception {
        // Given
        doNothing().when(circuitBreakerMonitoringService).monitorCircuitBreakers();

        // When & Then
        mockMvc.perform(post("/circuit-breaker/monitor/trigger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.message").value("서킷브레이커 모니터링이 시작되었습니다. 결과는 로그에서 확인하세요."))
                .andExpected(jsonPath("$.data").value("monitoring_started"));
    }

    @Test
    @DisplayName("서킷브레이커 설정 정보 조회 성공 테스트")
    void getCircuitBreakerConfig_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/circuit-breaker/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.message").value("서킷브레이커 설정 정보 조회 성공"))
                .andExpected(jsonPath("$.data.callRateThreshold").exists())
                .andExpected(jsonPath("$.data.failureRateThreshold").exists())
                .andExpected(jsonPath("$.data.consecutiveFailuresThreshold").exists())
                .andExpected(jsonPath("$.data.supportedStates").isArray());
    }

    @Test
    @DisplayName("서킷브레이커 통계 조회 성공 테스트")
    void getCircuitBreakerStatistics_Success() throws Exception {
        // Given
        Map<String, CircuitBreakerEvent.CircuitBreakerState> mockStates = Map.of(
                "api-1", CircuitBreakerEvent.CircuitBreakerState.CLOSED,
                "api-2", CircuitBreakerEvent.CircuitBreakerState.CLOSED,
                "api-3", CircuitBreakerEvent.CircuitBreakerState.DEGRADED
        );

        when(circuitBreakerMonitoringService.getAllCircuitBreakerStates())
                .thenReturn(mockStates);

        // When & Then
        mockMvc.perform(get("/circuit-breaker/statistics")
                .param("hours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.message").value("서킷브레이커 통계 조회 성공"))
                .andExpected(jsonPath("$.data.period").value("24 hours"))
                .andExpected(jsonPath("$.data.totalApis").value(3))
                .andExpected(jsonPath("$.data.stabilityIndex").exists())
                .andExpected(jsonPath("$.data.stateDistribution").exists());
    }

    @Test
    @DisplayName("서킷브레이커 통계 조회 기본값 테스트")
    void getCircuitBreakerStatistics_DefaultHours() throws Exception {
        // Given
        when(circuitBreakerMonitoringService.getAllCircuitBreakerStates())
                .thenReturn(Map.of());

        // When & Then
        mockMvc.perform(get("/circuit-breaker/statistics"))
                .andExpect(status().isOk())
                .andExpected(jsonPath("$.data.period").value("24 hours"));
    }

    @Test
    @DisplayName("서킷브레이커 상태 조회 실패 테스트")
    void getAllCircuitBreakerStatus_Failure() throws Exception {
        // Given
        when(circuitBreakerMonitoringService.getAllCircuitBreakerStates())
                .thenThrow(new RuntimeException("Redis 연결 실패"));

        // When & Then
        mockMvc.perform(get("/circuit-breaker/status"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpected(jsonPath("$.message").contains("서킷브레이커 상태 조회 실패"));
    }

    @Test
    @DisplayName("서킷브레이커 제어 실패 테스트")
    void controlCircuitBreaker_ServiceFailure() throws Exception {
        // Given
        doThrow(new RuntimeException("서비스 오류"))
                .when(circuitBreakerMonitoringService)
                .forceCircuitBreakerState(anyString(), anyString(), anyString(), 
                        any(CircuitBreakerEvent.CircuitBreakerState.class), anyString());

        // When & Then
        mockMvc.perform(post("/circuit-breaker/control")
                .param("apiId", "test-api")
                .param("apiName", "테스트 API")
                .param("apiProvider", "테스트 제공업체")
                .param("forcedState", "CLOSED")
                .param("reason", "테스트"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpected(jsonPath("$.message").contains("서킷브레이커 제어 실패"));
    }
}