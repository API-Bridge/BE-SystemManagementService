package org.example.SystemManagementSvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 개발 환경용 Security 설정
 * Auth0 연동 없이 모든 요청을 허용하여 개발 및 테스트 용이성 제공
 * 
 * 주요 차이점:
 * - SecurityConfig: JWT 토큰 검증, Auth0 연동, 프로덕션 보안 설정
 * - DevSecurityConfig: 모든 인증 우회, 개발 편의성 우선
 */
@Configuration
@EnableWebSecurity
@Profile("dev")
public class DevSecurityConfig {

    @Bean
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)  // CSRF 비활성화
            .authorizeHttpRequests(authz -> authz
                .anyRequest().permitAll()  // 모든 요청 허용 - JWT 토큰 없이도 접근 가능
            );
        // OAuth2 Resource Server 설정 없음 - JWT 토큰 검증하지 않음
        // SessionManagement 설정 없음 - 기본 세션 관리 사용

        return http.build();
    }
}