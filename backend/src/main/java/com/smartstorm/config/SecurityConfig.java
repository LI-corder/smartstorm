package com.smartstorm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置（游客只读，登录可写）：
 * <ul>
 *   <li>写入接口需登录：POST /api/rooms、POST/DELETE /api/notes/** 走 .authenticated()；</li>
 *   <li>其余（读接口、/ws、/api/auth/**）permitAll；</li>
 *   <li>JwtAuthFilter 解析 Bearer token 写入 SecurityContext；</li>
 *   <li>未认证访问受保护接口 → 自定义 401 JSON（与 Result 结构一致）。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 写入操作需登录
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/rooms").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/notes/**").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/notes/**").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/rooms/*/analysis").authenticated()
                        // 个人中心需登录
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/profile").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/profile").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/profile/change-password").authenticated()
                        // 其余（读接口 / 认证 / WebSocket）匿名可访问
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(objectMapper.writeValueAsString(
                            com.smartstorm.common.Result.fail(401, "未登录或登录已过期")));
                }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
