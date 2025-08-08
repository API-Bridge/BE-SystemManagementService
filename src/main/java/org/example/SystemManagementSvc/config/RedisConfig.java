package org.example.SystemManagementSvc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 캐시 및 세션 저장소 설정 클래스
 * MSA 환경에서 성능 향상을 위한 Redis 기반 캐시 시스템 구성
 * 
 * 주요 기능:
 * - Redis 연결 팩토리 및 커넥션 풀 설정
 * - RedisTemplate 구성 (JSON 직렬화/역직렬화)
 * - Spring Cache 추상화를 위한 캐시 매니저 설정
 * - TTL 및 캐시 정책 관리
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redisHost, redisPort)
        );
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))  // 캐시 만료 시간: 10분 (API 데이터 실시간성 고려)
                .disableCachingNullValues();       // null 값 캐싱 비활성화 (메모리 낭비 방지)

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)  // 기본 캐시 설정 적용
                .build();
        
        // 캐시 전략:
        // - 짧은 TTL(10분): 외부 API 데이터의 실시간성 보장
        // - 건강체크 결과 캐싱으로 네트워크 비용 절약
        // - 대시보드 데이터 캐싱으로 성능 향상
    }
}