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
import org.example.SystemManagementSvc.dto.common.BaseResponse;
import org.example.SystemManagementSvc.event.model.CircuitBreakerEvent;
import org.example.SystemManagementSvc.service.CircuitBreakerMonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 서킷브레이커 관리 컨트롤러
 * 서킷브레이커 상태 조회, 수동 제어, 모니터링 API 제공
 */
@Slf4j
@RestController
@RequestMapping("/circuit-breaker")
@RequiredArgsConstructor
@Tag(name = "Circuit Breaker Management", description = "서킷브레이커 상태 관리 및 모니터링 API")
public class CircuitBreakerController {

    private final CircuitBreakerMonitoringService circuitBreakerMonitoringService;

    @Operation(
        summary = "모든 서킷브레이커 상태 조회",
        description = "현재 등록된 모든 API의 서킷브레이커 상태를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "서킷브레이커 상태 조회 성공",
            content = @Content(schema = @Schema(implementation = BaseResponse.class))
        ),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/status")
    public ResponseEntity<BaseResponse<Map<String, Object>>> getAllCircuitBreakerStatus() {
        log.info("Circuit breaker status request received");
        
        try {
            Map<String, CircuitBreakerEvent.CircuitBreakerState> allStates = 
                circuitBreakerMonitoringService.getAllCircuitBreakerStates();
            
            // 상태별 통계 계산
            long totalApis = allStates.size();
            long healthyCount = allStates.values().stream()
                .mapToLong(state -> state == CircuitBreakerEvent.CircuitBreakerState.CLOSED ? 1 : 0)
                .sum();
            long degradedCount = allStates.values().stream()
                .mapToLong(state -> state == CircuitBreakerEvent.CircuitBreakerState.DEGRADED ? 1 : 0)
                .sum();
            long openCount = allStates.values().stream()
                .mapToLong(state -> state.isBlocking() ? 1 : 0)
                .sum();
            
            Map<String, Object> response = Map.of(
                "totalApis", totalApis,
                "healthyCount", healthyCount,
                "degradedCount", degradedCount,
                "openCount", openCount,
                "healthRate", totalApis > 0 ? (double) healthyCount / totalApis * 100 : 100.0,
                "states", allStates,
                "lastUpdated", LocalDateTime.now()
            );
            
            BaseResponse<Map<String, Object>> result = BaseResponse.<Map<String, Object>>builder()
                .success(true)
                .message(String.format("서킷브레이커 상태 조회 성공: 총 %d개 API", totalApis))
                .data(response)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to get circuit breaker status", e);
            
            BaseResponse<Map<String, Object>> result = BaseResponse.<Map<String, Object>>builder()
                .success(false)
                .message("서킷브레이커 상태 조회 실패: " + e.getMessage())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(result);
        }
    }

