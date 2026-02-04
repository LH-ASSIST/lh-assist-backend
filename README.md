# 📑 lh-assist-backend (LH-Assist AI)

LH 공공주택본부의 사업 리스크를 사전에 탐지하고 규정 준수 여부를 검증하는 **AI 기반 업무 어시스턴트 백엔드** 시스템입니다. 본 시스템은 고부하 AI 분석 작업을 비동기로 처리하며, 법령 위계 기반의 정교한 RAG 시스템을 지향합니다.

---

## 🏗 Backend Introduction

본 백엔드는 **Spring Boot(Java)** 기반의 메인 서버와 **FastAPI(Python)** 기반의 AI 엔진을 **Amazon SQS**로 연결한 비동기 아키텍처로 설계되었습니다.

### 🎯 핵심 역할

* **오케스트레이션**: 사용자의 요청을 수신하고 SQS를 통해 AI 엔진에 분석 태스크를 배분 및 결과 동기화.
* **데이터 관리**: PostgreSQL(pgvector)을 활용한 법령 메타데이터 및 스냅샷 관리.
* **비동기 파이프라인**: 대용량 문서 분석 중 서버 부하를 방지하기 위한 메시지 큐 기반 처리.
* **모니터링 & 보안**: Prometheus, Grafana를 활용한 가시성 확보 및 IAM/KMS 기반의 엔터프라이즈 보안 적용.

---

## 🛠 Tech Stack & Architecture

### 🔹 Infrastructure

* **Frontend**: React (Static Web Hosting via CloudFront)
* **Backend**: Spring Boot 3.5.9 (Java 21), Spring Batch, Redis
* **AI Engine**: FastAPI, Langchain, Langgraph, Python
* **Storage**: AWS S3 (Original Docs, Results), PostgreSQL (Metadata, pgvector)
* **Messaging**: Amazon SQS (Async Processing)
* **Monitoring**: Prometheus, Grafana, CloudWatch
* **Security**: IAM, KMS, Spring Security (JWT)

벡터 검색은 **PostgreSQL + pgvector**로만 처리합니다. 
Redis는 캐시/레이트리밋 등 상태 관리 용도로 사용합니다.

---

## ✅ 개발 체크리스트 (현황)

* [x] 문서 업로드 및 S3 저장/삭제, presigned URL 발급
* [x] 분석 요청 생성 및 SQS 메시지 발행(Producer)
* [x] 분석 콜백 처리 및 상태 동기화
* [x] 분석 결과 스키마(analysis_results/sections/risk_items/evidences) 저장 및 조회 API
* [x] RAG 근거 테이블(regulations/reg_items, audit_items) 및 pgvector 저장
* [x] 챗봇 SSE 스트리밍 브리지(FastAPI 연동)
* [x] JWT 인증/인가 + BCrypt 비밀번호 암호화
* [x] 이메일 인증 발송/검증
* [x] Redis 기반 레이트리밋(챗 스트림) 및 조회수 집계 플러시
* [ ] 법령/시행세칙 데이터 자동 수집 배치(Spring Batch)
* [ ] 기준일자별 규정 버전 관리(Snapshot)
* [ ] 감사 매뉴얼(audit_manuals/audit_manual_items) 데이터 적재 파이프라인
* [ ] 분석 진행 상태(Progress Bar) 캐싱
* [ ] 리스크 엔진 가중치/의무조항 점수화 로직
* [ ] 감사사례 유사도 매칭 알고리즘 고도화
* [ ] DLQ 모니터링 및 알림
* [ ] KMS 기반 암호화 적용

---

## 🚀 시작하기

```bash
# 1. 저장소 클론
git clone https://github.com/lh-assist/lh-assist-backend.git

# 2. 환경 변수 설정
cp src/main/resources/application-sample.yml src/main/resources/application-secrets.yml
# (AWS Access Key, DB URL, OpenAI Key 등 입력)

# 3. 빌드 및 실행
./gradlew clean build
java -jar build/libs/lh-assist-backend-0.0.1-SNAPSHOT.jar

```
---

## Local vs. Cloud Architecture

본 프로젝트는 개발 편의성을 위한 로컬 환경(Docker Compose)과 고가용성/확장성을 고려한 운영 환경(AWS)을 분리하여 관리합니다. 애플리케이션의 핵심 로직(Dockerfile)은 동일하게 유지되나, 이를 둘러싼 인프라 구성은 환경에 최적화된 방식으로 전환됩니다.

### 🔹 환경별 구성 비교 (Infrastructure Mapping)

| 구성 요소 | 로컬 개발 환경 (Local) | 실제 운영 환경 (Production) | 핵심 변경 포인트 |
| --- | --- | --- | --- |
| **백엔드 로직** | `backend` 서비스 컨테이너 | **AWS ECS/EC2 (Auto-scaling)** | 트래픽에 따른 자동 확장 및 고가용성 확보 |
| **데이터베이스** | `db` (Postgres + pgvector) | **AWS RDS for PostgreSQL** | 데이터 백업, Multi-AZ 복제, 보안 관리 자동화 |
| **메시지 큐** | 로컬에서는 실제 SQS 사용(또는 분석 비활성) | **Amazon SQS** | 완전 관리형 메시징 서비스를 통한 데이터 유실 방지 |
| **모니터링** | `prometheus / grafana` 컨테이너 | **AWS Managed Prometheus / Grafana** | 모니터링 시스템 자체의 안정성 분리 및 가용성 확보 |
| **저장소** | 로컬 볼륨 (postgres_data) | **Amazon S3 / RDS Storage** | 휘발성 컨테이너와 분리된 영구적·안정적 데이터 저장 |
