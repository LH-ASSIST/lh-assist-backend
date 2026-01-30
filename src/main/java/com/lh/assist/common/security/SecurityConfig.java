package com.lh.assist.common.security;

import com.lh.assist.common.security.jwt.JwtAuthenticationFilter;
import com.lh.assist.chatbot.api.filter.ChatRateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final ChatRateLimitFilter chatRateLimitFilter;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ChatRateLimitFilter chatRateLimitFilter) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.chatRateLimitFilter = chatRateLimitFilter;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/v3/api-docs/**",
						"/swagger-ui/**",
						"/swagger-ui.html",
						"/api/v1/auth/**",
						"/api/v1/chat/**",
						"/actuator/health",
						"/actuator/health/**"
				).permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/qna").permitAll()
						.anyRequest().authenticated()
				)
				.addFilterBefore(chatRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}