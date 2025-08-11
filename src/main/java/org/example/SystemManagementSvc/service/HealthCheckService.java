package org.example.SystemManagementSvc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.SystemManagementSvc.domain.ExternalApi;
import org.example.SystemManagementSvc.domain.HealthCheckResult;
import org.example.SystemManagementSvc.repository.ExternalApiRepository;
import org.example.SystemManagementSvc.repository.HealthCheckResultRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 외부 API 헬스체크 서비스
 * - 정기적인 외부 API 상태 모니터링
 * - 병렬 헬스체크 실행
 * - Redis 캐싱을 통한 비정상 API 관리
 * - 동적/정적 헬스체크 지원
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCheckService {

    private final ExternalApiRepository externalApiRepository;
    private final HealthCheckResultRepository healthCheckResultRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PrometheusMetricsService prometheusMetricsService;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${healthcheck.cache.unhealthy-ttl:180}")
    private long unhealthyTtlSeconds;
    
    private static final String UNHEALTHY_API_CACHE_PREFIX = "unhealthy:";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RESPONSE_SAMPLE_LENGTH = 500;

    /**
     * 모든 활성화된 API에 대해 병렬 헬스체크 수행
     */
    public CompletableFuture<Map<String, HealthCheckResult>> performHealthCheckForAllApis() {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting health check for all active APIs");
            
            List<ExternalApi> activeApis = externalApiRepository.findByApiEffectivenessTrue();
            log.info("Found {} active APIs to check", activeApis.size());
            
            // 병렬로 헬스체크 실행
            List<CompletableFuture<HealthCheckResult>> healthCheckTasks = activeApis.stream()
                .map(api -> performSingleHealthCheckAsync(api))
                .collect(Collectors.toList());
            
            // 모든 헬스체크 완료 대기
            CompletableFuture<Void> allHealthChecks = CompletableFuture.allOf(
                healthCheckTasks.toArray(new CompletableFuture[0])
            );
            
            try {
                allHealthChecks.get(60, TimeUnit.SECONDS); // 최대 60초 대기
                
                Map<String, HealthCheckResult> results = healthCheckTasks.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toMap(
                        HealthCheckResult::getApiId,
                        result -> result
                    ));
                
                log.info("Health check completed for {} APIs", results.size());
                updateApiEffectiveness(results);
                
                return results;
                
            } catch (Exception e) {
                log.error("Failed to complete all health checks within timeout", e);
                
                // 완료된 헬스체크만 반환
                return healthCheckTasks.stream()
                    .filter(CompletableFuture::isDone)
                    .map(CompletableFuture::join)
                    .collect(Collectors.toMap(
                        HealthCheckResult::getApiId,
                        result -> result
                    ));
            }
        });
    }

    /**
     * 단일 API 헬스체크를 비동기로 수행
     */
    @Async
    public CompletableFuture<HealthCheckResult> performSingleHealthCheckAsync(ExternalApi api) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 우선순위에 따라 헬스체크 타입 결정
                HealthCheckResult.HealthCheckType checkType = determineCheckType(api);
                
                HealthCheckResult result = switch (checkType) {
                    case STATIC -> performStaticHealthCheck(api);
                    case DYNAMIC -> performDynamicHealthCheck(api);
                    default -> performStaticHealthCheck(api);
                };
                
                // 결과 저장
                healthCheckResultRepository.save(result);
                
                // Prometheus 메트릭 업데이트
                prometheusMetricsService.recordHealthCheck(
                    api.getApiName(),
                    api.getApiIssuer(),
                    result.isSuccess(),
                    result.getResponseTimeMs() != null ? result.getResponseTimeMs() : 0
                );
                
                // Redis 캐시 업데이트
                updateRedisCache(api.getApiId(), result);
                
                log.debug("Health check completed for API: {} - Status: {}", 
                         api.getApiName(), result.getStatus());
                
                return result;
                
            } catch (Exception e) {
                log.error("Unexpected error during health check for API: {}", api.getApiName(), e);
                
                return HealthCheckResult.builder()
                    .apiId(api.getApiId())
                    .checkType(HealthCheckResult.HealthCheckType.STATIC)
                    .status(HealthCheckResult.HealthStatus.UNKNOWN)
                    .errorMessage("Unexpected error: " + e.getMessage())
                    .checkedAt(LocalDateTime.now())
                    .build();
            }
        });
    }

    /**
     * 정적 헬스체크 수행 (단순 HTTP 상태 코드 확인)
     */
    private HealthCheckResult performStaticHealthCheck(ExternalApi api) {
        long startTime = System.currentTimeMillis();
        
        try {
            WebClient webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1MB
                .build();
            
            String response = webClient.get()
                .uri(api.getApiUrl())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(REQUEST_TIMEOUT)
                .block();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            return HealthCheckResult.builder()
                .apiId(api.getApiId())
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(determineHealthStatus(200, responseTime))
                .httpStatusCode(200)
                .responseTimeMs(responseTime)
                .responseSample(truncateResponse(response))
                .checkedAt(LocalDateTime.now())
                .consecutiveFailures(0)
                .build();
                
        } catch (WebClientResponseException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            
            return HealthCheckResult.builder()
                .apiId(api.getApiId())
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(determineHealthStatus(e.getStatusCode().value(), responseTime))
                .httpStatusCode(e.getStatusCode().value())
                .responseTimeMs(responseTime)
                .errorMessage("HTTP Error: " + e.getStatusCode())
                .errorDetails(e.getResponseBodyAsString())
                .checkedAt(LocalDateTime.now())
                .consecutiveFailures(getConsecutiveFailures(api.getApiId()) + 1)
                .build();
                
        } catch (WebClientRequestException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            
            return HealthCheckResult.builder()
                .apiId(api.getApiId())
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(responseTime >= REQUEST_TIMEOUT.toMillis() ? 
                       HealthCheckResult.HealthStatus.TIMEOUT : 
                       HealthCheckResult.HealthStatus.UNHEALTHY)
                .responseTimeMs(responseTime)
                .errorMessage("Request failed: " + e.getMessage())
                .errorDetails(e.getCause() != null ? e.getCause().getMessage() : null)
                .checkedAt(LocalDateTime.now())
                .consecutiveFailures(getConsecutiveFailures(api.getApiId()) + 1)
                .isTimeout(responseTime >= REQUEST_TIMEOUT.toMillis())
                .build();
                
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            
            return HealthCheckResult.builder()
                .apiId(api.getApiId())
                .checkType(HealthCheckResult.HealthCheckType.STATIC)
                .status(HealthCheckResult.HealthStatus.UNKNOWN)
                .responseTimeMs(responseTime)
                .errorMessage("Unknown error: " + e.getMessage())
                .checkedAt(LocalDateTime.now())
                .consecutiveFailures(getConsecutiveFailures(api.getApiId()) + 1)
                .build();
        }
    }

    /**
     * 동적 헬스체크 수행 (실제 API 호출 및 응답 검증)
     */
    private HealthCheckResult performDynamicHealthCheck(ExternalApi api) {
        // 정적 헬스체크를 먼저 수행
        HealthCheckResult staticResult = performStaticHealthCheck(api);
        
        // 정적 헬스체크가 실패하면 동적 헬스체크 스킵
        if (!staticResult.isSuccess()) {
            staticResult.setCheckType(HealthCheckResult.HealthCheckType.DYNAMIC);
            return staticResult;
        }
        
        // 추가적인 동적 검증 로직 (응답 스키마 검증, 데이터 품질 확인 등)
        try {
            // 응답 데이터의 기본적인 구조 검증
            boolean isValidResponse = validateResponseStructure(staticResult.getResponseSample());
            
            if (!isValidResponse) {
                staticResult.setStatus(HealthCheckResult.HealthStatus.DEGRADED);
                staticResult.setErrorMessage("Response structure validation failed");
            }
            
            staticResult.setCheckType(HealthCheckResult.HealthCheckType.DYNAMIC);
            return staticResult;
            
        } catch (Exception e) {
            staticResult.setStatus(HealthCheckResult.HealthStatus.DEGRADED);
            staticResult.setErrorMessage("Dynamic validation error: " + e.getMessage());
            staticResult.setCheckType(HealthCheckResult.HealthCheckType.DYNAMIC);
            return staticResult;
        }
    }

    /**
     * 헬스체크 타입 결정
     */
    private HealthCheckResult.HealthCheckType determineCheckType(ExternalApi api) {
        // 높은 우선순위 API는 동적 헬스체크
        return api.getHealthCheckPriority() == ExternalApi.HealthCheckPriority.HIGH ?
               HealthCheckResult.HealthCheckType.DYNAMIC :
               HealthCheckResult.HealthCheckType.STATIC;
    }

    /**
     * HTTP 상태 코드와 응답 시간을 바탕으로 헬스 상태 결정
     */
    private HealthCheckResult.HealthStatus determineHealthStatus(int statusCode, long responseTime) {
        // HTTP 상태 코드 체크
        if (statusCode >= 200 && statusCode < 300) {
            // 응답 시간에 따른 상태 결정
            if (responseTime > 5000) {
                return HealthCheckResult.HealthStatus.DEGRADED; // 5초 이상은 성능 저하
            } else {
                return HealthCheckResult.HealthStatus.HEALTHY;
            }
        } else if (statusCode >= 400 && statusCode < 500) {
            return HealthCheckResult.HealthStatus.UNHEALTHY; // 클라이언트 에러
        } else if (statusCode >= 500) {
            return HealthCheckResult.HealthStatus.UNHEALTHY; // 서버 에러
        } else {
            return HealthCheckResult.HealthStatus.UNKNOWN;
        }
    }

    /**
     * 응답 구조 검증 (간단한 JSON/XML 형식 체크)
     */
    private boolean validateResponseStructure(String response) {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = response.trim();
        
        // JSON 형식 체크
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return true;
        }
        
        // XML 형식 체크
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            return true;
        }
        
        // 기타 텍스트 응답 (최소 길이 체크)
        return trimmed.length() > 10;
    }

    /**
     * 응답 텍스트 자르기
     */
    private String truncateResponse(String response) {
        if (response == null) {
            return null;
        }
        
        return response.length() > MAX_RESPONSE_SAMPLE_LENGTH ?
               response.substring(0, MAX_RESPONSE_SAMPLE_LENGTH) + "..." :
               response;
    }

    /**
     * 연속 실패 횟수 조회
     */
    private int getConsecutiveFailures(String apiId) {
        Integer failures = healthCheckResultRepository.findMaxConsecutiveFailures(apiId);
        return failures != null ? failures : 0;
    }

    /**
     * Redis 캐시 업데이트 (비정상 API만 캐시)
     */
    private void updateRedisCache(String apiId, HealthCheckResult result) {
        String cacheKey = UNHEALTHY_API_CACHE_PREFIX + apiId;
        
        try {
            if (result.isFailure()) {
                // 비정상 API를 Redis에 TTL과 함께 저장
                Map<String, Object> cacheData = Map.of(
                    "status", result.getStatus().name(),
                    "errorMessage", result.getErrorMessage() != null ? result.getErrorMessage() : "",
                    "responseTime", result.getResponseTimeMs() != null ? result.getResponseTimeMs() : 0,
                    "lastCheck", result.getCheckedAt().toString(),
                    "consecutiveFailures", result.getConsecutiveFailures() != null ? result.getConsecutiveFailures() : 0
                );
                
                redisTemplate.opsForValue().set(cacheKey, cacheData, unhealthyTtlSeconds, TimeUnit.SECONDS);
                log.debug("Cached unhealthy API: {} with TTL: {}s", apiId, unhealthyTtlSeconds);
                
            } else {
                // 정상 API는 캐시에서 제거
                Boolean deleted = redisTemplate.delete(cacheKey);
                if (Boolean.TRUE.equals(deleted)) {
                    log.debug("Removed healthy API from cache: {}", apiId);
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to update Redis cache for API: {}", apiId, e);
        }
    }

    /**
     * API 유효성 상태 업데이트
     */
    private void updateApiEffectiveness(Map<String, HealthCheckResult> results) {
        results.forEach((apiId, result) -> {
            try {
                externalApiRepository.findById(apiId).ifPresent(api -> {
                    boolean wasEffective = api.getApiEffectiveness();
                    boolean isNowEffective = result.isSuccess();
                    
                    if (wasEffective != isNowEffective) {
                        api.setApiEffectiveness(isNowEffective);
                        externalApiRepository.save(api);
                        
                        log.info("API effectiveness updated - {}: {} -> {}", 
                                api.getApiName(), wasEffective, isNowEffective);
                    }
                });
                
            } catch (Exception e) {
                log.error("Failed to update API effectiveness for: {}", apiId, e);
            }
        });
    }

    /**
     * 정기적 헬스체크 스케줄러
     */
    @Scheduled(fixedDelayString = "${healthcheck.scheduler.fixed-delay:120000}", 
               initialDelayString = "${healthcheck.scheduler.initial-delay:30000}")
    public void scheduledHealthCheck() {
        if (!"${healthcheck.scheduler.enabled:true}".equals("false")) {
            log.info("Starting scheduled health check");
            
            try {
                performHealthCheckForAllApis().thenAccept(results -> {
                    log.info("Scheduled health check completed. Checked {} APIs", results.size());
                    
                    long healthyCount = results.values().stream()
                        .mapToLong(result -> result.isSuccess() ? 1 : 0)
                        .sum();
                    
                    log.info("Health check summary - Healthy: {}/{}", healthyCount, results.size());
                });
                
            } catch (Exception e) {
                log.error("Scheduled health check failed", e);
            }
        }
    }

    /**
     * Redis에서 비정상 API 목록 조회
     */
    public List<String> getUnhealthyApisFromCache() {
        try {
            return redisTemplate.keys(UNHEALTHY_API_CACHE_PREFIX + "*")
                .stream()
                .map(key -> key.replace(UNHEALTHY_API_CACHE_PREFIX, ""))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to get unhealthy APIs from cache", e);
            return List.of();
        }
    }

    /**
     * 특정 API가 현재 비정상 상태인지 확인
     */
    public boolean isApiCurrentlyUnhealthy(String apiId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(UNHEALTHY_API_CACHE_PREFIX + apiId));
        } catch (Exception e) {
            log.warn("Failed to check API health status in cache for: {}", apiId, e);
            return false;
        }
    }
}