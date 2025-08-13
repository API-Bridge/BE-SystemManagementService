package org.example.SystemManagementSvc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.SystemManagementSvc.domain.ExternalApi;
import org.example.SystemManagementSvc.dto.ApiAvailabilityResponse;
import org.example.SystemManagementSvc.dto.ApiStatusSummary;
import org.example.SystemManagementSvc.service.AdvancedHealthCheckService;
import org.example.SystemManagementSvc.service.ApiStatusManager;
import org.example.SystemManagementSvc.service.RedisHealthStateManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API 헬스체크 및 상태 관리 REST 컨트롤러
 * 
 * 제공 기능:
 * - 실시간 API 가용성 확인
 * - 도메인/키워드별 API 조회
 * - 헬스체크 수동 실행
 * - 상태 통계 및 요약 정보
 */
@Slf4j
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
@Tag(name = "API Health Management", description = "API 헬스체크 및 상태 관리")
public class ApiHealthController {

    private final ApiStatusManager apiStatusManager;
    private final AdvancedHealthCheckService advancedHealthCheckService;
    private final RedisHealthStateManager redisHealthStateManager;

    /**
     * 전체 API 상태 요약 조회
     */
    @GetMapping("/summary")
    @Operation(summary = "전체 API 상태 요약", description = "시스템 전체의 API 가용성 현황을 요약하여 반환")
    public ResponseEntity<ApiStatusSummary> getApiStatusSummary() {
        try {
            ApiStatusSummary summary = apiStatusManager.getApiStatusSummary();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Failed to get API status summary", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 특정 API 상태 조회
     */
    @GetMapping("/status/{apiId}")
    @Operation(summary = "특정 API 상태 조회", description = "지정된 API의 상세 상태 정보를 반환")
    public ResponseEntity<ApiAvailabilityResponse> getApiStatus(
            @Parameter(description = "API 고유 식별자") @PathVariable String apiId) {
        try {
            ApiAvailabilityResponse status = apiStatusManager.getApiStatusDetails(apiId);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get API status for: {}", apiId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 여러 API 가용성 일괄 확인
     */
    @PostMapping("/availability/batch")
    @Operation(summary = "다중 API 가용성 확인", description = "여러 API의 가용성을 일괄로 확인")
    public ResponseEntity<Map<String, Boolean>> checkMultipleApiAvailability(
            @RequestBody List<String> apiIds) {
        try {
            Map<String, Boolean> availability = apiStatusManager.checkApisAvailability(apiIds);
            return ResponseEntity.ok(availability);
        } catch (Exception e) {
            log.error("Failed to check multiple API availability", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 도메인별 가용한 API 목록 조회
     */
    @GetMapping("/available/domain/{domain}")
    @Operation(summary = "도메인별 가용 API 조회", description = "특정 도메인의 현재 가용한 API 목록을 반환")
    public ResponseEntity<List<ExternalApi>> getAvailableApisByDomain(
            @Parameter(description = "API 도메인") @PathVariable ExternalApi.ApiDomain domain) {
        try {
            List<ExternalApi> apis = apiStatusManager.getAvailableApisByDomain(domain);
            return ResponseEntity.ok(apis);
        } catch (Exception e) {
            log.error("Failed to get available APIs by domain: {}", domain, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 키워드별 가용한 API 목록 조회
     */
    @GetMapping("/available/keyword/{keyword}")
    @Operation(summary = "키워드별 가용 API 조회", description = "특정 키워드의 현재 가용한 API 목록을 반환")
    public ResponseEntity<List<ExternalApi>> getAvailableApisByKeyword(
            @Parameter(description = "API 키워드") @PathVariable ExternalApi.ApiKeyword keyword) {
        try {
            List<ExternalApi> apis = apiStatusManager.getAvailableApisByKeyword(keyword);
            return ResponseEntity.ok(apis);
        } catch (Exception e) {
            log.error("Failed to get available APIs by keyword: {}", keyword, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 우선순위별 가용한 API 목록 조회
     */
    @GetMapping("/available/priority")
    @Operation(summary = "우선순위별 가용 API 조회", description = "헬스체크 우선순위별로 그룹화된 가용 API 목록을 반환")
    public ResponseEntity<Map<ExternalApi.HealthCheckPriority, List<ExternalApi>>> getAvailableApisByPriority() {
        try {
            Map<ExternalApi.HealthCheckPriority, List<ExternalApi>> apis = apiStatusManager.getAvailableApisByPriority();
            return ResponseEntity.ok(apis);
        } catch (Exception e) {
            log.error("Failed to get available APIs by priority", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 현재 불가용한 API 목록 조회
     */
    @GetMapping("/unavailable")
    @Operation(summary = "불가용 API 목록", description = "현재 사용할 수 없는 API의 식별자 목록을 반환")
    public ResponseEntity<List<String>> getUnavailableApis() {
        try {
            List<String> unavailableApis = apiStatusManager.getUnavailableApiIds();
            return ResponseEntity.ok(unavailableApis);
        } catch (Exception e) {
            log.error("Failed to get unavailable APIs", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 복구 예상 시간 조회
     */
    @GetMapping("/recovery-estimates")
    @Operation(summary = "복구 예상 시간", description = "장애 중인 API들의 예상 복구 시간(TTL 기반)을 반환")
    public ResponseEntity<Map<String, Long>> getRecoveryEstimates() {
        try {
            Map<String, Long> estimates = apiStatusManager.getRecoveryEstimates();
            return ResponseEntity.ok(estimates);
        } catch (Exception e) {
            log.error("Failed to get recovery estimates", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 장애 통계 정보 조회
     */
    @GetMapping("/failure-statistics")
    @Operation(summary = "장애 통계", description = "현재 장애 중인 API들의 상세 통계 정보를 반환")
    public ResponseEntity<Map<String, Object>> getFailureStatistics() {
        try {
            Map<String, Object> statistics = redisHealthStateManager.getFailureStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("Failed to get failure statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 수동 헬스체크 실행
     */
    @PostMapping("/check/manual")
    @Operation(summary = "수동 헬스체크 실행", description = "모든 활성 API에 대해 즉시 헬스체크를 수행")
    public ResponseEntity<Map<String, Object>> triggerManualHealthCheck() {
        try {
            log.info("Manual health check triggered via API");
            
            advancedHealthCheckService.performIntelligentHealthCheck()
                .thenAccept(results -> {
                    log.info("Manual health check completed for {} APIs", results.size());
                });
            
            return ResponseEntity.ok(Map.of(
                "message", "Health check initiated",
                "status", "STARTED",
                "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Failed to trigger manual health check", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to start health check", "message", e.getMessage()));
        }
    }

    /**
     * 특정 API 수동 헬스체크
     */
    @PostMapping("/check/manual/{apiId}")
    @Operation(summary = "특정 API 헬스체크", description = "지정된 API에 대해서만 즉시 헬스체크를 수행")
    public ResponseEntity<Map<String, Object>> triggerSingleApiHealthCheck(
            @Parameter(description = "API 고유 식별자") @PathVariable String apiId) {
        try {
            // TODO: API 존재 여부 확인 후 단일 API 헬스체크 수행
            log.info("Manual health check triggered for API: {}", apiId);
            
            return ResponseEntity.ok(Map.of(
                "message", "Health check initiated for API: " + apiId,
                "apiId", apiId,
                "status", "STARTED",
                "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Failed to trigger health check for API: {}", apiId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to start health check", "apiId", apiId, "message", e.getMessage()));
        }
    }

    /**
     * Redis 캐시 상태 조회
     */
    @GetMapping("/cache/status")
    @Operation(summary = "캐시 상태 조회", description = "Redis 기반 헬스체크 캐시의 현재 상태를 반환")
    public ResponseEntity<Map<String, Object>> getCacheStatus() {
        try {
            Map<String, Object> cacheStatus = Map.of(
                "unhealthyApis", redisHealthStateManager.getFailureStatistics(),
                "timestamp", java.time.LocalDateTime.now()
            );
            
            return ResponseEntity.ok(cacheStatus);
            
        } catch (Exception e) {
            log.error("Failed to get cache status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 조기 복구 감지 트리거
     */
    @PostMapping("/recovery/early-check/{apiId}")
    @Operation(summary = "조기 복구 감지", description = "특정 API의 조기 복구 가능성을 확인하고 TTL을 조정")
    public ResponseEntity<Map<String, Object>> triggerEarlyRecoveryCheck(
            @Parameter(description = "API 고유 식별자") @PathVariable String apiId) {
        try {
            redisHealthStateManager.performEarlyRecoveryCheck(apiId);
            
            return ResponseEntity.ok(Map.of(
                "message", "Early recovery check performed",
                "apiId", apiId,
                "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Failed to perform early recovery check for API: {}", apiId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to perform early recovery check", "apiId", apiId));
        }
    }

    /**
     * 시스템 건강도 요약 (간단한 상태 확인용)
     */
    @GetMapping("/system/health")
    @Operation(summary = "시스템 건강도", description = "전체 시스템의 간단한 건강도 정보를 반환")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        try {
            ApiStatusSummary summary = apiStatusManager.getApiStatusSummary();
            
            Map<String, Object> health = Map.of(
                "status", summary.getSystemHealth().name(),
                "description", summary.getAvailabilityStatusText(),
                "availabilityRate", summary.getAvailabilityRate(),
                "availableApis", summary.getAvailableApis(),
                "totalApis", summary.getEffectiveApis(),
                "lastUpdated", summary.getLastUpdated()
            );
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            log.error("Failed to get system health", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("status", "UNKNOWN", "error", "Failed to check system health"));
        }
    }
}