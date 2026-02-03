package com.lh.assist.common.security;

import com.lh.assist.common.security.jwt.JwtAuthenticationFilter;
import com.lh.assist.chatbot.api.filter.ChatRateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final ChatRateLimitFilter chatRateLimitFilter;
	private final Environment environment;

	public SecurityConfig(
			JwtAuthenticationFilter jwtAuthenticationFilter,
			ChatRateLimitFilter chatRateLimitFilter,
			Environment environment
	) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.chatRateLimitFilter = chatRateLimitFilter;
		this.environment = environment;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
				.requestMatchers(
						"/v3/api-docs/**",
						"/swagger-ui/**",
						"/swagger-ui.html",
						"/api/v1/auth/**",
						"/api/v1/chat/**",
						"/test/**",
						"/actuator/health",
						"/actuator/health/**"
				).permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/analysis/jobs/*/callback").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/user/password/reset").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/qna/all").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/notice/search").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/notice/all").permitAll()
						.anyRequest().authenticated()
				)
				.addFilterBefore(chatRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		String[] allowedOrigins = environment.getProperty(
				"app.cors.allowed-origins",
				String[].class,
				new String[0]
		);
		List<String> originList = Arrays.stream(allowedOrigins)
				.map(String::trim)
				.filter(value -> !value.isEmpty())
				.toList();

		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(originList);
		configuration.setAllowedOrigins(List.of(
				"https://lh-assist.cloud",
				"https://d1v7ka3ykr3zod.cloudfront.net",
				"http://localhost:5173"
		));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

		configuration.setAllowedHeaders(List.of("*"));
		configuration.setExposedHeaders(List.of("Authorization"));

		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}