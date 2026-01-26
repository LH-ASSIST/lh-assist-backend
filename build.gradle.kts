plugins {
	java
	id("org.springframework.boot") version "3.5.9"
	id("io.spring.dependency-management") version "1.1.7"
//	jacoco
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
	testImplementation(platform("org.testcontainers:testcontainers-bom:1.19.8"))
	implementation("org.springframework.boot:spring-boot-starter-batch")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
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
	implementation("com.amazonaws:aws-java-sdk-sqs:1.12.710")
	implementation("io.github.cdimascio:dotenv-java:3.0.0")
	implementation("io.jsonwebtoken:jjwt-api:0.12.5")
	runtimeOnly("io.netty:netty-resolver-dns-native-macos:4.1.117.Final:osx-aarch_64")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
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
	testImplementation("org.testcontainers:testcontainers")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

//jacoco {
//	toolVersion = "0.8.12"
//}
//
//tasks.jacocoTestReport {
//	dependsOn(tasks.test)
//	reports {
//		xml.required.set(true)
//		html.required.set(true)
//	}
//}
