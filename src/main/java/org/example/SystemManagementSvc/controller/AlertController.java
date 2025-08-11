package org.example.SystemManagementSvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.SystemManagementSvc.dto.analytics.AlertRequest;
import org.example.SystemManagementSvc.dto.analytics.AlertResponse;
import org.example.SystemManagementSvc.dto.common.BaseResponse;
import org.example.SystemManagementSvc.service.AlertNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Prometheus Alertmanager 연동을 위한 알림 처리 컨트롤러
 * Webhook을 통해 전달되는 알림을 수신하고 처리
 */
@Slf4j
@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
@Tag(name = "Alert Management", description = "Prometheus Alertmanager 연동 및 알림 처리 API")
public class AlertController {

    private final AlertNotificationService alertNotificationService;

    @Operation(
        summary = "Prometheus Alertmanager Webhook 수신",
        description = "Prometheus Alertmanager에서 전송되는 알림을 수신하여 이메일, Slack 등의 채널로 전파합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "알림 처리 성공",
            content = @Content(schema = @Schema(implementation = BaseResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 알림 데이터"),
        @ApiResponse(responseCode = "500", description = "알림 처리 중 서버 오류")
    })
    @PostMapping("/webhook")
    public ResponseEntity<BaseResponse<AlertResponse>> receiveAlert(
            @Parameter(description = "Prometheus Alertmanager 알림 데이터", required = true)
            @RequestBody AlertRequest alertRequest) {
        
        long startTime = System.currentTimeMillis();
        
        log.info("Received alert webhook - GroupKey: {}, Status: {}, Alerts count: {}", 
            alertRequest.getGroupKey(), 
            alertRequest.getStatus(), 
            alertRequest.getAlerts() != null ? alertRequest.getAlerts().size() : 0);
        
        try {
            // 알림 처리 및 이메일 발송
            AlertResponse alertResponse = alertNotificationService.processAlert(alertRequest);
            
            // 처리 시간 계산
            long processingTime = System.currentTimeMillis() - startTime;
            alertResponse.setProcessingTimeMs(processingTime);
            
            BaseResponse<AlertResponse> response = BaseResponse.<AlertResponse>builder()
                .success(alertResponse.isSuccess())
                .message(alertResponse.isSuccess() ? "알림이 성공적으로 처리되었습니다" : "알림 처리 중 오류가 발생했습니다")
                .data(alertResponse)
                .timestamp(java.time.LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to process alert webhook", e);
            
            AlertResponse errorResponse = AlertResponse.builder()
                .success(false)
                .message("서버 오류로 인한 알림 처리 실패")
                .alertId(alertRequest.getGroupKey())
                .processedAt(java.time.LocalDateTime.now())
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .errorDetails(e.getMessage())
                .retryRequested(true)
                .build();
            
            BaseResponse<AlertResponse> response = BaseResponse.<AlertResponse>builder()
                .success(false)
                .message("알림 처리 실패")
                .data(errorResponse)
                .timestamp(java.time.LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(
        summary = "알림 설정 테스트",
        description = "이메일 발송 및 알림 시스템이 정상 작동하는지 테스트합니다."
    )
    @PostMapping("/test")
    public ResponseEntity<BaseResponse<AlertResponse>> testAlert(
            @Parameter(description = "테스트 알림 타입 (error, warning, info)", example = "warning")
            @RequestParam(defaultValue = "warning") String severity,
            
            @Parameter(description = "테스트 메시지", example = "시스템 테스트 알림입니다")
            @RequestParam(defaultValue = "시스템 테스트 알림입니다") String message) {
        
        log.info("Test alert requested - severity: {}, message: {}", severity, message);
        
        try {
            // 테스트 알림 데이터 생성
            AlertRequest testAlert = AlertRequest.builder()
                .groupKey("test-alert-" + System.currentTimeMillis())
                .status("firing")
                .receiver("test-webhook")
                .commonLabels(java.util.Map.of(
                    "alertname", "TestAlert",
                    "severity", severity,
                    "service", "systemmanagement-svc"
                ))
                .alerts(java.util.List.of(
                    AlertRequest.Alert.builder()
                        .status("firing")
                        .labels(java.util.Map.of(
                            "alertname", "TestAlert",
                            "instance", "localhost:8080",
                            "job", "systemmanagement-svc"
                        ))
                        .annotations(java.util.Map.of(
                            "summary", "테스트 알림 발송",
                            "description", message
                        ))
                        .startsAt(java.time.LocalDateTime.now())
                        .build()
                ))
                .build();
            
            AlertResponse alertResponse = alertNotificationService.processAlert(testAlert);
            
            BaseResponse<AlertResponse> response = BaseResponse.<AlertResponse>builder()
                .success(alertResponse.isSuccess())
                .message("테스트 알림이 발송되었습니다")
                .data(alertResponse)
                .timestamp(java.time.LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to send test alert", e);
            
            BaseResponse<AlertResponse> response = BaseResponse.<AlertResponse>builder()
                .success(false)
                .message("테스트 알림 발송 실패: " + e.getMessage())
                .data(null)
                .timestamp(java.time.LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(
        summary = "알림 상태 확인",
        description = "알림 시스템의 현재 상태와 설정을 확인합니다."
    )
    @GetMapping("/status")
    public ResponseEntity<BaseResponse<java.util.Map<String, Object>>> getAlertStatus() {
        
        log.debug("Alert status check requested");
        
        try {
            java.util.Map<String, Object> status = java.util.Map.of(
                "alertingEnabled", true,
                "emailEnabled", true,
                "slackEnabled", false,  // 추후 구현
                "teamsEnabled", false,  // 추후 구현
                "lastHealthCheck", java.time.LocalDateTime.now(),
                "supportedSeverities", java.util.List.of("critical", "warning", "info"),
                "supportedChannels", java.util.List.of("email")
            );
            
            BaseResponse<java.util.Map<String, Object>> response = BaseResponse.<java.util.Map<String, Object>>builder()
                .success(true)
                .message("알림 시스템 상태 조회 성공")
                .data(status)
                .timestamp(java.time.LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get alert status", e);
            
            BaseResponse<java.util.Map<String, Object>> response = BaseResponse.<java.util.Map<String, Object>>builder()
                .success(false)
                .message("알림 상태 조회 실패: " + e.getMessage())
                .data(null)
                .timestamp(java.time.LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(response);
        }
    }
}