package com.lh.assist.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
public abstract class IntegrationTestBase {

	// @Container 사용으로 자원관리
	@SuppressWarnings("resource")
	@Container
	static final PostgreSQLContainer<?> POSTGRES =
		new PostgreSQLContainer<>(
			DockerImageName.parse("ankane/pgvector:latest")
				.asCompatibleSubstituteFor("postgres")
		)
			.withDatabaseName("lh_assist_db")
			.withUsername("postgres")
			.withPassword("postgres")
			.withInitScript("db/init-test.sql");

	@DynamicPropertySource
	static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}
}