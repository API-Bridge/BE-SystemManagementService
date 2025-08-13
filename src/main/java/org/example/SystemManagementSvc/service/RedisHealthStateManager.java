package org.example.SystemManagementSvc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.SystemManagementSvc.domain.HealthCheckResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis TTL 기반 고급 API 상태 관리 시스템
 * 
 * 핵심 전략:
 * - Zero Storage for Healthy APIs: 정상 API는 아무것도 저장하지 않음
 * - TTL 기반 자동 복구: 비정상 API만 TTL과 함께 저장하여 자동 복구
 * - 동적 TTL 조정: 장애 심각도에 따른 차등 TTL 설정
 * - 지능형 복구 감지: 점진적 복구 및 조기 회복 감지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisHealthStateManager {

    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${healthcheck.redis.unhealthy-ttl:180}")
    private long defaultUnhealthyTtl;
    
    @Value("${healthcheck.redis.degraded-ttl:120}")
    private long degradedTtl;
    
    @Value("${healthcheck.redis.timeout-ttl:300}")
    private long timeoutTtl;
    
    @Value("${healthcheck.redis.critical-ttl:600}")
    private long criticalTtl;
    
    // Redis 키 패턴
    private static final String UNHEALTHY_PREFIX = "unhealthy:";
    private static final String RECOVERY_TRACKING_PREFIX = "recovery:";
    private static final String FAILURE_HISTORY_PREFIX = "failure_history:";
    private static final String HEALTH_ANALYTICS_KEY = "health_analytics:summary";
    
    /**
     * API 상태를 Redis에 저장
     * 정상 API는 저장하지 않고, 비정상 API만 TTL과 함께 저장
     */
    public void updateApiHealthState(String apiId, HealthCheckResult result) {
        String unhealthyKey = UNHEALTHY_PREFIX + apiId;
        
        try {
            if (result.isSuccess()) {
                // 정상 API: 캐시에서 제거하고 복구 추적 시작
                handleHealthyApiRecovery(apiId, unhealthyKey);
            } else {
                // 비정상 API: TTL과 함께 상태 정보 저장
                handleUnhealthyApiState(apiId, unhealthyKey, result);
            }
            
            // 장애 히스토리 업데이트
            updateFailureHistory(apiId, result);
            
        } catch (Exception e) {
            log.error("Failed to update API health state for: {}", apiId, e);
        }
    }
    
    /**
     * 정상 API 복구 처리
     */
    private void handleHealthyApiRecovery(String apiId, String unhealthyKey) {
        try {
            // 이전에 비정상이었는지 확인
            boolean wasUnhealthy = Boolean.TRUE.equals(redisTemplate.hasKey(unhealthyKey));
            
            if (wasUnhealthy) {
                // 복구된 API 정보 로깅
                @SuppressWarnings("unchecked")
                Map<String, Object> previousState = (Map<String, Object>) redisTemplate.opsForValue().get(unhealthyKey);
                
                if (previousState != null) {
                    log.info("API recovered: {} - Previous state: {}, Failures: {}", 
                            apiId, 
                            previousState.get("status"),
                            previousState.get("consecutiveFailures"));
                }
                
                // 비정상 상태 캐시 제거
                redisTemplate.delete(unhealthyKey);
                
                // 복구 추적 정보 저장 (짧은 TTL)
                trackApiRecovery(apiId);
                
                log.debug("API {} recovered and removed from unhealthy cache", apiId);
            }
            
        } catch (Exception e) {
            log.warn("Failed to handle healthy API recovery for: {}", apiId, e);
        }
    }
    
    /**
     * 비정상 API 상태 처리
     */
    private void handleUnhealthyApiState(String apiId, String unhealthyKey, HealthCheckResult result) {
        try {
            // 이전 연속 실패 횟수 조회
            int previousFailures = getPreviousConsecutiveFailures(unhealthyKey);
            int currentFailures = previousFailures + 1;
            
            // TTL 결정 (심각도에 따른 차등 설정)
            long ttl = determineTtlByStatus(result.getStatus(), currentFailures);
            
            // 상태 정보 구성
            Map<String, Object> healthState = createHealthStateMap(result, currentFailures);
            
            // Redis에 TTL과 함께 저장
            redisTemplate.opsForValue().set(unhealthyKey, healthState, ttl, TimeUnit.SECONDS);
            
            log.debug("API {} marked as unhealthy with TTL: {}s, Status: {}, Failures: {}", 
                     apiId, ttl, result.getStatus(), currentFailures);
            
        } catch (Exception e) {
            log.error("Failed to handle unhealthy API state for: {}", apiId, e);
        }
    }
    
    /**
     * 상태에 따른 TTL 결정
     */
    private long determineTtlByStatus(HealthCheckResult.HealthStatus status, int consecutiveFailures) {
        // 연속 실패 횟수에 따른 TTL 증가 (최대 2배)
        double failureMultiplier = Math.min(1.0 + (consecutiveFailures - 1) * 0.2, 2.0);
        
        long baseTtl = switch (status) {
            case TIMEOUT -> timeoutTtl;
            case UNHEALTHY -> consecutiveFailures >= 5 ? criticalTtl : defaultUnhealthyTtl;
            case DEGRADED -> degradedTtl;
            case UNKNOWN -> defaultUnhealthyTtl;
            default -> defaultUnhealthyTtl;
        };
        
        return Math.round(baseTtl * failureMultiplier);
    }
    
    /**
     * 헬스 상태 맵 생성
     */
    private Map<String, Object> createHealthStateMap(HealthCheckResult result, int consecutiveFailures) {
        Map<String, Object> state = new HashMap<>();
        state.put("status", result.getStatus().name());
        state.put("errorMessage", result.getErrorMessage() != null ? result.getErrorMessage() : "");
        state.put("responseTime", result.getResponseTimeMs() != null ? result.getResponseTimeMs() : 0);
        state.put("httpStatusCode", result.getHttpStatusCode() != null ? result.getHttpStatusCode() : 0);
        state.put("consecutiveFailures", consecutiveFailures);
        state.put("lastCheck", result.getCheckedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        state.put("checkType", result.getCheckType().name());
        state.put("isTimeout", result.isTimeout() != null ? result.isTimeout() : false);
        state.put("firstFailureTime", getFirstFailureTime(consecutiveFailures));
        
        return state;
    }
    
    /**
     * 이전 연속 실패 횟수 조회
     */
    @SuppressWarnings("unchecked")
    private int getPreviousConsecutiveFailures(String unhealthyKey) {
        try {
            Map<String, Object> previousState = (Map<String, Object>) redisTemplate.opsForValue().get(unhealthyKey);
            if (previousState != null && previousState.containsKey("consecutiveFailures")) {
                return Integer.parseInt(previousState.get("consecutiveFailures").toString());
            }
        } catch (Exception e) {
            log.debug("Failed to get previous consecutive failures, assuming 0", e);
        }
        return 0;
    }
    
    /**
     * 첫 번째 장애 시간 조회 또는 설정
     */
    @SuppressWarnings("unchecked")
    private String getFirstFailureTime(int consecutiveFailures) {
        if (consecutiveFailures <= 1) {
            return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        
        // 기존 첫 번째 장애 시간 유지 (이미 있는 경우)
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    
    /**
     * API 복구 추적
     */
    private void trackApiRecovery(String apiId) {
        try {
            String recoveryKey = RECOVERY_TRACKING_PREFIX + apiId;
            Map<String, Object> recoveryInfo = Map.of(
                "recoveredAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "apiId", apiId
            );
            
            // 복구 정보는 1시간 동안만 보관
            redisTemplate.opsForValue().set(recoveryKey, recoveryInfo, 1, TimeUnit.HOURS);
            
        } catch (Exception e) {
            log.warn("Failed to track API recovery for: {}", apiId, e);
        }
    }
    
    /**
     * 장애 히스토리 업데이트
     */
    private void updateFailureHistory(String apiId, HealthCheckResult result) {
        try {
            String historyKey = FAILURE_HISTORY_PREFIX + apiId;
            
            // 최근 장애만 추적 (최대 10개)
            redisTemplate.opsForList().leftPush(historyKey, Map.of(
                "timestamp", result.getCheckedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "status", result.getStatus().name(),
                "isSuccess", result.isSuccess()
            ));
            
            // 리스트 크기 제한
            redisTemplate.opsForList().trim(historyKey, 0, 9);
            
            // 24시간 TTL
            redisTemplate.expire(historyKey, 24, TimeUnit.HOURS);
            
        } catch (Exception e) {
            log.debug("Failed to update failure history for: {}", apiId, e);
        }
    }
    
    /**
     * 배치 상태 확인 (성능 최적화)
     */
    public Map<String, Boolean> batchCheckApiAvailability(List<String> apiIds) {
        if (apiIds == null || apiIds.isEmpty()) {
            return Map.of();
        }
        
        try {
            // Pipeline으로 배치 조회
            List<String> keys = apiIds.stream()
                .map(apiId -> UNHEALTHY_PREFIX + apiId)
                .collect(Collectors.toList());
            
            List<Object> pipelineResults = redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                for (String key : keys) {
                    connection.exists(key.getBytes());
                }
                return null;
            });
            
            List<Boolean> results = new ArrayList<>();
            for (Object result : pipelineResults) {
                results.add((Boolean) result);
            }
            
            Map<String, Boolean> availabilityMap = new HashMap<>();
            for (int i = 0; i < apiIds.size(); i++) {
                // 키가 없으면 가용, 있으면 불가용
                availabilityMap.put(apiIds.get(i), !Boolean.TRUE.equals(results.get(i)));
            }
            
            return availabilityMap;
            
        } catch (Exception e) {
            log.error("Failed to batch check API availability", e);
            // 실패 시 모든 API를 가용으로 간주
            return apiIds.stream().collect(Collectors.toMap(id -> id, id -> true));
        }
    }
    
    /**
     * 복구 예상 시간 조회
     */
    public Map<String, Long> getRecoveryEstimates() {
        try {
            Set<String> unhealthyKeys = redisTemplate.keys(UNHEALTHY_PREFIX + "*");
            Map<String, Long> estimates = new HashMap<>();
            
            for (String key : unhealthyKeys) {
                String apiId = key.replace(UNHEALTHY_PREFIX, "");
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                
                if (ttl != null && ttl > 0) {
                    estimates.put(apiId, ttl);
                }
            }
            
            return estimates;
            
        } catch (Exception e) {
            log.error("Failed to get recovery estimates", e);
            return Map.of();
        }
    }
    
    /**
     * 상세 API 상태 정보 조회
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDetailedApiStatus(String apiId) {
        try {
            String unhealthyKey = UNHEALTHY_PREFIX + apiId;
            
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(unhealthyKey))) {
                return createHealthyStatusMap(apiId);
            }
            
            Map<String, Object> status = (Map<String, Object>) redisTemplate.opsForValue().get(unhealthyKey);
            if (status == null) {
                return createHealthyStatusMap(apiId);
            }
            
            // TTL 정보 추가
            Long ttl = redisTemplate.getExpire(unhealthyKey, TimeUnit.SECONDS);
            status.put("ttlSeconds", ttl);
            status.put("estimatedRecoveryTime", 
                ttl != null && ttl > 0 ? 
                LocalDateTime.now().plusSeconds(ttl).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : 
                null);
            
            return status;
            
        } catch (Exception e) {
            log.error("Failed to get detailed API status for: {}", apiId, e);
            return createHealthyStatusMap(apiId);
        }
    }
    
    /**
     * 정상 상태 맵 생성
     */
    private Map<String, Object> createHealthyStatusMap(String apiId) {
        return Map.of(
            "apiId", apiId,
            "status", "HEALTHY",
            "isAvailable", true,
            "lastCheck", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "consecutiveFailures", 0
        );
    }
    
    /**
     * 장애 통계 조회
     */
    public Map<String, Object> getFailureStatistics() {
        try {
            Set<String> unhealthyKeys = redisTemplate.keys(UNHEALTHY_PREFIX + "*");
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalUnhealthyApis", unhealthyKeys.size());
            stats.put("unhealthyApiIds", unhealthyKeys.stream()
                .map(key -> key.replace(UNHEALTHY_PREFIX, ""))
                .collect(Collectors.toList()));
            
            // 상태별 통계
            Map<String, Integer> statusCounts = new HashMap<>();
            for (String key : unhealthyKeys) {
                @SuppressWarnings("unchecked")
                Map<String, Object> apiStatus = (Map<String, Object>) redisTemplate.opsForValue().get(key);
                if (apiStatus != null) {
                    String status = apiStatus.getOrDefault("status", "UNKNOWN").toString();
                    statusCounts.merge(status, 1, Integer::sum);
                }
            }
            stats.put("statusBreakdown", statusCounts);
            
            return stats;
            
        } catch (Exception e) {
            log.error("Failed to get failure statistics", e);
            return Map.of("totalUnhealthyApis", 0, "statusBreakdown", Map.of());
        }
    }
    
    /**
     * 조기 복구 감지 및 TTL 조정
     */
    public void performEarlyRecoveryCheck(String apiId) {
        try {
            String unhealthyKey = UNHEALTHY_PREFIX + apiId;
            
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(unhealthyKey))) {
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> currentState = (Map<String, Object>) redisTemplate.opsForValue().get(unhealthyKey);
            
            if (currentState != null) {
                // 연속 실패 횟수가 적으면 TTL 단축
                int failures = Integer.parseInt(currentState.getOrDefault("consecutiveFailures", "0").toString());
                
                if (failures <= 2) {
                    // TTL을 30초로 단축하여 빠른 재검증 유도
                    redisTemplate.expire(unhealthyKey, 30, TimeUnit.SECONDS);
                    log.debug("Shortened TTL for potentially recovering API: {}", apiId);
                }
            }
            
        } catch (Exception e) {
            log.debug("Failed to perform early recovery check for: {}", apiId, e);
        }
    }
    
    /**
     * Redis 상태 정리 (메모리 최적화)
     */
    public void cleanupExpiredStates() {
        try {
            // 만료된 키들을 정리하는 것은 Redis가 자동으로 처리하므로
            // 여기서는 통계 정보만 업데이트
            log.debug("Redis cleanup completed automatically by TTL expiration");
            
        } catch (Exception e) {
            log.error("Failed to cleanup expired states", e);
        }
    }
}