    @Operation(
        summary = "특정 API 서킷브레이커 수동 제어",
        description = "관리자가 특정 API의 서킷브레이커 상태를 수동으로 변경합니다."
    )
    @PostMapping("/control")
    public ResponseEntity<BaseResponse<String>> controlCircuitBreaker(
            @Parameter(description = "API ID", required = true)
            @RequestParam String apiId,
            
            @Parameter(description = "API 이름", required = true)
            @RequestParam String apiName,
            
            @Parameter(description = "API 제공업체", required = true)
            @RequestParam String apiProvider,
            
            @Parameter(description = "강제 설정할 상태", required = true)
            @RequestParam CircuitBreakerEvent.CircuitBreakerState forcedState,
            
            @Parameter(description = "변경 사유", required = true)
            @RequestParam String reason) {
        
        log.info("Manual circuit breaker control requested - API: {}, State: {}, Reason: {}", 
                apiId, forcedState, reason);
        
        try {
            // 유효성 검증
            if (apiId == null || apiId.trim().isEmpty()) {
                throw new IllegalArgumentException("API ID는 필수 입력값입니다.");
            }
            
            if (reason == null || reason.trim().isEmpty()) {
                throw new IllegalArgumentException("변경 사유는 필수 입력값입니다.");
            }
            
            // 서킷브레이커 상태 강제 변경
            circuitBreakerMonitoringService.forceCircuitBreakerState(
                apiId, apiName, apiProvider, forcedState, reason
            );
            
            String message = String.format("API '%s'의 서킷브레이커 상태를 '%s'로 변경했습니다.", 
                                          apiName, forcedState.getDisplayName());
            
            BaseResponse<String> response = BaseResponse.<String>builder()
                .success(true)
                .message(message)
                .data(forcedState.name())
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid circuit breaker control request: {}", e.getMessage());
            
            BaseResponse<String> response = BaseResponse.<String>builder()
                .success(false)
                .message("요청 오류: " + e.getMessage())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            log.error("Failed to control circuit breaker for API: {}", apiId, e);
            
            BaseResponse<String> response = BaseResponse.<String>builder()
                .success(false)
                .message("서킷브레이커 제어 실패: " + e.getMessage())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(
        summary = "서킷브레이커 강제 리셋",
        description = "모든 API의 서킷브레이커를 정상 상태(CLOSED)로 강제 리셋합니다."
    )
    @PostMapping("/reset-all")
    public ResponseEntity<BaseResponse<Map<String, Object>>> resetAllCircuitBreakers(
            @Parameter(description = "리셋 사유", required = true)
            @RequestParam String reason) {
        
        log.info("Circuit breaker reset all requested - Reason: {}", reason);
        
        try {
            Map<String, CircuitBreakerEvent.CircuitBreakerState> currentStates = 
                circuitBreakerMonitoringService.getAllCircuitBreakerStates();
            
            int resetCount = 0;
            
            // 모든 API를 CLOSED 상태로 리셋
            for (String apiId : currentStates.keySet()) {
                CircuitBreakerEvent.CircuitBreakerState currentState = currentStates.get(apiId);
                
                if (currentState != CircuitBreakerEvent.CircuitBreakerState.CLOSED) {
                    circuitBreakerMonitoringService.forceCircuitBreakerState(
                        apiId, 
                        "API-" + apiId, // 실제로는 API 이름 조회 필요
                        "Unknown", 
                        CircuitBreakerEvent.CircuitBreakerState.CLOSED, 
                        "관리자에 의한 전체 리셋: " + reason
                    );
                    resetCount++;
                }
            }
            
            Map<String, Object> result = Map.of(
                "totalApis", currentStates.size(),
                "resetCount", resetCount,
                "skippedCount", currentStates.size() - resetCount,
                "reason", reason,
                "resetTime", LocalDateTime.now()
            );
            
            BaseResponse<Map<String, Object>> response = BaseResponse.<Map<String, Object>>builder()
                .success(true)
                .message(String.format("서킷브레이커 전체 리셋 완료: %d개 API 리셋됨", resetCount))
                .data(result)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to reset all circuit breakers", e);
            
            BaseResponse<Map<String, Object>> response = BaseResponse.<Map<String, Object>>builder()
                .success(false)
                .message("서킷브레이커 전체 리셋 실패: " + e.getMessage())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(
        summary = "서킷브레이커 모니터링 수동 실행",
        description = "정기 스케줄과 별도로 서킷브레이커 모니터링을 수동으로 실행합니다."
    )
    @PostMapping("/monitor/trigger")
    public ResponseEntity<BaseResponse<String>> triggerMonitoring() {
        log.info("Manual circuit breaker monitoring triggered");
        
        try {
            // 별도 스레드에서 모니터링 실행 (응답 속도 향상)
            new Thread(() -> {
                try {
                    circuitBreakerMonitoringService.monitorCircuitBreakers();
                    log.info("Manual circuit breaker monitoring completed successfully");
                } catch (Exception e) {
                    log.error("Manual circuit breaker monitoring failed", e);
                }
            }).start();
            
            BaseResponse<String> response = BaseResponse.<String>builder()
                .success(true)
                .message("서킷브레이커 모니터링이 시작되었습니다. 결과는 로그에서 확인하세요.")
                .data("monitoring_started")
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to trigger circuit breaker monitoring", e);
            
            BaseResponse<String> response = BaseResponse.<String>builder()
                .success(false)
                .message("서킷브레이커 모니터링 시작 실패: " + e.getMessage())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(
        summary = "서킷브레이커 설정 정보 조회",
        description = "현재 서킷브레이커 모니터링에 사용되는 임계치 설정을 조회합니다."
    )
    @GetMapping("/config")
    public ResponseEntity<BaseResponse<Map<String, Object>>> getCircuitBreakerConfig() {
        log.info("Circuit breaker configuration requested");
        
        try {
            // 설정 정보 수집 (실제로는 @Value로 주입된 값들 사용)
            Map<String, Object> config = Map.of(
                "callRateThreshold", "1000 calls/minute",
                "failureRateThreshold", "50.0%",
                "consecutiveFailuresThreshold", 5,
                "responseTimeThreshold", "5000ms",
                "monitoringWindow", "5 minutes",
                "monitoringInterval", "1 minute",
                "autoRecoveryTime", "5 minutes",
                "supportedStates", java.util.Arrays.asList(
                    CircuitBreakerEvent.CircuitBreakerState.values()
                ),
                "lastConfigUpdate", LocalDateTime.now()
            );
            
            BaseResponse<Map<String, Object>> response = BaseResponse.<Map<String, Object>>builder()
                .success(true)
                .message("서킷브레이커 설정 정보 조회 성공")
                .data(config)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get circuit breaker configuration", e);
            
            BaseResponse<Map<String, Object>> response = BaseResponse.<Map<String, Object>>builder()
                .success(false)
                .message("서킷브레이커 설정 조회 실패: " + e.getMessage())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(
        summary = "서킷브레이커 통계 조회",
        description = "서킷브레이커 동작 통계와 성능 지표를 조회합니다."
    )
    @GetMapping("/statistics")
    public ResponseEntity<BaseResponse<Map<String, Object>>> getCircuitBreakerStatistics(
            @Parameter(description = "조회 기간 (시간 단위)", example = "24")
            @RequestParam(defaultValue = "24") int hours) {
        
        log.info("Circuit breaker statistics requested for last {} hours", hours);
        
        try {
            Map<String, CircuitBreakerEvent.CircuitBreakerState> allStates = 
                circuitBreakerMonitoringService.getAllCircuitBreakerStates();
            
            // 기본 통계 계산
            long totalApis = allStates.size();
            Map<CircuitBreakerEvent.CircuitBreakerState, Long> stateDistribution = 
                allStates.values().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                        state -> state, 
                        java.util.stream.Collectors.counting()));
            
            // 안정성 지수 계산 (0-100)
            double stabilityIndex = totalApis > 0 ? 
                (double) stateDistribution.getOrDefault(CircuitBreakerEvent.CircuitBreakerState.CLOSED, 0L) 
                / totalApis * 100 : 100.0;
            
            Map<String, Object> statistics = Map.of(
                "period", hours + " hours",
                "totalApis", totalApis,
                "stateDistribution", stateDistribution,
                "stabilityIndex", Math.round(stabilityIndex * 100.0) / 100.0,
                "monitoringActive", true,
                "lastUpdate", LocalDateTime.now()
            );
            
            BaseResponse<Map<String, Object>> response = BaseResponse.<Map<String, Object>>builder()
                .success(true)
                .message("서킷브레이커 통계 조회 성공")
                .data(statistics)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get circuit breaker statistics", e);
            
            BaseResponse<Map<String, Object>> response = BaseResponse.<Map<String, Object>>builder()
                .success(false)
                .message("서킷브레이커 통계 조회 실패: " + e.getMessage())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(response);
        }
    }
}