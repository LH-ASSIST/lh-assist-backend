# LH Assist Backend

LH 공공주택 문서 리스크 분석/검토 서비스를 위한 Spring Boot 백엔드입니다.  
`lh-assist-rag-service`와 연동해 문서 업로드, 분석 요청/콜백, RAG 챗봇 스트리밍, 공지/건의, 관리자 기능을 제공합니다.

## 서비스 개요
- **문서 처리/오케스트레이션**: 업로드, 분석 요청(SQS), 상태/결과 동기화
- **리스크 결과 조회**: 섹션별 리스크/근거/점수 조회 API
- **RAG 챗봇 브리지**: FastAPI 스트리밍 응답을 SSE로 중계
- **운영 기능**: 공지사항, 건의사항(QnA), 사용자/권한 관리

## 기술 스택
- **Spring Boot**: 3.5.x, Java 21
- **Database**: PostgreSQL + pgvector
- **Cache/Rate limit**: Redis
- **Messaging**: Amazon SQS
- **Monitoring**: Prometheus / Grafana
- **Security**: Spring Security (JWT)

## 아키텍처 요약
- **메인 앱**: `src/main/java/com/lh/assist`
- **설정**: `src/main/resources/application.yaml`
- **AI 연동**: `lh-assist-rag-service`(FastAPI)와 SQS/HTTP 콜백
- **벡터 검색**: PostgreSQL + pgvector

## 프로젝트 구성
- `src/main/java`: Spring Boot 애플리케이션 코드
- `src/main/resources`: 설정 파일
- `src/test/java`: JUnit 테스트
- `build.gradle.kts`: 빌드/의존성
- `docker-compose.yml`: 로컬 의존 서비스

## 실행 방법
요청하신 실행 순서 기준:

```bash
# 1) 로컬 의존 서비스 기동 (Redis, DB)
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build redis db
```

```bash
# 2) 백엔드 실행 (로컬 프로파일)
./gradlew bootRun --args="--spring.profiles.active=local --server.port=8080"
```

기본 API 서버: `http://localhost:8080`

## DB 준비 사항 (필수)
정상 동작을 위해 **PostgreSQL에 pgvector 확장 적용**과 **`rag_data.dump` 적재**가 필요합니다.
- pgvector 확장을 활성화해야 벡터 검색이 동작합니다.
- `rag_data.dump`를 복원하지 않으면 RAG 근거 데이터가 없어 기능이 제한됩니다.

## 환경 변수
`.env.example`을 참고해 `.env`를 구성하세요.

주요 항목:
- `DATABASE_URL`, `SPRING_DATASOURCE_*`
- `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `SQS_QUEUE_URL`
- `OPENAI_API_KEY`

## 주요 엔드포인트 (예시)
- 인증: `/api/v1/auth/*`
- 사용자: `/api/v1/user/*`
- 문서/분석: `/api/v1/documents/*`, `/api/v1/analysis/*`
- 챗봇: `/api/v1/chat/*`
- 공지/건의: `/api/v1/notice/*`, `/api/v1/qna*`
- 관리자: `/api/v1/admin/*`

## 서비스 기동 순서
전체 기능 확인을 위해 아래 서비스가 함께 실행되어야 합니다.
- `lh-assist-rag-service`
- `lh-assist-backend` (현재 저장소)
- `lh-assist-frontend`
