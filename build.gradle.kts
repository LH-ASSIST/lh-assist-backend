plugins {
	java
	id("org.springframework.boot") version "3.5.9"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.lh.assist"
version = "0.0.1-SNAPSHOT"
description = "LH 공공사업 리스크 탐지 및 규정 준수 AI 어시스턴트 백엔드"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-batch")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	// 애플리케이션 상태 모니터링 및 관리용
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	// 지표 데이터를 프로메테우스 포맷으로 변환
	implementation("io.micrometer:micrometer-registry-prometheus")
	//swagger
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")
	// AWS S3 연동
	implementation ("io.awspring.cloud:spring-cloud-starter-aws:2.4.4")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.projectreactor:reactor-test")
	testImplementation("org.springframework.batch:spring-batch-test")
	testImplementation("org.springframework.security:spring-security-test")
	//test container
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}