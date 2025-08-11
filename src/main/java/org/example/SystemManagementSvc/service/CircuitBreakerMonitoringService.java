package org.example.SystemManagementSvc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.SystemManagementSvc.dto.analytics.ApiCallStatistics;
import org.example.SystemManagementSvc.event.model.CircuitBreakerEvent;
import org.example.SystemManagementSvc.event.publisher.EventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 서킷브레이커 모니터링 서비스
 * - API 호출량 임계치 모니터링
 * - 비정상적 호출 패턴 감지
 * - 서킷브레이커 상태 관리
 * - 관련 이벤트 발행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreakerMonitoringService {

    private final ApiCallAnalyticsService apiCallAnalyticsService;
    private final EventPublisher eventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AlertNotificationService alertNotificationService;
    
    // 임계치 설정
    @Value("${circuit-breaker.call-rate-threshold:1000}")
    private long callRateThreshold;
    
    @Value("${circuit-breaker.failure-rate-threshold:50.0}")
    private double failureRateThreshold;
    
    @Value("${circuit-breaker.consecutive-failures-threshold:5}")
    private int consecutiveFailuresThreshold;
    
    @Value("${circuit-breaker.response-time-threshold:5000}")
    private long responseTimeThreshold;
    
    @Value("${circuit-breaker.monitoring-window-minutes:5}")
    private int monitoringWindowMinutes;
    
    // Redis 키 prefixes
    private static final String CB_STATE_PREFIX = "circuit-breaker:state:";
    private static final String CB_METRICS_PREFIX = "circuit-breaker:metrics:";
    private static final String CB_HISTORY_PREFIX = "circuit-breaker:history:";
    
    // 로컬 상태 캐시 (성능 최적화용)
    private final Map<String, CircuitBreakerEvent.CircuitBreakerState> localStateCache = new ConcurrentHashMap<>();
    private final Map<String, LocalMetrics> localMetricsCache = new ConcurrentHashMap<>();

    /**
     * 정기적인 서킷브레이커 모니터링
     */
    @Scheduled(fixedRate = 60000) // 1분마다 실행
    public void monitorCircuitBreakers() {
        log.debug("Starting circuit breaker monitoring");
        
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusMinutes(monitoringWindowMinutes);
            
            // 최근 API 호출 통계 조회
            List<ApiCallStatistics> recentStats = apiCallAnalyticsService.getApiCallRanking(startTime, endTime, 50);
            
            // 각 API별로 서킷브레이커 상태 체크
            for (ApiCallStatistics stats : recentStats) {
                checkAndUpdateCircuitBreakerState(stats, startTime, endTime);
            }
            
            // 로컬 캐시 정리 (메모리 사용량 최적화)
            cleanupLocalCache();
            
            log.debug("Circuit breaker monitoring completed for {} APIs", recentStats.size());
            
        } catch (Exception e) {
            log.error("Failed to monitor circuit breakers", e);
        }
    }

    /**
     * 개별 API의 서킷브레이커 상태 체크 및 업데이트
     */
    private void checkAndUpdateCircuitBreakerState(ApiCallStatistics stats, LocalDateTime startTime, LocalDateTime endTime) {
        String apiId = stats.getApiName(); // API 이름을 ID로 사용 (실제로는 별도 매핑 필요)
        
        try {
            // 현재 상태 조회
            CircuitBreakerEvent.CircuitBreakerState currentState = getCurrentState(apiId);
            
            // 새로운 상태 계산
            CircuitBreakerEvent.CircuitBreakerState newState = calculateNewState(stats, currentState);
            
            // 상태 변경이 필요한 경우
            if (newState != currentState) {
                CircuitBreakerEvent.StateChangeTrigger trigger = determineTrigger(stats);
                String reason = generateStateChangeReason(stats, trigger);
                
                // 서킷브레이커 이벤트 생성
                CircuitBreakerEvent event = CircuitBreakerEvent.stateChange(
                    apiId, 
                    stats.getApiName(), 
                    stats.getApiProvider(),
                    newState, 
                    currentState, 
                    trigger, 
                    reason
                );
                
                // 추가 메트릭 정보 설정
                enrichEventWithMetrics(event, stats);
                
                // 상태 업데이트
                updateCircuitBreakerState(apiId, newState);
                
                // 이벤트 발행
                publishCircuitBreakerEvent(event);
                
                // 높은 우선순위 이벤트의 경우 즉시 알림
                if (event.getSeverity().isHighPriority()) {
                    sendImmediateAlert(event);
                }
                
                log.info("Circuit breaker state changed - API: {}, {} → {}, Trigger: {}", 
                        stats.getApiName(), currentState, newState, trigger);
            }
            
            // 메트릭 업데이트 (상태 변경 여부와 관계없이)
            updateApiMetrics(apiId, stats);
            
        } catch (Exception e) {
            log.error("Failed to check circuit breaker state for API: {}", stats.getApiName(), e);
        }
    }

    /**
     * 통계를 바탕으로 새로운 서킷브레이커 상태 계산
     */
    private CircuitBreakerEvent.CircuitBreakerState calculateNewState(ApiCallStatistics stats, 
                                                                     CircuitBreakerEvent.CircuitBreakerState currentState) {
        
        // 분당 호출량 계산
        long callsPerMinute = stats.getTotalCallCount() / monitoringWindowMinutes;
        
        // 임계치 체크
        boolean excessiveCalls = callsPerMinute > callRateThreshold;
        boolean highFailureRate = stats.getSuccessRate() < (100.0 - failureRateThreshold);
        boolean slowResponse = stats.getAverageResponseTime() > responseTimeThreshold;
        
        // 현재 상태에 따른 상태 전이 로직
        return switch (currentState) {
            case CLOSED -> {
                if (excessiveCalls && highFailureRate) {
                    yield CircuitBreakerEvent.CircuitBreakerState.OPEN;
                } else if (excessiveCalls || highFailureRate || slowResponse) {
                    yield CircuitBreakerEvent.CircuitBreakerState.DEGRADED;
                } else {
                    yield CircuitBreakerEvent.CircuitBreakerState.CLOSED;
                }
            }
            
            case DEGRADED -> {
                if (excessiveCalls && highFailureRate) {
                    yield CircuitBreakerEvent.CircuitBreakerState.OPEN;
                } else if (!excessiveCalls && !highFailureRate && !slowResponse) {
                    yield CircuitBreakerEvent.CircuitBreakerState.CLOSED;
                } else {
                    yield CircuitBreakerEvent.CircuitBreakerState.DEGRADED;
                }
            }
            
            case OPEN -> {
                // OPEN 상태에서는 자동으로 HALF_OPEN으로 전환 (시간 기반)
                LocalDateTime lastStateChange = getLastStateChangeTime(stats.getApiName());
                if (lastStateChange != null && lastStateChange.isBefore(LocalDateTime.now().minusMinutes(5))) {
                    yield CircuitBreakerEvent.CircuitBreakerState.HALF_OPEN;
                } else {
                    yield CircuitBreakerEvent.CircuitBreakerState.OPEN;
                }
            }
            
            case HALF_OPEN -> {
                if (!highFailureRate && !slowResponse) {
                    yield CircuitBreakerEvent.CircuitBreakerState.CLOSED;
                } else if (highFailureRate) {
                    yield CircuitBreakerEvent.CircuitBreakerState.OPEN;
                } else {
                    yield CircuitBreakerEvent.CircuitBreakerState.HALF_OPEN;
                }
            }
            
            case FORCE_OPEN -> CircuitBreakerEvent.CircuitBreakerState.FORCE_OPEN; // 수동 해제 필요
        };
    }

    /**
     * 상태 변경 트리거 결정
     */
    private CircuitBreakerEvent.StateChangeTrigger determineTrigger(ApiCallStatistics stats) {
        long callsPerMinute = stats.getTotalCallCount() / monitoringWindowMinutes;
        
        if (callsPerMinute > callRateThreshold * 2) { // 임계치의 2배 초과
            return CircuitBreakerEvent.StateChangeTrigger.EXCESSIVE_CALLS;
        }
        
        if (stats.getSuccessRate() < (100.0 - failureRateThreshold)) {
            return CircuitBreakerEvent.StateChangeTrigger.HIGH_FAILURE_RATE;
        }
        
        if (stats.getAverageResponseTime() > responseTimeThreshold) {
            return CircuitBreakerEvent.StateChangeTrigger.SLOW_RESPONSE;
        }
        
        if (stats.getFailureCallCount() > consecutiveFailuresThreshold) {
            return CircuitBreakerEvent.StateChangeTrigger.CONSECUTIVE_FAILURES;
        }
        
        return CircuitBreakerEvent.StateChangeTrigger.AUTO_RECOVERY;
    }

    /**
     * 상태 변경 사유 생성
     */
    private String generateStateChangeReason(ApiCallStatistics stats, CircuitBreakerEvent.StateChangeTrigger trigger) {
        long callsPerMinute = stats.getTotalCallCount() / monitoringWindowMinutes;
        
        return switch (trigger) {
            case EXCESSIVE_CALLS -> String.format("분당 호출량 %d회가 임계치 %d회를 초과했습니다.", 
                                                 callsPerMinute, callRateThreshold);
            case HIGH_FAILURE_RATE -> String.format("실패율 %.1f%%가 임계치 %.1f%%를 초과했습니다.", 
                                                   100.0 - stats.getSuccessRate(), failureRateThreshold);
            case SLOW_RESPONSE -> String.format("평균 응답시간 %.0fms가 임계치 %dms를 초과했습니다.", 
                                               stats.getAverageResponseTime(), responseTimeThreshold);
            case CONSECUTIVE_FAILURES -> String.format("연속 실패 %d회가 임계치 %d회를 초과했습니다.", 
                                                      stats.getFailureCallCount(), consecutiveFailuresThreshold);
            case AUTO_RECOVERY -> "모든 지표가 정상 범위로 복구되어 자동으로 상태를 변경합니다.";
            default -> "시스템 모니터링 결과에 따른 상태 변경입니다.";
        };
    }

    /**
     * 이벤트에 메트릭 정보 추가
     */
    private void enrichEventWithMetrics(CircuitBreakerEvent event, ApiCallStatistics stats) {
        long callsPerMinute = stats.getTotalCallCount() / monitoringWindowMinutes;
        
        event.setCurrentCallRate(callsPerMinute);
        event.setThresholdCallRate(callRateThreshold);
        event.setCurrentFailureRate(100.0 - stats.getSuccessRate());
        event.setThresholdFailureRate(failureRateThreshold);
        event.setAverageResponseTime(stats.getAverageResponseTime().longValue());
        
        // 서킷브레이커가 OPEN 상태인 경우 재시도 시간 설정
        if (event.getState() == CircuitBreakerEvent.CircuitBreakerState.OPEN) {
            event.setOpenDurationSeconds(300); // 5분
            event.setNextRetryTime(LocalDateTime.now().plusMinutes(5));
        }
        
        // 메타데이터 추가
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("monitoringWindow", monitoringWindowMinutes + " minutes");
        metadata.put("totalCalls", stats.getTotalCallCount());
        metadata.put("successCalls", stats.getSuccessCallCount());
        metadata.put("failureCalls", stats.getFailureCallCount());
        metadata.put("maxResponseTime", stats.getMaxResponseTime());
        event.setMetadata(metadata);
    }

    /**
     * 현재 서킷브레이커 상태 조회
     */
    private CircuitBreakerEvent.CircuitBreakerState getCurrentState(String apiId) {
        // 로컬 캐시 우선 확인
        CircuitBreakerEvent.CircuitBreakerState cachedState = localStateCache.get(apiId);
        if (cachedState != null) {
            return cachedState;
        }
        
        // Redis에서 조회
        try {
            String stateStr = (String) redisTemplate.opsForValue().get(CB_STATE_PREFIX + apiId);
            if (stateStr != null) {
                CircuitBreakerEvent.CircuitBreakerState state = 
                    CircuitBreakerEvent.CircuitBreakerState.valueOf(stateStr);
                localStateCache.put(apiId, state);
                return state;
            }
        } catch (Exception e) {
            log.warn("Failed to get circuit breaker state from Redis for API: {}", apiId, e);
        }
        
        // 기본값: CLOSED
        CircuitBreakerEvent.CircuitBreakerState defaultState = CircuitBreakerEvent.CircuitBreakerState.CLOSED;
        localStateCache.put(apiId, defaultState);
        return defaultState;
    }

    /**
     * 서킷브레이커 상태 업데이트
     */
    private void updateCircuitBreakerState(String apiId, CircuitBreakerEvent.CircuitBreakerState newState) {
        try {
            // Redis에 저장 (TTL: 24시간)
            redisTemplate.opsForValue().set(CB_STATE_PREFIX + apiId, newState.name(), 24, TimeUnit.HOURS);
            
            // 상태 변경 시간 기록
            redisTemplate.opsForValue().set(CB_STATE_PREFIX + apiId + ":timestamp", 
                                           LocalDateTime.now().toString(), 24, TimeUnit.HOURS);
            
            // 로컬 캐시 업데이트
            localStateCache.put(apiId, newState);
            
        } catch (Exception e) {
            log.error("Failed to update circuit breaker state for API: {}", apiId, e);
        }
    }

    /**
     * API 메트릭 업데이트
     */
    private void updateApiMetrics(String apiId, ApiCallStatistics stats) {
        try {
            LocalMetrics metrics = new LocalMetrics();
            metrics.totalCalls = stats.getTotalCallCount();
            metrics.failureCalls = stats.getFailureCallCount();
            metrics.averageResponseTime = stats.getAverageResponseTime();
            metrics.lastUpdated = LocalDateTime.now();
            
            localMetricsCache.put(apiId, metrics);
            
            // Redis에도 저장 (성능상 배치로 처리 가능)
            Map<String, Object> metricsData = Map.of(
                "totalCalls", stats.getTotalCallCount(),
                "successRate", stats.getSuccessRate(),
                "averageResponseTime", stats.getAverageResponseTime(),
                "lastUpdated", LocalDateTime.now().toString()
            );
            
            redisTemplate.opsForHash().putAll(CB_METRICS_PREFIX + apiId, metricsData);
            redisTemplate.expire(CB_METRICS_PREFIX + apiId, 1, TimeUnit.HOURS);
            
        } catch (Exception e) {
            log.warn("Failed to update API metrics for: {}", apiId, e);
        }
    }

    /**
     * 서킷브레이커 이벤트 발행
     */
    private void publishCircuitBreakerEvent(CircuitBreakerEvent event) {
        try {
            eventPublisher.publishEvent("circuit-breaker-events", event);
            log.info("Circuit breaker event published - API: {}, State: {}, Priority: {}", 
                    event.getApiName(), event.getState(), event.calculatePriorityScore());
                    
        } catch (Exception e) {
            log.error("Failed to publish circuit breaker event", e);
        }
    }

    /**
     * 즉시 알림 발송 (높은 우선순위 이벤트)
     */
    private void sendImmediateAlert(CircuitBreakerEvent event) {
        try {
            // AlertNotificationService를 통한 즉시 알림
            // 여기서는 간단히 로그만 남김 (실제로는 AlertRequest 생성하여 발송)
            log.warn("HIGH PRIORITY CIRCUIT BREAKER ALERT - {}", event.getNotificationMessage());
            
            // TODO: AlertRequest 생성하여 alertNotificationService.processAlert() 호출
            
        } catch (Exception e) {
            log.error("Failed to send immediate circuit breaker alert", e);
        }
    }

    /**
     * 마지막 상태 변경 시간 조회
     */
    private LocalDateTime getLastStateChangeTime(String apiId) {
        try {
            String timestamp = (String) redisTemplate.opsForValue().get(CB_STATE_PREFIX + apiId + ":timestamp");
            return timestamp != null ? LocalDateTime.parse(timestamp) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 로컬 캐시 정리
     */
    private void cleanupLocalCache() {
        // 1시간 이상 된 캐시 엔트리 제거
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        
        localMetricsCache.entrySet().removeIf(entry -> 
            entry.getValue().lastUpdated.isBefore(cutoff));
        
        // 상태 캐시는 크기 제한 (최대 1000개)
        if (localStateCache.size() > 1000) {
            localStateCache.clear();
            log.info("Local state cache cleared due to size limit");
        }
    }

    /**
     * 외부에서 수동으로 서킷브레이커 상태 변경
     */
    public void forceCircuitBreakerState(String apiId, String apiName, String apiProvider, 
                                        CircuitBreakerEvent.CircuitBreakerState forcedState, 
                                        String reason) {
        
        CircuitBreakerEvent.CircuitBreakerState currentState = getCurrentState(apiId);
        
        if (currentState != forcedState) {
            CircuitBreakerEvent event = CircuitBreakerEvent.stateChange(
                apiId, apiName, apiProvider,
                forcedState, currentState,
                CircuitBreakerEvent.StateChangeTrigger.MANUAL_OVERRIDE,
                reason
            );
            
            updateCircuitBreakerState(apiId, forcedState);
            publishCircuitBreakerEvent(event);
            
            log.info("Circuit breaker state manually changed - API: {}, {} → {}, Reason: {}", 
                    apiName, currentState, forcedState, reason);
        }
    }

    /**
     * 현재 모든 서킷브레이커 상태 조회
     */
    public Map<String, CircuitBreakerEvent.CircuitBreakerState> getAllCircuitBreakerStates() {
        Map<String, CircuitBreakerEvent.CircuitBreakerState> states = new HashMap<>(localStateCache);
        
        // Redis에서 추가 상태 조회 (로컬 캐시에 없는 것들)
        try {
            redisTemplate.keys(CB_STATE_PREFIX + "*").forEach(key -> {
                String apiId = key.replace(CB_STATE_PREFIX, "").replaceAll(":timestamp$", "");
                if (!key.endsWith(":timestamp") && !states.containsKey(apiId)) {
                    try {
                        String stateStr = (String) redisTemplate.opsForValue().get(key);
                        if (stateStr != null) {
                            states.put(apiId, CircuitBreakerEvent.CircuitBreakerState.valueOf(stateStr));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse circuit breaker state for API: {}", apiId);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to get all circuit breaker states from Redis", e);
        }
        
        return states;
    }

    /**
     * 로컬 메트릭 저장용 내부 클래스
     */
    private static class LocalMetrics {
        long totalCalls;
        long failureCalls;
        double averageResponseTime;
        LocalDateTime lastUpdated;
    }
}