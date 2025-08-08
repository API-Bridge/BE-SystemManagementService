package org.example.SystemManagementSvc.config;

import org.example.SystemManagementSvc.security.AudienceValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 및 Auth0 JWT 토큰 인증 설정 클래스
 * OAuth2 Resource Server로서 Auth0에서 발급한 JWT 토큰을 검증하여 API 보안 제공
 * 
 * 주요 기능:
 * - Stateless JWT 기반 인증 (세션 사용 안함)
 * - Auth0 JWT 토큰 검증 및 Audience 확인
 * - 메소드 레벨 보안 (@PreAuthorize, @PostAuthorize)
 * - 공개 엔드포인트 설정 (health check, swagger 등)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Profile("!dev")  // dev 프로파일이 아닌 모든 환경에서 사용 (prod, staging 등)
public class SecurityConfig {

    @Value("${auth0.audience}")
    private String audience;

    @Value("${auth0.issuerUri}")
    private String issuer;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)  // CSRF 비활성화 (JWT 사용으로 stateless)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // 세션 사용 안함
            .authorizeHttpRequests(authz -> authz
                // 공개 엔드포인트 - 인증 불필요
                .requestMatchers("/actuator/**").permitAll()  // Spring Boot Actuator 메트릭
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()  // API 문서
                .requestMatchers("/api/v1/health").permitAll()  // 헬스체크
                // 나머지 모든 요청은 JWT 토큰 인증 필요
                .anyRequest().authenticated()
            )
            // OAuth2 Resource Server 설정 - Auth0 JWT 토큰 검증
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())  // JWT 디코더 (서명 검증 포함)
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())  // JWT를 Spring Security Authentication으로 변환
                )
            );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // Auth0의 OIDC 엔드포인트에서 자동으로 JWT 디코더 생성
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromOidcIssuerLocation(issuer);

        // JWT 토큰 검증자 설정
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(audience);  // 사용자 정의 audience 검증
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);  // 기본 issuer 검증
        // 여러 검증자를 결합 (issuer + audience + signature + expiry)
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);

        jwtDecoder.setJwtValidator(withAudience);

        return jwtDecoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // JWT에서 Spring Security 권한을 추출하는 컨버터
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        converter.setAuthoritiesClaimName("permissions");  // Auth0에서 사용하는 권한 claim 명
        converter.setAuthorityPrefix("");  // Spring Security의 기본 "ROLE_" 접두사 제거

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(converter);
        return jwtConverter;
    }
}