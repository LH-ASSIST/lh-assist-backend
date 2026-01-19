package com.lh.assist.config;

import io.github.cdimascio.dotenv.Dotenv;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		Dotenv dotenv = Dotenv.configure()
				.ignoreIfMissing()
				.load();

		Map<String, Object> values = new HashMap<>();
		dotenv.entries().forEach(entry -> values.put(entry.getKey(), entry.getValue()));

		if (!values.isEmpty()) {
			environment.getPropertySources().addLast(new MapPropertySource("dotenv", values));
		}
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}
}