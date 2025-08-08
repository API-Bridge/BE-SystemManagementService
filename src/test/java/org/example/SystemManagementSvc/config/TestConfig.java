package org.example.SystemManagementSvc.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;

/**
 * 테스트 환경용 설정
 * 외부 의존성(Redis, Kafka)를 비활성화
 */
@TestConfiguration
@EnableAutoConfiguration(exclude = {
    RedisAutoConfiguration.class,
    KafkaAutoConfiguration.class
})
public class TestConfig {
    // 테스트용 Bean 정의 (필요한 경우)
}