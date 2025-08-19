package org.example.SystemManagementSvc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.SystemManagementSvc.dto.analytics.AlertRequest;
import org.example.SystemManagementSvc.dto.analytics.AlertResponse;
import org.example.SystemManagementSvc.service.AlertNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertController.class)
@DisplayName("AlertController 테스트")
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertNotificationService alertNotificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("알림 웹훅 수신 성공 테스트")
    void receiveAlert_Success() throws Exception {
        // Given
        AlertRequest alertRequest = AlertRequest.builder()
                .groupKey("test-group-key")
                .status("firing")
                .receiver("webhook")
                .commonLabels(Map.of("alertname", "TestAlert", "severity", "critical"))
                .alerts(List.of(
                    AlertRequest.Alert.builder()
                        .status("firing")
                        .labels(Map.of("instance", "localhost:8080"))
                        .annotations(Map.of("summary", "Test alert"))
                        .startsAt(LocalDateTime.now())
                        .build()
                ))
                .build();

        AlertResponse mockResponse = AlertResponse.builder()
                .success(true)
                .message("알림 처리 성공")
                .alertId("test-group-key")
                .processedAt(LocalDateTime.now())
                .processingTimeMs(100L)
                .build();

        when(alertNotificationService.processAlert(any(AlertRequest.class)))
                .thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/alerts/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(alertRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("알림이 성공적으로 처리되었습니다"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpected(jsonPath("$.data.alertId").value("test-group-key"));
    }

    @Test
    @DisplayName("알림 웹훅 수신 실패 테스트")
    void receiveAlert_Failure() throws Exception {
        // Given
        AlertRequest alertRequest = AlertRequest.builder()
                .groupKey("test-group-key")
                .status("firing")
                .receiver("webhook")
                .build();

        when(alertNotificationService.processAlert(any(AlertRequest.class)))
                .thenThrow(new RuntimeException("알림 처리 중 오류 발생"));

        // When & Then
        mockMvc.perform(post("/alerts/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(alertRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("알림 처리 실패"))
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.retryRequested").value(true));
    }

    @Test
    @DisplayName("테스트 알림 발송 성공 테스트")
    void testAlert_Success() throws Exception {
        // Given
        AlertResponse mockResponse = AlertResponse.builder()
                .success(true)
                .message("테스트 알림 발송 성공")
                .alertId("test-alert-" + System.currentTimeMillis())
                .processedAt(LocalDateTime.now())
                .build();

        when(alertNotificationService.processAlert(any(AlertRequest.class)))
                .thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/alerts/test")
                .param("severity", "warning")
                .param("message", "테스트 메시지"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("테스트 알림이 발송되었습니다"))
                .andExpected(jsonPath("$.data.success").value(true));
    }

    @Test
    @DisplayName("테스트 알림 발송 실패 테스트")
    void testAlert_Failure() throws Exception {
        // Given
        when(alertNotificationService.processAlert(any(AlertRequest.class)))
                .thenThrow(new RuntimeException("이메일 서버 연결 실패"));

        // When & Then
        mockMvc.perform(post("/alerts/test")
                .param("severity", "error")
                .param("message", "테스트 메시지"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").contains("테스트 알림 발송 실패"));
    }

    @Test
    @DisplayName("알림 상태 확인 성공 테스트")
    void getAlertStatus_Success() throws Exception {
        // When & Then
        mockMvc.perform(get("/alerts/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.message").value("알림 시스템 상태 조회 성공"))
                .andExpected(jsonPath("$.data.alertingEnabled").value(true))
                .andExpected(jsonPath("$.data.emailEnabled").value(true))
                .andExpected(jsonPath("$.data.supportedSeverities").isArray());
    }

    @Test
    @DisplayName("잘못된 JSON 포맷 요청 테스트")
    void receiveAlert_InvalidJson() throws Exception {
        // When & Then
        mockMvc.perform(post("/alerts/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("invalid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("빈 요청 바디 테스트")
    void receiveAlert_EmptyBody() throws Exception {
        // When & Then
        mockMvc.perform(post("/alerts/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpected(status().isOk());
    }
}