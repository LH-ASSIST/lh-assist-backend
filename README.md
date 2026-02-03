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

---

## ✅ 개발 체크리스트 (Main Checklist)

### 2️⃣ 데이터 파이프라인 및 규정 DB 관리

* [ ] 법령/시행세칙 데이터 자동 수집 배치(Spring Batch) 구현
* [ ] pgvector 기반의 규정 위계 DB 구축 (Semantic Chunking)
* [ ] 기준일자별 규정 버전 관리(Snapshot) 시스템 구현

### 3️⃣ 비동기 분석 엔진 개발

* [ ] SQS 기반 Spring Boot ↔ FastAPI 비동기 통신 로직 구현
* [ ] 문서 업로드 및 텍스트 추출(S3 연동) 프로세스 최적화

### 4️⃣ 지능형 서비스 및 인터페이스

* [ ] RAG 기반 규정 질의응답 챗봇 서비스 구현
* [ ] 리스크 하이라이팅 및 보완 가이드 제공 API
* [ ] 감사 소명용 이력 관리 및 대시보드 API

---

## 🔍 세부 구현 체크리스트

### 📂 1. Infra & Security Details

* [ ] **ALB & CloudFront**: 정적 호스팅 연동 및 부하 분산 설정
* [ ] **Spring Security**: JWT 기반 인증 및 BCrypt 비밀번호 암호화
* [ ] **KMS Encryption**: S3 저장 문서 및 DB 민감 컬럼(AES-256) 암호화
* [ ] **Monitoring**: 에러 발생 시 SQS Dead Letter Queue(DLQ) 모니터링 및 알림 설정

### 📂 2. Data & Batch Details

* [ ] **External API**: 법제처/나라장터 Open API 연동 모듈 개발
* [ ] **Metadata Mapping**: 법령-시행령-시행세칙 간 위계 그래프 DB 설계
* [ ] **Batch Processing**: 대량의 PDF/HWP 문서 분절 및 벡터화 자동화

### 📂 3. Analysis & Messaging Details

* [ ] **SQS Producer/Consumer**: 분석 요청 메시지 발행 및 완료 메시지 소비 로직
* [ ] **Redis Caching**: 분석 진행 상태(Progress Bar) 실시간 추적 및 캐싱
* [ ] **Risk Engine**:
* [ ] `risk_type`별 가중치 적용 로직
* [ ] `is_mandatory` 필드 기반 점수 증폭 알고리즘
* [ ] 과거 감사사례 유사도 매칭 알고리즘



### 📂 4. AI & RAG Details

* [ ] **FastAPI Bridge**: Langchain을 활용한 규정 근거/유사 사례 검색 레이어
* [ ] **Explainable AI**: 분석 결과에 대한 근거 문장 매핑 및 시각화 좌표 생성
* [ ] **Chatbot Integration**: Langgraph 기반의 대화형 업무 가이드 시나리오 구현

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
| **메시지 큐** | Docker 기반 Mock SQS | **Amazon SQS** | 완전 관리형 메시징 서비스를 통한 데이터 유실 방지 |
| **모니터링** | `prometheus / grafana` 컨테이너 | **AWS Managed Prometheus / Grafana** | 모니터링 시스템 자체의 안정성 분리 및 가용성 확보 |
| **저장소** | 로컬 볼륨 (postgres_data) | **Amazon S3 / RDS Storage** | 휘발성 컨테이너와 분리된 영구적·안정적 데이터 저장 |
