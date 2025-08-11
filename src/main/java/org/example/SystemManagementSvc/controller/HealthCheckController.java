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
import org.example.SystemManagementSvc.domain.ExternalApi;
import org.example.SystemManagementSvc.domain.HealthCheckResult;
import org.example.SystemManagementSvc.dto.common.BaseResponse;
import org.example.SystemManagementSvc.repository.ExternalApiRepository;
import org.example.SystemManagementSvc.repository.HealthCheckResultRepository;
import org.example.SystemManagementSvc.service.HealthCheckService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 외부 API 헬스체크 관리 컨트롤러
 * 헬스체크 실행, 결과 조회, 상태 모니터링 API 제공
 */
@Slf4j
@RestController
@RequestMapping("/health-check")
@RequiredArgsConstructor
@Tag(name = "Health Check Management", description = "외부 API 헬스체크 관리 및 모니터링 API")
public class HealthCheckController {

    private final HealthCheckService healthCheckService;
    private final ExternalApiRepository externalApiRepository;
    private final HealthCheckResultRepository healthCheckResultRepository;

    @Operation(
        summary = "전체 API 헬스체크 실행",
        description = "등록된 모든 외부 API에 대해 병렬 헬스체크를 수행하고 결과를 반환합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "헬스체크 실행 성공",
            content = @Content(schema = @Schema(implementation = BaseResponse.class))
        ),
        @ApiResponse(responseCode = "500", description = "헬스체크 실행 중 서버 오류")
    })
    @PostMapping("/run")
    public ResponseEntity<BaseResponse<Map<String, Object>>> runHealthCheckForAllApis() {
        log.info("Manual health check requested for all APIs");
        
        try {
            CompletableFuture<Map<String, HealthCheckResult>> healthCheckFuture = 
                healthCheckService.performHealthCheckForAllApis();
            
            Map<String, HealthCheckResult> results = healthCheckFuture.get();
            
            // 결과 요약 생성
            long totalApis = results.size();
            long healthyApis = results.values().stream()
                .mapToLong(result -> result.isSuccess() ? 1 : 0)
                .sum();
            long unhealthyApis = totalApis - healthyApis;
            
            double averageResponseTime = results.values().stream()
                .filter(result -> result.getResponseTimeMs() != null)
                .mapToLong(HealthCheckResult::getResponseTimeMs)
                .average()
                .orElse(0.0);
            
            Map<String, Object> summary = Map.of(
                "totalApis", totalApis,
                "healthyApis", healthyApis,
                "unhealthyApis", unhealthyApis,
                "healthRate", totalApis > 0 ? (double) healthyApis / totalApis * 100 : 0,
                "averageResponseTime", Math.round(averageResponseTime),
                "executionTime", LocalDateTime.now(),
                "results", results
            );
            
            BaseResponse<Map<String, Object>> response = BaseResponse.<Map<String, Object>>builder()
                .success(true)
                .message(String.format("헬스체크 완료: %d개 API 중 %d개 정상", totalApis, healthyApis))
                .data(summary)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to run health check for all APIs", e);
            
            BaseResponse<Map<String, Object>> response = BaseResponse.<Map<String, Object>>builder()
                .success(false)
                .message("헬스체크 실행 실패: " + e.getMessage())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(
        summary = "특정 API 헬스체크 실행",
        description = "지정된 API에 대해서만 헬스체크를 수행합니다."
    )
    @PostMapping("/run/{apiId}")
    public ResponseEntity<BaseResponse<HealthCheckResult>> runHealthCheckForSingleApi(
            @Parameter(description = "헬스체크를 수행할 API ID", required = true)
            @PathVariable String apiId) {
        
        log.info("Manual health check requested for API: {}", apiId);
        
        try {
            ExternalApi api = externalApiRepository.findById(apiId)
                .orElseThrow(() -> new IllegalArgumentException("API not found: " + apiId));
            
            CompletableFuture<HealthCheckResult> healthCheckFuture = 
                healthCheckService.performSingleHealthCheckAsync(api);
            
            HealthCheckResult result = healthCheckFuture.get();
            
            BaseResponse<HealthCheckResult> response = BaseResponse.<HealthCheckResult>builder()
                .success(true)
                .message("헬스체크 완료: " + result.getStatus().getDisplayName())
                .data(result)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid API ID for health check: {}", apiId);
            
            BaseResponse<HealthCheckResult> response = BaseResponse.<HealthCheckResult>builder()
                .success(false)
                .message(e.getMessage())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            log.error("Failed to run health check for API: {}", apiId, e);
            
            BaseResponse<HealthCheckResult> response = BaseResponse.<HealthCheckResult>builder()
                .success(false)
                .message("헬스체크 실행 실패: " + e.getMessage())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(
        summary = "헬스체크 결과 조회",
        description = "지정된 기간 동안의 헬스체크 결과를 페이징하여 조회합니다."
    )
    @GetMapping("/results")
    public ResponseEntity<BaseResponse<Page<HealthCheckResult>>> getHealthCheckResults(
            @Parameter(description = "조회할 시간 범위 (시간 단위)", example = "24")
            @RequestParam(defaultValue = "24") int hours,
            
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "정렬 기준 (checkedAt, status, responseTimeMs)", example = "checkedAt")
            @RequestParam(defaultValue = "checkedAt") String sortBy,
            
            @Parameter(description = "정렬 방향 (asc, desc)", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDirection) {
        
        log.info("Health check results requested - hours: {}, page: {}, size: {}", hours, page, size);
        
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(hours);
            
            Sort.Direction direction = sortDirection.equalsIgnoreCase("asc") ? 
                Sort.Direction.ASC : Sort.Direction.DESC;
            
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
            
            // Repository에서 페이징 지원이 필요한 경우 JpaRepository를 확장
            List<HealthCheckResult> results = healthCheckResultRepository
                .findByCheckedAtBetweenOrderByCheckedAtDesc(startTime, endTime);
            
            // 간단한 페이징 구현 (실제로는 Repository에서 지원하는 것이 좋음)
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), results.size());
            List<HealthCheckResult> pageContent = results.subList(start, end);
            
            Page<HealthCheckResult> pageResult = new org.springframework.data.domain.PageImpl<>(
                pageContent, pageable, results.size()
            );
            
            BaseResponse<Page<HealthCheckResult>> response = BaseResponse.<Page<HealthCheckResult>>builder()
                .success(true)
                .message(String.format("헬스체크 결과 조회 성공: %d건", pageResult.getTotalElements()))
                .data(pageResult)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get health check results", e);
            
            BaseResponse<Page<HealthCheckResult>> response = BaseResponse.<Page<HealthCheckResult>>builder()
                .success(false)
                .message("헬스체크 결과 조회 실패: " + e.getMessage())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(
        summary = "현재 비정상 API 목록 조회",
        description = "현재 비정상 상태인 API 목록을 Redis 캐시에서 조회합니다."
    )
    @GetMapping("/unhealthy")
    public ResponseEntity<BaseResponse<List<String>>> getUnhealthyApis() {
        log.info("Unhealthy APIs list requested");
        
        try {
            List<String> unhealthyApiIds = healthCheckService.getUnhealthyApisFromCache();
            
            BaseResponse<List<String>> response = BaseResponse.<List<String>>builder()
                .success(true)
                .message(String.format("현재 비정상 API: %d개", unhealthyApiIds.size()))
                .data(unhealthyApiIds)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get unhealthy APIs", e);
            
            BaseResponse<List<String>> response = BaseResponse.<List<String>>builder()
                .success(false)
                .message("비정상 API 목록 조회 실패: " + e.getMessage())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(
        summary = "API 헬스체크 통계 조회",
        description = "지정된 기간의 API별 헬스체크 성공률 및 평균 응답시간 통계를 제공합니다."
    )
    @GetMapping("/statistics")
    public ResponseEntity<BaseResponse<Map<String, Object>>> getHealthCheckStatistics(
            @Parameter(description = "통계 조회 기간 (시간 단위)", example = "24")
            @RequestParam(defaultValue = "24") int hours) {
        
        log.info("Health check statistics requested for last {} hours", hours);
        
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            
            // API별 성공률 통계
            List<Object[]> successRateStats = healthCheckResultRepository.getSuccessRateByApi(since);
            
            // API별 평균 응답시간 통계
            List<Object[]> responseTimeStats = healthCheckResultRepository.getAverageResponseTimeByApi(since);
            
            // 전체 통계
            Object overallStats = healthCheckResultRepository.getOverallHealthStatistics(since);
            
            Map<String, Object> statistics = Map.of(
                "period", hours + " hours",
                "since", since,
                "successRateByApi", successRateStats,
                "averageResponseTimeByApi", responseTimeStats,
                "overallStatistics", overallStats
            );
            
            BaseResponse<Map<String, Object>> response = BaseResponse.<Map<String, Object>>builder()
                .success(true)
                .message("헬스체크 통계 조회 성공")
                .data(statistics)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get health check statistics", e);
            
            BaseResponse<Map<String, Object>> response = BaseResponse.<Map<String, Object>>builder()
                .success(false)
                .message("헬스체크 통계 조회 실패: " + e.getMessage())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(
        summary = "특정 API 헬스체크 이력 조회",
        description = "특정 API의 최근 헬스체크 이력을 조회합니다."
    )
    @GetMapping("/history/{apiId}")
    public ResponseEntity<BaseResponse<List<HealthCheckResult>>> getApiHealthHistory(
            @Parameter(description = "조회할 API ID", required = true)
            @PathVariable String apiId,
            
            @Parameter(description = "조회할 결과 수", example = "50")
            @RequestParam(defaultValue = "50") int limit) {
        
        log.info("Health check history requested for API: {}, limit: {}", apiId, limit);
        
        try {
            List<HealthCheckResult> history = healthCheckResultRepository
                .findByApiIdOrderByCheckedAtDesc(apiId)
                .stream()
                .limit(limit)
                .toList();
            
            BaseResponse<List<HealthCheckResult>> response = BaseResponse.<List<HealthCheckResult>>builder()
                .success(true)
                .message(String.format("API 헬스체크 이력 조회 성공: %d건", history.size()))
                .data(history)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get health check history for API: {}", apiId, e);
            
            BaseResponse<List<HealthCheckResult>> response = BaseResponse.<List<HealthCheckResult>>builder()
                .success(false)
                .message("헬스체크 이력 조회 실패: " + e.getMessage())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(
        summary = "등록된 API 목록 조회",
        description = "헬스체크 대상 API 목록을 조회합니다."
    )
    @GetMapping("/apis")
    public ResponseEntity<BaseResponse<List<ExternalApi>>> getRegisteredApis(
            @Parameter(description = "활성화된 API만 조회 여부", example = "true")
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        
        log.info("Registered APIs list requested - activeOnly: {}", activeOnly);
        
        try {
            List<ExternalApi> apis = activeOnly ?
                externalApiRepository.findByApiEffectivenessTrue() :
                externalApiRepository.findAll();
            
            BaseResponse<List<ExternalApi>> response = BaseResponse.<List<ExternalApi>>builder()
                .success(true)
                .message(String.format("등록된 API 목록 조회 성공: %d개", apis.size()))
                .data(apis)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get registered APIs", e);
            
            BaseResponse<List<ExternalApi>> response = BaseResponse.<List<ExternalApi>>builder()
                .success(false)
                .message("API 목록 조회 실패: " + e.getMessage())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
            
            return ResponseEntity.status(500).body(response);
        }
    }
}