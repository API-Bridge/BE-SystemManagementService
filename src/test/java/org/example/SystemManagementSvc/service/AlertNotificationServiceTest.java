package org.example.SystemManagementSvc.service;

import org.example.SystemManagementSvc.dto.analytics.AlertRequest;
import org.example.SystemManagementSvc.dto.analytics.AlertResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertNotificationService 테스트")
class AlertNotificationServiceTest {

    @Mock
    private MockEmailService mockEmailService;

    @InjectMocks
    private AlertNotificationService alertNotificationService;

    @BeforeEach
    void setUp() {
        // 테스트용 설정값 주입
        ReflectionTestUtils.setField(alertNotificationService, "sesRegion", "ap-northeast-2");
        ReflectionTestUtils.setField(alertNotificationService, "fromEmail", "test@apibridge.com");
        ReflectionTestUtils.setField(alertNotificationService, "mockEmailEnabled", true);
    }

    @Test
    @DisplayName("서비스 에러 알림 처리 성공 테스트")
    void processAlert_ServiceError_Success() {
        // Given
        AlertRequest alertRequest = AlertRequest.builder()
                .groupKey("service-error-alert-123")
                .status("firing")
                .receiver("webhook")
                .commonLabels(Map.of(
                        "alertname", "APIError",
                        "severity", "critical",
                        "service", "user-service"
                ))
                .alerts(List.of(
                        AlertRequest.Alert.builder()
                                .status("firing")
                                .labels(Map.of(
                                        "instance", "user-service-01",
                                        "job", "user-service"
                                ))
                                .annotations(Map.of(
                                        "summary", "User Service API 에러 발생",
                                        "description", "사용자 서비스에서 500 에러가 지속적으로 발생하고 있습니다."
                                ))
                                .startsAt(LocalDateTime.now())
                                .build()
                ))
                .build();

        doNothing().when(mockEmailService).sendEmails(anyList(), anyString(), anyString());

        // When
        AlertResponse result = alertNotificationService.processAlert(alertRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Alert notification processed successfully");
        assertThat(result.getAlertId()).isEqualTo("service-error-alert-123");
        assertThat(result.getNotificationChannels()).contains("email");
        assertThat(result.getRecipientCount()).isEqualTo(3); // 기본 관리자 이메일 3개

        verify(mockEmailService).sendEmails(anyList(), contains("[긴급]"), anyString());
    }

    @Test
    @DisplayName("성능 이슈 알림 처리 성공 테스트")
    void processAlert_PerformanceIssue_Success() {
        // Given
        AlertRequest alertRequest = AlertRequest.builder()
                .groupKey("performance-alert-456")
                .status("firing")
                .receiver("webhook")
                .commonLabels(Map.of(
                        "alertname", "HighCPU",
                        "severity", "warning",
                        "service", "payment-service"
                ))
                .alerts(List.of(
                        AlertRequest.Alert.builder()
                                .status("firing")
                                .labels(Map.of(
                                        "instance", "payment-service-01",
                                        "cpu_usage", "85"
                                ))
                                .annotations(Map.of(
                                        "summary", "CPU 사용률 높음",
                                        "description", "결제 서비스의 CPU 사용률이 85%를 초과했습니다."
                                ))
                                .startsAt(LocalDateTime.now())
                                .build()
                ))
                .build();

        doNothing().when(mockEmailService).sendEmails(anyList(), anyString(), anyString());

        // When
        AlertResponse result = alertNotificationService.processAlert(alertRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAlertId()).isEqualTo("performance-alert-456");

        verify(mockEmailService).sendEmails(anyList(), contains("[주의]"), anyString());
    }

    @Test
    @DisplayName("서비스 다운 알림 처리 성공 테스트")
    void processAlert_ServiceDown_Success() {
        // Given
        AlertRequest alertRequest = AlertRequest.builder()
                .groupKey("service-down-alert-789")
                .status("firing")
                .receiver("webhook")
                .commonLabels(Map.of(
                        "alertname", "ServiceDown",
                        "severity", "critical",
                        "service", "notification-service"
                ))
                .alerts(List.of(
                        AlertRequest.Alert.builder()
                                .status("firing")
                                .labels(Map.of(
                                        "instance", "notification-service-01",
                                        "healthcheck", "failed"
                                ))
                                .annotations(Map.of(
                                        "summary", "알림 서비스 다운",
                                        "description", "알림 서비스가 응답하지 않습니다."
                                ))
                                .startsAt(LocalDateTime.now())
                                .build()
                ))
                .build();

        doNothing().when(mockEmailService).sendEmails(anyList(), anyString(), anyString());

        // When
        AlertResponse result = alertNotificationService.processAlert(alertRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        verify(mockEmailService).sendEmails(anyList(), contains("서비스 다운 알림"), anyString());
    }

    @Test
    @DisplayName("외부 API 이슈 알림 처리 성공 테스트")
    void processAlert_ExternalApiIssue_Success() {
        // Given
        AlertRequest alertRequest = AlertRequest.builder()
                .groupKey("external-api-alert-101")
                .status("firing")
                .receiver("webhook")
                .commonLabels(Map.of(
                        "alertname", "ExternalAPIError",
                        "severity", "warning",
                        "api_provider", "weather-api"
                ))
                .alerts(List.of(
                        AlertRequest.Alert.builder()
                                .status("firing")
                                .labels(Map.of(
                                        "api_name", "weather-forecast",
                                        "error_rate", "15"
                                ))
                                .annotations(Map.of(
                                        "summary", "날씨 API 에러율 증가",
                                        "description", "날씨 예보 API에서 에러율이 15%를 초과했습니다."
                                ))
                                .startsAt(LocalDateTime.now())
                                .build()
                ))
                .build();

        doNothing().when(mockEmailService).sendEmails(anyList(), anyString(), anyString());

        // When
        AlertResponse result = alertNotificationService.processAlert(alertRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        verify(mockEmailService).sendEmails(anyList(), contains("외부 API 장애"), anyString());
    }

    @Test
    @DisplayName("빈 알림 리스트 처리 테스트")
    void processAlert_EmptyAlerts_Success() {
        // Given
        AlertRequest alertRequest = AlertRequest.builder()
                .groupKey("empty-alert")
                .status("resolved")
                .receiver("webhook")
                .commonLabels(Map.of(
                        "alertname", "TestAlert",
                        "severity", "info"
                ))
                .alerts(List.of()) // 빈 알림 리스트
                .build();

        doNothing().when(mockEmailService).sendEmails(anyList(), anyString(), anyString());

        // When
        AlertResponse result = alertNotificationService.processAlert(alertRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        verify(mockEmailService).sendEmails(anyList(), anyString(), anyString());
    }

    @Test
    @DisplayName("MockEmailService가 null인 경우 테스트")
    void processAlert_MockEmailServiceNull() {
        // Given
        ReflectionTestUtils.setField(alertNotificationService, "mockEmailEnabled", false);
        ReflectionTestUtils.setField(alertNotificationService, "mockEmailService", null);

        AlertRequest alertRequest = AlertRequest.builder()
                .groupKey("test-alert")
                .status("firing")
                .receiver("webhook")
                .commonLabels(Map.of("alertname", "TestAlert", "severity", "info"))
                .alerts(List.of())
                .build();

        // When
        AlertResponse result = alertNotificationService.processAlert(alertRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("알림 처리 실패 테스트")
    void processAlert_Failure() {
        // Given
        AlertRequest alertRequest = AlertRequest.builder()
                .groupKey("failure-alert")
                .status("firing")
                .receiver("webhook")
                .commonLabels(Map.of(
                        "alertname", "TestAlert",
                        "severity", "critical"
                ))
                .alerts(List.of(
                        AlertRequest.Alert.builder()
                                .status("firing")
                                .labels(Map.of("instance", "test-instance"))
                                .annotations(Map.of("summary", "Test failure"))
                                .startsAt(LocalDateTime.now())
                                .build()
                ))
                .build();

        doThrow(new RuntimeException("이메일 서버 연결 실패"))
                .when(mockEmailService).sendEmails(anyList(), anyString(), anyString());

        // When
        AlertResponse result = alertNotificationService.processAlert(alertRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Failed to process alert");
        assertThat(result.getAlertId()).isEqualTo("failure-alert");
        assertThat(result.getRecipientCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("알림 심각도별 이메일 제목 생성 테스트")
    void generateEmailSubject_DifferentSeverities() {
        // Given
        AlertRequest criticalAlert = createAlertRequest("critical", "APIError");
        AlertRequest warningAlert = createAlertRequest("warning", "HighCPU");
        AlertRequest infoAlert = createAlertRequest("info", "TestAlert");

        doNothing().when(mockEmailService).sendEmails(anyList(), anyString(), anyString());

        // When
        alertNotificationService.processAlert(criticalAlert);
        alertNotificationService.processAlert(warningAlert);
        alertNotificationService.processAlert(infoAlert);

        // Then
        verify(mockEmailService).sendEmails(anyList(), contains("[긴급]"), anyString());
        verify(mockEmailService).sendEmails(anyList(), contains("[주의]"), anyString());
        verify(mockEmailService).sendEmails(anyList(), contains("[알림]"), anyString());
    }

    @Test
    @DisplayName("알림 타입별 대응 가이드 포함 테스트")
    void processAlert_IncludesActionGuide() {
        // Given
        AlertRequest alertRequest = createAlertRequest("critical", "APIError");
        doNothing().when(mockEmailService).sendEmails(anyList(), anyString(), anyString());

        // When
        alertNotificationService.processAlert(alertRequest);

        // Then
        verify(mockEmailService).sendEmails(anyList(), anyString(), 
                argThat(content -> content.contains("대응 가이드") && 
                                  content.contains("서비스 로그를 확인하여")));
    }

    private AlertRequest createAlertRequest(String severity, String alertName) {
        return AlertRequest.builder()
                .groupKey("test-" + severity + "-" + System.currentTimeMillis())
                .status("firing")
                .receiver("webhook")
                .commonLabels(Map.of(
                        "alertname", alertName,
                        "severity", severity
                ))
                .alerts(List.of(
                        AlertRequest.Alert.builder()
                                .status("firing")
                                .labels(Map.of("instance", "test-instance"))
                                .annotations(Map.of("summary", "Test alert", "description", "Test description"))
                                .startsAt(LocalDateTime.now())
                                .build()
                ))
                .build();
    }
}