# LH Assist Backend

LH 공공주택 문서 리스크 분석 서비스의 Spring Boot 백엔드입니다. `lh-assist-rag-service`(FastAPI)와 SQS/HTTP로 연동해서 문서 업로드, 분석, RAG 챗봇 스트리밍, 공지/건의, 관리자 기능을 제공합니다.

## 기술 스택
- Spring Boot 3.5.x, Java 21 (Gradle Kotlin DSL)
- PostgreSQL + pgvector, Redis
- Flyway (`src/main/resources/db/migration`)
- Amazon SQS (분석 요청/콜백 메시징)
- Spring Security(JWT), Spring Batch
- springdoc-openapi, Prometheus/Micrometer, Loki 로깅
- 테스트: JUnit5, Testcontainers(PostgreSQL)

## 프로젝트 구조
`src/main/java/com/lh/assist/<도메인>` 아래 도메인별 패키지, 각 도메인은 대체로 다음 레이어로 구성됩니다.
- `api` (컨트롤러/dto/mapper, `api/docs`는 OpenAPI 문서)
- `application` (서비스/유스케이스)
- `domain` (`entity`, `enums`, `repository`)

도메인 목록: `admin`, `analysis`, `approval`, `audit`, `auth`, `chatbot`, `common`, `document`, `infrastructure`, `notice`, `reg`, `suggestion`, `test`, `user`

- `infrastructure`: AWS SQS 등 외부 연동 어댑터
- `common`: `ErrorCode` 등 공통 예외/유틸
- `reg`: 규정(Regulation) 조회 도메인 — Repository와 QueryDSL/JPQL 성능 최적화 이력 있음

## 빌드 / 테스트 / 실행
```bash
./gradlew clean build     # 빌드 + 테스트
./gradlew test            # 테스트만
./gradlew bootRun --args="--spring.profiles.active=local --server.port=8080"
```

## 로컬 의존 서비스
```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build redis db
```
- **필수**: PostgreSQL에 pgvector 확장 활성화, `rag_data.dump` 복원 (없으면 RAG 근거 데이터 누락)
- 시크릿: `src/main/resources/application-sample.yml` → `application-secrets.yml` 복사 후 채움, 또는 `.env` (`.env.example` 참고)

## 전체 스택 기동 순서
`lh-assist-rag-service` → `lh-assist-backend`(현재 저장소) → `lh-assist-frontend` 순으로 함께 실행해야 전체 기능 확인 가능.

## DB 마이그레이션
- Flyway 파일명 규칙: `V<날짜>_<순번>__<설명>.sql` (예: `V20260908_0001__reg_query_performance_and_dedup.sql`)
- 신규 마이그레이션 추가 시 기존 데이터 정합성(dedup, integrity 제약) 함께 고려할 것 — 최근 이력이 이 패턴을 따름

## 코딩 컨벤션
- 4-space 들여쓰기, `PascalCase`(클래스)/`camelCase`(메서드·필드)/`UPPER_SNAKE_CASE`(상수)
- Lombok 적극 사용 (기존 코드 패턴 따름)
- 예외는 `common/exception/ErrorCode`에 정의된 코드 체계를 따름

## 커밋/PR
- 커밋 메시지는 conventional-ish 접두사(`docs:`, `chore:`, `feat:`, `fix:`) 사용
- PR에는 변경 내용, 설정 변경 여부, 테스트 결과 명시
