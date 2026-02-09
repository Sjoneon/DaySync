# DaySync - AI 기반 일정 관리 애플리케이션

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![FastAPI](https://img.shields.io/badge/Backend-FastAPI-009688.svg)](https://fastapi.tiangolo.com)
[![Gemini](https://img.shields.io/badge/AI-Gemini%202.5-4285F4.svg)](https://ai.google.dev)

> AI와 대화하듯 일정을 관리하는 스마트한 방법

DaySync는 Gemini AI를 활용한 대화형 일정 관리 앱입니다. 음성이나 텍스트로 자연스럽게 대화하면 AI가 자동으로 일정을 생성하고, 실시간 교통 정보와 날씨를 고려한 최적의 경로를 추천합니다.

## 목차

- [시스템 아키텍처](#시스템-아키텍처)
- [주요 기능](#주요-기능)
- [기술 스택](#기술-스택)
- [설치 및 실행](#설치-및-실행)
- [프로젝트 구조](#프로젝트-구조)
- [문서](#문서)

## 시스템 아키텍처

### 아키텍처 개요

DaySync는 **클라이언트-서버 분리 아키텍처**를 채택하여 Android 클라이언트와 FastAPI 백엔드가 RESTful API로 통신합니다.

```mermaid
graph LR
    subgraph client["클라이언트 레이어"]
        A[UI 레이어]
        B[데이터 레이어]
        C[음성 인식]
    end
    
    subgraph backend["백엔드 레이어"]
        D[API 게이트웨이]
        E[비즈니스 로직]
        F[AI 통합]
    end
    
    subgraph external["외부 서비스"]
        G[Gemini AI]
        H[Maps API]
        I[Weather API]
    end
    
    subgraph data["데이터 레이어"]
        J[(데이터베이스)]
        K[(캐시)]
    end
    
    A --> B
    B --> C
    C --> B
    
    B --> D
    D --> E
    E --> F
    
    F --> G
    E --> H
    E --> I
    
    E --> J
    E --> K
    
    D --> B
    B --> A
    
    style A fill:#bbdefb,stroke:#1976d2,stroke-width:3px,color:#000
    style B fill:#bbdefb,stroke:#1976d2,stroke-width:3px,color:#000
    style C fill:#bbdefb,stroke:#1976d2,stroke-width:3px,color:#000
    style D fill:#ce93d8,stroke:#7b1fa2,stroke-width:3px,color:#000
    style E fill:#ce93d8,stroke:#7b1fa2,stroke-width:3px,color:#000
    style F fill:#ce93d8,stroke:#7b1fa2,stroke-width:3px,color:#000
    style G fill:#ffcc80,stroke:#f57c00,stroke-width:3px,color:#000
    style H fill:#ffcc80,stroke:#f57c00,stroke-width:3px,color:#000
    style I fill:#ffcc80,stroke:#f57c00,stroke-width:3px,color:#000
    style J fill:#a5d6a7,stroke:#388e3c,stroke-width:3px,color:#000
    style K fill:#a5d6a7,stroke:#388e3c,stroke-width:3px,color:#000
```

### 계층별 역할

| 계층 | 역할 | 주요 기술 |
|-----|------|----------|
| **클라이언트 레이어** | 사용자 인터페이스 및 로컬 데이터 처리 | Java, Android SDK, Retrofit |
| **백엔드 레이어** | 비즈니스 로직 처리 및 서비스 조정 | Python, FastAPI, SQLAlchemy |
| **외부 서비스** | AI 분석, 지도, 날씨 등 외부 기능 제공 | Gemini AI, Maps API, Weather API |
| **데이터 레이어** | 영속성 데이터 저장 및 캐싱 | MySQL, Redis |

---

## 주요 기능

### AI 대화형 일정 관리
- 자연어 처리: "내일 오후 3시에 강남역에서 회의"라고 말하면 자동으로 일정 생성
- Function Calling: Gemini AI가 사용자 의도를 분석하여 구조화된 데이터 추출
- 세션 관리: 대화 맥락을 유지하여 연속적인 일정 관리 가능

### 음성 인식
- Android STT API 활용
- 실시간 음성-텍스트 변환
- 핸즈프리 일정 생성

### 스마트 경로 추천
- 실시간 교통 정보: 버스 도착 정보 실시간 확인
- 보행자 경로: TMAP API를 활용한 최적 경로 안내
- 경로 캐싱: 자주 사용하는 경로를 캐싱하여 빠른 응답

### 날씨 연동
- 기상청 단기예보 API 연동
- 일정 시간의 날씨 정보 자동 표시
- 우천 시 알림 기능

### 알람 관리
- 일정별 알람 설정
- 반복 알람 지원
- 출발 시간 알림 (교통 정보 고려)

---

## 기술 스택

### Frontend (Android)
```
Language:     Java (JDK 21)
SDK:          Android SDK (minSdk 24, targetSdk 35)
Architecture: MVVM (ViewModel, LiveData)
Network:      Retrofit 2.9.0, OkHttp 4.12.0
Map:          Naver Maps SDK 3.22.0
Location:     Google Play Services 21.3.0
Push:         Firebase Cloud Messaging 34.6.0
```

### Backend (FastAPI)
```
Language:     Python 3.11+
Framework:    FastAPI 0.104.1
Server:       Uvicorn 0.24.0
ORM:          SQLAlchemy 2.0.23
Validation:   Pydantic 2.5.1
AI SDK:       google-generativeai 0.8.3
```

### Infrastructure
```
Server:       AWS EC2
Database:     AWS RDS (MySQL 8.0+)
AI Model:     Gemini 2.5 Flash
```

### External APIs
- **Gemini 2.5 Flash** - AI 대화 및 일정 관리
- **TMAP API** - 보행자 경로 안내
- **국토교통부 TAGO API** - 실시간 버스 정보
- **네이버 Maps API** - 지도, Geocoding, Directions
- **기상청 단기예보 API** - 날씨 정보

---

## 설치 및 실행

### Android 앱 빌드

```bash
# 1. 리포지토리 클론
git clone https://github.com/yourusername/DaySync.git
cd DaySync

# 2. API 키 설정 (local.properties)
NAVER_CLIENT_ID=your_naver_client_id
NAVER_CLIENT_SECRET=your_naver_client_secret
TMAP_API_KEY=your_tmap_api_key
TAGO_API_KEY=your_tago_api_key
WEATHER_API_KEY=your_weather_api_key

# 3. 빌드 및 실행
./gradlew assembleDebug
./gradlew installDebug
```

### 백엔드 서버 실행

```bash
# 1. 백엔드 디렉토리로 이동
cd DaySync_Server

# 2. 환경변수 설정 (.env)
DATABASE_URL=mysql+mysqlconnector://user:password@host:port/daysync_db
GEMINI_API_KEY=your_gemini_api_key

# 3. 의존성 설치
pip install -r requirements.txt

# 4. 서버 실행
python run.py
```

### 데이터베이스 설정

```bash
# MySQL 접속
mysql -u root -p

# 스키마 생성
mysql -u root -p < daysyncdata.sql
```

---

## 프로젝트 구조

```
DaySync/
├── app/src/main/java/com/sjoneon/cap/
│   ├── activities/          # Activity 클래스
│   ├── fragments/           # Fragment 클래스
│   ├── models/              # 데이터 모델
│   ├── services/            # API 서비스
│   ├── repositories/        # 데이터 저장소
│   ├── viewmodels/          # ViewModel
│   └── helpers/             # 유틸리티
│
DaySync_Server/
├── app/
│   ├── routers/             # API 라우터
│   │   ├── users.py
│   │   ├── ai_chat.py
│   │   ├── calendar_alarm.py
│   │   └── routes.py
│   ├── models.py            # ORM 모델
│   ├── schemas.py           # Pydantic 스키마
│   └── database.py          # DB 연결
│
architecture/                # 아키텍처 문서
├── system-overview.md
├── android-architecture.md
├── backend-architecture.md
└── database-schema.md
```

---

## 주요 데이터 플로우

### 음성으로 일정 생성하기

```
사용자: "내일 오후 3시에 강남역에서 회의 일정 잡아줘"
   ↓
[Android] STT 변환
   ↓
[Android] POST /api/ai/chat
   ↓
[FastAPI] Gemini AI 분석 요청
   ↓
[Gemini] 의도 분석 및 구조화
   {
     "intent": "create_event",
     "title": "회의",
     "datetime": "2024-02-10 15:00",
     "location": "강남역"
   }
   ↓
[FastAPI] DB 저장 (calendars 테이블)
   ↓
[Android] 캘린더 UI 업데이트
```

---

## 문서

### 아키텍처 문서

프로젝트의 상세한 아키텍처 정보는 [`architecture/`](./architecture/) 디렉토리에서 확인할 수 있습니다:

- **[시스템 개요](./architecture/Design_System_overview.md)** - 전체 시스템 구조, 기술 스택, 데이터 플로우
- **[Android 아키텍처](./architecture/Design_Android_architecture.md)** - MVVM 패턴, 프로젝트 구조, 주요 컴포넌트
- **[백엔드 아키텍처](./architecture/Design_Backend_architecture.md)** - FastAPI 구조, API 설계, 비즈니스 로직
- **[데이터베이스 스키마](./architecture/Design_Database_schema.md)** - ERD, 테이블 구조, 인덱스 전략

---

## 개발자

**송재원** - 서원대학교 컴퓨터공학과

---

## 저작권

본 프로젝트는 서원대학교 컴퓨터공학과 캡스톤 디자인 프로젝트로 제작되었습니다.

해당 코드의 저작권은 송재원([@Sjoneon](https://github.com/Sjoneon))에게 있습니다.
