package org.example.SystemManagementSvc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.SystemManagementSvc.domain.ExternalApi;
import org.example.SystemManagementSvc.dto.ApiAvailabilityResponse;
import org.example.SystemManagementSvc.dto.ApiStatusSummary;
import org.example.SystemManagementSvc.repository.ExternalApiRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * API 상태 관리 공통 라이브러리
 * - 실시간 API 가용성 확인
 * - Redis TTL 기반 효율적 상태 관리
 * - 다양한 상태 조회 인터페이스 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiStatusManager {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ExternalApiRepository externalApiRepository;
    
    private static final String UNHEALTHY_API_PREFIX = "unhealthy:";
    private static final String API_METADATA_PREFIX = "api_meta:";
    private static final String HEALTH_STATS_KEY = "health_stats:summary";
    
    /**
     * 특정 API의 현재 가용성 확인
     * Zero Storage 전략: 정상 API는 Redis에 저장하지 않음
     */
    public boolean isApiAvailable(String apiId) {
        try {
            // Redis에 키가 없으면 정상 (1-2ms 초고속 조회)
            return !Boolean.TRUE.equals(redisTemplate.hasKey(UNHEALTHY_API_PREFIX + apiId));
        } catch (Exception e) {
            log.warn("Failed to check API availability for: {}, assuming available", apiId, e);
            return true; // Redis 장애 시 기본적으로 가용으로 간주
        }
    }
    
    /**
     * 여러 API의 가용성을 일괄 확인
     * 배치 조회로 성능 최적화
     */
    public Map<String, Boolean> checkApisAvailability(List<String> apiIds) {
        if (apiIds == null || apiIds.isEmpty()) {
            return Map.of();
        }
        
        try {
            // Redis Pipeline으로 배치 조회
            List<String> unhealthyKeys = apiIds.stream()
                .map(apiId -> UNHEALTHY_API_PREFIX + apiId)
                .collect(Collectors.toList());
            
            List<Object> pipelineResults = redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                for (String key : unhealthyKeys) {
                    connection.exists(key.getBytes());
                }
                return null;
            });
            
            List<Boolean> existsResults = new ArrayList<>();
            for (Object result : pipelineResults) {
                existsResults.add((Boolean) result);
            }
            
            Map<String, Boolean> availabilityMap = new HashMap<>();
            for (int i = 0; i < apiIds.size(); i++) {
                // 키가 없으면 가용, 있으면 불가용
                boolean isAvailable = !Boolean.TRUE.equals(existsResults.get(i));
                availabilityMap.put(apiIds.get(i), isAvailable);
            }
            
            return availabilityMap;
            
        } catch (Exception e) {
            log.error("Failed to check multiple APIs availability", e);
            // 실패 시 모든 API를 가용으로 간주
            return apiIds.stream()
                .collect(Collectors.toMap(apiId -> apiId, apiId -> true));
        }
    }
    
    /**
     * 도메인별 가용한 API 목록 조회
     */
    public List<ExternalApi> getAvailableApisByDomain(ExternalApi.ApiDomain domain) {
        try {
            List<ExternalApi> domainApis = externalApiRepository.findByApiDomainAndApiEffectivenessTrue(domain);
            
            if (domainApis.isEmpty()) {
                return List.of();
            }
            
            List<String> apiIds = domainApis.stream()
                .map(ExternalApi::getApiId)
                .collect(Collectors.toList());
            
            Map<String, Boolean> availabilityMap = checkApisAvailability(apiIds);
            
            return domainApis.stream()
                .filter(api -> availabilityMap.getOrDefault(api.getApiId(), true))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to get available APIs by domain: {}", domain, e);
            return List.of();
        }
    }
    
    /**
     * 키워드별 가용한 API 목록 조회
     */
    public List<ExternalApi> getAvailableApisByKeyword(ExternalApi.ApiKeyword keyword) {
        try {
            List<ExternalApi> keywordApis = externalApiRepository.findByApiKeywordAndApiEffectivenessTrue(keyword);
            
            if (keywordApis.isEmpty()) {
                return List.of();
            }
            
            List<String> apiIds = keywordApis.stream()
                .map(ExternalApi::getApiId)
                .collect(Collectors.toList());
            
            Map<String, Boolean> availabilityMap = checkApisAvailability(apiIds);
            
            return keywordApis.stream()
                .filter(api -> availabilityMap.getOrDefault(api.getApiId(), true))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to get available APIs by keyword: {}", keyword, e);
            return List.of();
        }
    }
    
    /**
     * 현재 불가용한 API 목록 조회
     */
    public List<String> getUnavailableApiIds() {
        try {
            Set<String> unhealthyKeys = redisTemplate.keys(UNHEALTHY_API_PREFIX + "*");
            return unhealthyKeys.stream()
                .map(key -> key.replace(UNHEALTHY_API_PREFIX, ""))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get unavailable API IDs", e);
            return List.of();
        }
    }
    
    /**
     * API 상태 상세 정보 조회
     */
    @SuppressWarnings("unchecked")
    public ApiAvailabilityResponse getApiStatusDetails(String apiId) {
        try {
            boolean isAvailable = isApiAvailable(apiId);
            
            if (isAvailable) {
                return ApiAvailabilityResponse.builder()
                    .apiId(apiId)
                    .isAvailable(true)
                    .status("HEALTHY")
                    .message("API is currently available")
                    .checkedAt(LocalDateTime.now())
                    .build();
            }
            
            // 불가용한 경우 상세 정보 조회
            String cacheKey = UNHEALTHY_API_PREFIX + apiId;
            Map<String, Object> statusData = (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
            
            if (statusData == null) {
                return ApiAvailabilityResponse.builder()
                    .apiId(apiId)
                    .isAvailable(true)
                    .status("HEALTHY")
                    .message("API status cache expired, assuming healthy")
                    .checkedAt(LocalDateTime.now())
                    .build();
            }
            
            return ApiAvailabilityResponse.builder()
                .apiId(apiId)
                .isAvailable(false)
                .status(statusData.getOrDefault("status", "UNKNOWN").toString())
                .message(statusData.getOrDefault("errorMessage", "API is currently unavailable").toString())
                .responseTimeMs(Long.valueOf(statusData.getOrDefault("responseTime", 0).toString()))
                .consecutiveFailures(Integer.valueOf(statusData.getOrDefault("consecutiveFailures", 0).toString()))
                .checkedAt(parseDateTime(statusData.get("lastCheck")))
                .ttlSeconds(redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS))
                .build();
            
        } catch (Exception e) {
            log.error("Failed to get API status details for: {}", apiId, e);
            return ApiAvailabilityResponse.builder()
                .apiId(apiId)
                .isAvailable(true)
                .status("UNKNOWN")
                .message("Failed to check API status")
                .checkedAt(LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * 전체 API 상태 요약 정보
     */
    public ApiStatusSummary getApiStatusSummary() {
        try {
            long totalApis = externalApiRepository.count();
            long effectiveApis = externalApiRepository.countByApiEffectivenessTrue();
            long unavailableApis = getUnavailableApiIds().size();
            long availableApis = effectiveApis - unavailableApis;
            
            // 도메인별 통계
            Map<ExternalApi.ApiDomain, Long> domainStats = Arrays.stream(ExternalApi.ApiDomain.values())
                .collect(Collectors.toMap(
                    domain -> domain,
                    domain -> (long) getAvailableApisByDomain(domain).size()
                ));
            
            return ApiStatusSummary.builder()
                .totalApis(totalApis)
                .effectiveApis(effectiveApis)
                .availableApis(availableApis)
                .unavailableApis(unavailableApis)
                .availabilityRate(effectiveApis > 0 ? (double) availableApis / effectiveApis * 100 : 100.0)
                .domainStats(domainStats)
                .lastUpdated(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get API status summary", e);
            return ApiStatusSummary.builder()
                .totalApis(0L)
                .effectiveApis(0L)
                .availableApis(0L)
                .unavailableApis(0L)
                .availabilityRate(0.0)
                .domainStats(Map.of())
                .lastUpdated(LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * 우선순위별 가용 API 조회
     */
    public Map<ExternalApi.HealthCheckPriority, List<ExternalApi>> getAvailableApisByPriority() {
        try {
            List<ExternalApi> allApis = externalApiRepository.findByApiEffectivenessTrue();
            
            List<String> apiIds = allApis.stream()
                .map(ExternalApi::getApiId)
                .collect(Collectors.toList());
            
            Map<String, Boolean> availabilityMap = checkApisAvailability(apiIds);
            
            return allApis.stream()
                .filter(api -> availabilityMap.getOrDefault(api.getApiId(), true))
                .collect(Collectors.groupingBy(ExternalApi::getHealthCheckPriority));
                
        } catch (Exception e) {
            log.error("Failed to get available APIs by priority", e);
            return Map.of();
        }
    }
    
    /**
     * 장애 복구 예상 시간 조회
     */
    public Map<String, Long> getRecoveryEstimates() {
        try {
            List<String> unavailableApis = getUnavailableApiIds();
            Map<String, Long> estimates = new HashMap<>();
            
            for (String apiId : unavailableApis) {
                String cacheKey = UNHEALTHY_API_PREFIX + apiId;
                Long ttl = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
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
     * API 메타데이터 캐싱
     */
    public void cacheApiMetadata(String apiId, ExternalApi api) {
        try {
            String cacheKey = API_METADATA_PREFIX + apiId;
            Map<String, Object> metadata = Map.of(
                "apiName", api.getApiName(),
                "apiUrl", api.getApiUrl(),
                "apiIssuer", api.getApiIssuer(),
                "apiDomain", api.getApiDomain().name(),
                "apiKeyword", api.getApiKeyword().name(),
                "healthCheckPriority", api.getHealthCheckPriority().name(),
                "cachedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
            
            redisTemplate.opsForValue().set(cacheKey, metadata, 1, TimeUnit.HOURS);
            
        } catch (Exception e) {
            log.warn("Failed to cache API metadata for: {}", apiId, e);
        }
    }
    
    /**
     * 캐시된 API 메타데이터 조회
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCachedApiMetadata(String apiId) {
        try {
            String cacheKey = API_METADATA_PREFIX + apiId;
            return (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Failed to get cached API metadata for: {}", apiId, e);
            return null;
        }
    }
    
    /**
     * DateTime 파싱 헬퍼 메소드
     */
    private LocalDateTime parseDateTime(Object dateTimeObj) {
        if (dateTimeObj == null) {
            return LocalDateTime.now();
        }
        
        try {
            return LocalDateTime.parse(dateTimeObj.toString());
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}