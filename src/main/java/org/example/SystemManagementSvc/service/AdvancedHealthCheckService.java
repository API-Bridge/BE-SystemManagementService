package org.example.SystemManagementSvc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.SystemManagementSvc.domain.ExternalApi;
import org.example.SystemManagementSvc.domain.HealthCheckResult;
import org.example.SystemManagementSvc.repository.ExternalApiRepository;
import org.example.SystemManagementSvc.repository.HealthCheckResultRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 향상된 헬스체크 서비스
 * - 지능형 정적/동적 헬스체크 전략
 * - Redis TTL 기반 효율적 상태 관리
 * - 우선순위 기반 스케줄링
 * - 장애 복구 자동 감지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvancedHealthCheckService {

    private final ExternalApiRepository externalApiRepository;
    private final HealthCheckResultRepository healthCheckResultRepository;
    private final RedisHealthStateManager redisHealthStateManager;
    private final ApiStatusManager apiStatusManager;
    private final PrometheusMetricsService prometheusMetricsService;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${healthcheck.static.timeout:5}")
    private int staticTimeoutSeconds;
    
    @Value("${healthcheck.dynamic.timeout:10}")
    private int dynamicTimeoutSeconds;
    
    @Value("${healthcheck.dynamic.validation.enabled:true}")
    private boolean dynamicValidationEnabled;
    
    @Value("${healthcheck.concurrent.max-threads:10}")
    private int maxConcurrentThreads;
    
    private static final Duration STATIC_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DYNAMIC_TIMEOUT = Duration.ofSeconds(10);
    private static final String HEALTH_ENDPOINT_SUFFIX = "/health";
    private static final String STATUS_ENDPOINT_SUFFIX = "/status";
    
    /**
     * 모든 API에 대한 지능형 헬스체크 수행
     */
    public CompletableFuture<Map<String, HealthCheckResult>> performIntelligentHealthCheck() {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting intelligent health check for all APIs");
            
            try {
                // 활성화된 API 목록 조회
                List<ExternalApi> activeApis = externalApiRepository.findByApiEffectivenessTrue();
                
                if (activeApis.isEmpty()) {
                    log.info("No active APIs found for health check");
                    return Map.of();
                }
                
                // 우선순위별로 그룹화
                Map<ExternalApi.HealthCheckPriority, List<ExternalApi>> priorityGroups = 
                    activeApis.stream().collect(Collectors.groupingBy(ExternalApi::getHealthCheckPriority));
                
                // 우선순위 순서대로 병렬 헬스체크 수행
                Map<String, HealthCheckResult> allResults = new HashMap<>();
                
                for (ExternalApi.HealthCheckPriority priority : ExternalApi.HealthCheckPriority.values()) {
                    List<ExternalApi> apis = priorityGroups.getOrDefault(priority, List.of());
                    if (!apis.isEmpty()) {
                        Map<String, HealthCheckResult> priorityResults = performPriorityGroupHealthCheck(apis);
                        allResults.putAll(priorityResults);
                    }
                }
                
                log.info("Intelligent health check completed for {} APIs", allResults.size());
                return allResults;
                
            } catch (Exception e) {
                log.error("Failed to perform intelligent health check", e);
                return Map.of();
            }
        });
    }
    
    /**
     * 우선순위 그룹별 병렬 헬스체크
     */
    private Map<String, HealthCheckResult> performPriorityGroupHealthCheck(List<ExternalApi> apis) {
        log.debug("Performing health check for {} APIs with priority: {}", 
                 apis.size(), apis.get(0).getHealthCheckPriority());
        
        // 병렬 헬스체크 실행
        List<CompletableFuture<HealthCheckResult>> healthCheckTasks = apis.stream()
            .map(this::performSingleApiHealthCheckAsync)
            .collect(Collectors.toList());
        
        // 모든 헬스체크 완료 대기
        CompletableFuture<Void> allTasks = CompletableFuture.allOf(
            healthCheckTasks.toArray(new CompletableFuture[0])
        );
        
        try {
            // 우선순위에 따른 타임아웃 설정
            int timeoutSeconds = getTimeoutByPriority(apis.get(0).getHealthCheckPriority());
            allTasks.get(timeoutSeconds, TimeUnit.SECONDS);
            
            return healthCheckTasks.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toMap(
                    HealthCheckResult::getApiId,
                    result -> result
                ));
                
        } catch (Exception e) {
            log.warn("Some health checks timed out or failed for priority group", e);
            
            // 완료된 헬스체크만 반환
            return healthCheckTasks.stream()
                .filter(CompletableFuture::isDone)
                .map(CompletableFuture::join)
                .collect(Collectors.toMap(
                    HealthCheckResult::getApiId,
                    result -> result
                ));
        }
    }
    
    /**
     * 단일 API 비동기 헬스체크
     */
    @Async
    public CompletableFuture<HealthCheckResult> performSingleApiHealthCheckAsync(ExternalApi api) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 이전 상태 확인하여 조기 복구 감지
                redisHealthStateManager.performEarlyRecoveryCheck(api.getApiId());
                
                // 헬스체크 타입 결정
                HealthCheckResult.HealthCheckType checkType = determineOptimalCheckType(api);
                
                HealthCheckResult result = switch (checkType) {
                    case STATIC -> performEnhancedStaticHealthCheck(api);
                    case DYNAMIC -> performEnhancedDynamicHealthCheck(api);
                    default -> performEnhancedStaticHealthCheck(api);
                };
                
                // 결과 저장 및 상태 업데이트
                saveHealthCheckResult(api, result);
                updateMetricsAndCache(api, result);
                
                return result;
                
            } catch (Exception e) {
                log.error("Unexpected error during health check for API: {}", api.getApiName(), e);
                return createErrorResult(api, e);
            }
        });
    }
    
    /**
     * 향상된 정적 헬스체크
     * /health, /status 엔드포인트 시도 후 원본 URL 시도
     */
    private HealthCheckResult performEnhancedStaticHealthCheck(ExternalApi api) {
        long startTime = System.currentTimeMillis();
        
        // 1. 표준 헬스체크 엔드포인트 시도
        HealthCheckResult healthEndpointResult = tryHealthEndpoint(api, startTime);
        if (healthEndpointResult.isSuccess()) {
            return healthEndpointResult;
        }
        
        // 2. 원본 API URL 시도
        return tryOriginalApiUrl(api, startTime);
    }
    
    /**
     * 향상된 동적 헬스체크
     * 정적 체크 + 응답 데이터 검증 + 성능 분석
     */
    private HealthCheckResult performEnhancedDynamicHealthCheck(ExternalApi api) {
        // 1. 정적 헬스체크 먼저 수행
        HealthCheckResult staticResult = performEnhancedStaticHealthCheck(api);
        
        if (!staticResult.isSuccess()) {
            staticResult.setCheckType(HealthCheckResult.HealthCheckType.DYNAMIC);
            return staticResult;
        }
        
        // 2. 동적 검증 수행
        return enhanceWithDynamicValidation(api, staticResult);
    }
    
    /**
     * 표준 헬스체크 엔드포인트 시도
     */
    private HealthCheckResult tryHealthEndpoint(ExternalApi api, long startTime) {
        List<String> healthEndpoints = Arrays.asList(
            api.getApiUrl() + HEALTH_ENDPOINT_SUFFIX,
            api.getApiUrl() + STATUS_ENDPOINT_SUFFIX
        );
        
        for (String endpoint : healthEndpoints) {
            try {
                HealthCheckResult result = performHttpRequest(api, endpoint, STATIC_TIMEOUT, startTime);
                if (result.isSuccess()) {
                    result.setAdditionalInfo("Health endpoint: " + endpoint);
                    return result;
                }
            } catch (Exception e) {
                log.debug("Health endpoint failed: {} - {}", endpoint, e.getMessage());
            }
        }
        
        return createFailureResult(api, "Health endpoints not available", startTime);
    }
    
    /**
     * 원본 API URL 시도
     */
    private HealthCheckResult tryOriginalApiUrl(ExternalApi api, long startTime) {
        try {
            return performHttpRequest(api, api.getApiUrl(), STATIC_TIMEOUT, startTime);
        } catch (Exception e) {
            return createFailureResult(api, "Original API URL failed: " + e.getMessage(), startTime);
        }
    }
    
    /**
     * HTTP 요청 수행
     */
    private HealthCheckResult performHttpRequest(ExternalApi api, String url, Duration timeout, long startTime) {
        try {
            WebClient webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
            
            String response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(timeout)
                .block();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            return HealthCheckResult.builder()
                .apiId(api.getApiId())
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(determineHealthStatusFromResponse(200, responseTime, response))
                .httpStatusCode(200)
                .responseTimeMs(responseTime)
                .responseSample(truncateResponse(response))
                .checkedAt(LocalDateTime.now())
                .consecutiveFailures(0)
                .isSuccess(true)
                .build();
                
        } catch (WebClientResponseException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return createHttpErrorResult(api, e, responseTime);
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return createRequestErrorResult(api, e, responseTime);
        }
    }
    
    /**
     * 동적 검증으로 결과 향상
     */
    private HealthCheckResult enhanceWithDynamicValidation(ExternalApi api, HealthCheckResult staticResult) {
        try {
            if (!dynamicValidationEnabled) {
                staticResult.setCheckType(HealthCheckResult.HealthCheckType.DYNAMIC);
                return staticResult;
            }
            
            boolean validationPassed = true;
            StringBuilder validationDetails = new StringBuilder();
            
            // 응답 구조 검증
            if (!validateResponseStructure(staticResult.getResponseSample())) {
                validationPassed = false;
                validationDetails.append("Invalid response structure; ");
            }
            
            // 응답 시간 검증
            if (staticResult.getResponseTimeMs() != null && staticResult.getResponseTimeMs() > 3000) {
                staticResult.setStatus(HealthCheckResult.HealthStatus.DEGRADED);
                validationDetails.append("Slow response time (").append(staticResult.getResponseTimeMs()).append("ms); ");
            }
            
            // 콘텐츠 품질 검증
            if (!validateContentQuality(staticResult.getResponseSample())) {
                validationPassed = false;
                validationDetails.append("Poor content quality; ");
            }
            
            if (!validationPassed) {
                staticResult.setStatus(HealthCheckResult.HealthStatus.DEGRADED);
                staticResult.setErrorMessage("Dynamic validation failed: " + validationDetails);
            }
            
            staticResult.setCheckType(HealthCheckResult.HealthCheckType.DYNAMIC);
            staticResult.setAdditionalInfo("Dynamic validation: " + (validationPassed ? "PASSED" : "FAILED"));
            
            return staticResult;
            
        } catch (Exception e) {
            staticResult.setStatus(HealthCheckResult.HealthStatus.DEGRADED);
            staticResult.setErrorMessage("Dynamic validation error: " + e.getMessage());
            staticResult.setCheckType(HealthCheckResult.HealthCheckType.DYNAMIC);
            return staticResult;
        }
    }
    
    /**
     * 최적 헬스체크 타입 결정
     */
    private HealthCheckResult.HealthCheckType determineOptimalCheckType(ExternalApi api) {
        // 높은 우선순위는 동적 헬스체크
        if (api.getHealthCheckPriority() == ExternalApi.HealthCheckPriority.HIGH) {
            return HealthCheckResult.HealthCheckType.DYNAMIC;
        }
        
        // 실시간/스트리밍 API는 동적 헬스체크
        if (api.getApiKeyword() == ExternalApi.ApiKeyword.REAL_TIME || 
            api.getApiKeyword() == ExternalApi.ApiKeyword.STREAMING) {
            return HealthCheckResult.HealthCheckType.DYNAMIC;
        }
        
        // 중요 도메인의 상세정보/검색 API는 동적 헬스체크
        if ((api.getApiDomain() == ExternalApi.ApiDomain.WEATHER || 
             api.getApiDomain() == ExternalApi.ApiDomain.TRAFFIC || 
             api.getApiDomain() == ExternalApi.ApiDomain.DISASTER) &&
            (api.getApiKeyword() == ExternalApi.ApiKeyword.DETAIL || 
             api.getApiKeyword() == ExternalApi.ApiKeyword.SEARCH)) {
            return HealthCheckResult.HealthCheckType.DYNAMIC;
        }
        
        // 나머지는 정적 헬스체크
        return HealthCheckResult.HealthCheckType.STATIC;
    }
    
    /**
     * 응답으로부터 헬스 상태 결정
     */
    private HealthCheckResult.HealthStatus determineHealthStatusFromResponse(int statusCode, long responseTime, String response) {
        if (statusCode < 200 || statusCode >= 300) {
            return HealthCheckResult.HealthStatus.UNHEALTHY;
        }
        
        if (responseTime > 5000) {
            return HealthCheckResult.HealthStatus.DEGRADED;
        }
        
        if (response == null || response.trim().isEmpty()) {
            return HealthCheckResult.HealthStatus.DEGRADED;
        }
        
        return HealthCheckResult.HealthStatus.HEALTHY;
    }
    
    /**
     * 응답 구조 검증
     */
    private boolean validateResponseStructure(String response) {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = response.trim();
        
        // JSON 검증
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return isValidJson(trimmed);
        }
        
        // XML 검증
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            return isValidXml(trimmed);
        }
        
        // 최소 텍스트 길이 검증
        return trimmed.length() >= 10;
    }
    
    /**
     * 콘텐츠 품질 검증
     */
    private boolean validateContentQuality(String response) {
        if (response == null) return false;
        
        // 에러 메시지 패턴 확인
        String lowerResponse = response.toLowerCase();
        List<String> errorPatterns = Arrays.asList(
            "error", "exception", "failed", "timeout", "unavailable", "maintenance"
        );
        
        for (String pattern : errorPatterns) {
            if (lowerResponse.contains(pattern)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * JSON 유효성 검사
     */
    private boolean isValidJson(String json) {
        try {
            // 간단한 JSON 구조 검증
            return json.contains("\"") && (json.contains(":") || json.contains("="));
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * XML 유효성 검사
     */
    private boolean isValidXml(String xml) {
        try {
            // 간단한 XML 태그 검증
            return xml.contains("<") && xml.contains(">") && !xml.equals("<>");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 응답 텍스트 자르기
     */
    private String truncateResponse(String response) {
        if (response == null) return null;
        return response.length() > 500 ? response.substring(0, 500) + "..." : response;
    }
    
    /**
     * 우선순위별 타임아웃 결정
     */
    private int getTimeoutByPriority(ExternalApi.HealthCheckPriority priority) {
        return switch (priority) {
            case HIGH -> 15;
            case MEDIUM -> 30;
            case LOW -> 60;
        };
    }
    
    /**
     * 헬스체크 결과 저장
     */
    private void saveHealthCheckResult(ExternalApi api, HealthCheckResult result) {
        try {
            healthCheckResultRepository.save(result);
        } catch (Exception e) {
            log.error("Failed to save health check result for API: {}", api.getApiId(), e);
        }
    }
    
    /**
     * 메트릭 및 캐시 업데이트
     */
    private void updateMetricsAndCache(ExternalApi api, HealthCheckResult result) {
        try {
            // Prometheus 메트릭 업데이트
            prometheusMetricsService.recordHealthCheck(
                api.getApiName(),
                api.getApiIssuer(),
                result.isSuccess(),
                result.getResponseTimeMs() != null ? result.getResponseTimeMs() : 0
            );
            
            // Redis 상태 업데이트
            redisHealthStateManager.updateApiHealthState(api.getApiId(), result);
            
            // API 메타데이터 캐싱
            apiStatusManager.cacheApiMetadata(api.getApiId(), api);
            
        } catch (Exception e) {
            log.error("Failed to update metrics and cache for API: {}", api.getApiId(), e);
        }
    }
    
    /**
     * 에러 결과 생성
     */
    private HealthCheckResult createErrorResult(ExternalApi api, Exception e) {
        return HealthCheckResult.builder()
            .apiId(api.getApiId())
            .checkType(HealthCheckResult.HealthCheckType.STATIC)
            .status(HealthCheckResult.HealthStatus.UNKNOWN)
            .errorMessage("Unexpected error: " + e.getMessage())
            .checkedAt(LocalDateTime.now())
            .isSuccess(false)
            .build();
    }
    
    /**
     * HTTP 에러 결과 생성
     */
    private HealthCheckResult createHttpErrorResult(ExternalApi api, WebClientResponseException e, long responseTime) {
        return HealthCheckResult.builder()
            .apiId(api.getApiId())
            .checkType(HealthCheckResult.HealthCheckType.STATIC)
            .status(determineHealthStatusFromHttpCode(HttpStatus.valueOf(e.getStatusCode().value())))
            .httpStatusCode(e.getStatusCode().value())
            .responseTimeMs(responseTime)
            .errorMessage("HTTP Error: " + e.getStatusCode())
            .errorDetails(e.getResponseBodyAsString())
            .checkedAt(LocalDateTime.now())
            .isSuccess(false)
            .build();
    }
    
    /**
     * 요청 에러 결과 생성
     */
    private HealthCheckResult createRequestErrorResult(ExternalApi api, Exception e, long responseTime) {
        boolean isTimeout = e instanceof WebClientRequestException && responseTime >= STATIC_TIMEOUT.toMillis();
        
        return HealthCheckResult.builder()
            .apiId(api.getApiId())
            .checkType(HealthCheckResult.HealthCheckType.STATIC)
            .status(isTimeout ? HealthCheckResult.HealthStatus.TIMEOUT : HealthCheckResult.HealthStatus.UNHEALTHY)
            .responseTimeMs(responseTime)
            .errorMessage("Request failed: " + e.getMessage())
            .checkedAt(LocalDateTime.now())
            .isTimeout(isTimeout)
            .isSuccess(false)
            .build();
    }
    
    /**
     * 실패 결과 생성
     */
    private HealthCheckResult createFailureResult(ExternalApi api, String message, long startTime) {
        return HealthCheckResult.builder()
            .apiId(api.getApiId())
            .checkType(HealthCheckResult.HealthCheckType.STATIC)
            .status(HealthCheckResult.HealthStatus.UNHEALTHY)
            .responseTimeMs(System.currentTimeMillis() - startTime)
            .errorMessage(message)
            .checkedAt(LocalDateTime.now())
            .isSuccess(false)
            .build();
    }
    
    /**
     * HTTP 상태 코드로부터 헬스 상태 결정
     */
    private HealthCheckResult.HealthStatus determineHealthStatusFromHttpCode(HttpStatus statusCode) {
        if (statusCode.is2xxSuccessful()) {
            return HealthCheckResult.HealthStatus.HEALTHY;
        } else if (statusCode.is4xxClientError()) {
            return HealthCheckResult.HealthStatus.UNHEALTHY;
        } else if (statusCode.is5xxServerError()) {
            return HealthCheckResult.HealthStatus.UNHEALTHY;
        } else {
            return HealthCheckResult.HealthStatus.UNKNOWN;
        }
    }
    
    /**
     * 정기적 지능형 헬스체크 스케줄러
     */
    @Scheduled(fixedDelayString = "${healthcheck.intelligent.interval:120000}")
    public void scheduledIntelligentHealthCheck() {
        log.info("Starting scheduled intelligent health check");
        
        try {
            performIntelligentHealthCheck().thenAccept(results -> {
                log.info("Intelligent health check completed for {} APIs", results.size());
                
                // 통계 로깅
                long healthyCount = results.values().stream()
                    .mapToLong(result -> result.isSuccess() ? 1 : 0)
                    .sum();
                
                log.info("Health check summary - Healthy: {}/{} ({:.1f}%)", 
                        healthyCount, results.size(), 
                        results.size() > 0 ? (double) healthyCount / results.size() * 100 : 100.0);
                
                // Redis 상태 정리
                redisHealthStateManager.cleanupExpiredStates();
            });
            
        } catch (Exception e) {
            log.error("Scheduled intelligent health check failed", e);
        }
    }
}