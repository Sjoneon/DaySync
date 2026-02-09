# DaySync

**AI 기반 일정 및 경로 통합 관리 애플리케이션**

DaySync는 Google Gemini AI와 실시간 교통 정보를 결합하여 사용자의 하루를 최적화하는 Android 애플리케이션입니다. 음성 인식을 통한 자연스러운 대화로 일정을 관리하고, 실시간 교통 상황을 반영한 경로 추천으로 효율적인 시간 관리를 지원합니다.

## 기술 스택

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Python](https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-009688?style=for-the-badge&logo=fastapi&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-232F3E?style=for-the-badge&logo=amazon-aws&logoColor=white)

### 프론트엔드 (Android)
- **Language**: Java (JDK 21)
- **SDK**: Android SDK (minSdk 24, targetSdk 35)
- **Build Tool**: Gradle 8.11.1, AGP 8.9.0
- **Architecture**: MVVM (ViewModel, LiveData)
- **Network**: Retrofit 2.9.0, OkHttp 4.12.0
- **Maps**: Naver Maps SDK 3.22.0
- **Location**: Google Play Services Location 21.3.0
- **Push**: Firebase Cloud Messaging 34.6.0

### 백엔드 (Python)
- **Framework**: FastAPI 0.104.1
- **Server**: Uvicorn 0.24.0
- **ORM**: SQLAlchemy 2.0.23
- **Database**: MySQL Connector 8.2.0
- **Validation**: Pydantic 2.5.1
- **AI SDK**: Google Generative AI 0.8.3

### 인프라 및 외부 서비스
- **Hosting**: AWS EC2 (백엔드 서버)
- **Database**: AWS RDS (MySQL 8.0+)
- **AI Model**: Google Gemini 2.5 Flash
- **Voice**: Android Speech-to-Text API
- **Maps**: Naver Maps API, TMAP API, Google MAPs API
- **Traffic**: 국토교통부 TAGO API (실시간 버스 정보) 4종(버스위치정보, 버스노선정보, 버스정류소정보, 버스도착정보)
- **Weather**: 기상청 단기예보 API

## 시스템 아키텍처

```
Android 앱 (Java)
    ↓
FastAPI 서버 (Python)
    ↓
AWS RDS (MySQL)

외부 API 연동:
- Gemini 2.5 Flash (AI 대화)
- Android STT (음성 인식)
- TMAP (보행자 경로)
- TAGO (버스 정보)
- Naver Maps (지도, Geocoding)
- 기상청 (날씨 정보)
```

REST API 기반의 클라이언트-서버 구조로 설계되었으며, Gemini AI의 Function Calling 기능을 활용하여 자연어 대화로 일정 관리, 알람 설정, 경로 검색 등의 기능을 제공합니다.

## 핵심 기능

### AI 대화형 인터페이스
- **자연어 처리**: Gemini 2.5 Flash 기반 대화형 일정 관리
- **음성 인식**: Android STT API를 통한 음성 입력 지원
- **Function Calling**: AI가 사용자 의도를 파악하여 자동으로 일정, 알람, 경로 검색 실행
- **세션 관리**: 대화 기록 저장 및 이전 대화 불러오기

### 일정 및 알람 관리
- **일정 등록**: 제목, 날짜, 시간, 장소, 메모를 포함한 상세 일정 관리
- **알람 설정**: 사용자 지정 시간 알람 및 일정 기반 자동 알람
- **푸시 알림**: FCM 기반 알람 및 일정 리마인더
- **캘린더 뷰**: 월별/주별 일정 조회 및 관리

### 실시간 교통 정보
- **경로 검색**: 출발지에서 목적지까지 최적 경로 제공
  - TMAP API를 통한 보행자 경로 안내
  - 국토교통부 TAGO API를 통한 실시간 버스 도착 정보
- **경로 저장**: 자주 가는 경로 저장 및 재사용
- **지도 표시**: Naver Maps SDK를 통한 경로 시각화
- **소요 시간**: 실시간 교통 상황을 반영한 예상 소요 시간

### 날씨 정보
- **현재 날씨**: 시간별 기온, 강수 확률, 하늘 상태
- **주간 예보**: 3일치 날씨 예보 제공
- **위치 기반**: 사용자의 현재 위치 또는 지정 위치 날씨 제공

## 데이터 설계

### 주요 테이블 구조

#### 사용자 (users)
```sql
CREATE TABLE users (
    uuid VARCHAR(36) PRIMARY KEY,
    nickname VARCHAR(50),
    created_at DATETIME,
    last_login DATETIME,
    is_active BOOLEAN
);
```

#### AI 세션 (sessions)
```sql
CREATE TABLE sessions (
    session_id INT PRIMARY KEY AUTO_INCREMENT,
    user_uuid VARCHAR(36),
    title VARCHAR(100),
    created_at DATETIME,
    updated_at DATETIME,
    FOREIGN KEY (user_uuid) REFERENCES users(uuid)
);
```

#### 일정 (calendars)
```sql
CREATE TABLE calendars (
    event_id INT PRIMARY KEY AUTO_INCREMENT,
    user_uuid VARCHAR(36),
    title VARCHAR(100),
    start_time DATETIME,
    end_time DATETIME,
    location VARCHAR(200),
    memo TEXT,
    FOREIGN KEY (user_uuid) REFERENCES users(uuid)
);
```

#### 알람 (alarms)
```sql
CREATE TABLE alarms (
    alarm_id INT PRIMARY KEY AUTO_INCREMENT,
    user_uuid VARCHAR(36),
    time TIME,
    label VARCHAR(100),
    is_active BOOLEAN,
    FOREIGN KEY (user_uuid) REFERENCES users(uuid)
);
```

## 프로젝트 구조

```
DaySync/
├── app/
│   ├── src/main/
│   │   ├── java/com/sjoneon/cap/
│   │   │   ├── activities/         # Activity 클래스
│   │   │   │   ├── MainActivity.java
│   │   │   │   ├── LoginActivity.java
│   │   │   │   └── AlarmActivity.java
│   │   │   ├── fragments/          # Fragment 클래스
│   │   │   │   ├── CalendarFragment.java
│   │   │   │   ├── AlarmFragment.java
│   │   │   │   ├── RouteFragment.java
│   │   │   │   ├── WeatherFragment.java
│   │   │   │   └── SettingsFragment.java
│   │   │   ├── adapters/           # RecyclerView 어댑터
│   │   │   ├── models/             # 데이터 모델
│   │   │   │   ├── api/            # API 요청/응답
│   │   │   │   └── local/          # 로컬 데이터
│   │   │   ├── services/           # API 서비스
│   │   │   │   ├── DaySyncApiService.java
│   │   │   │   ├── SpeechToTextService.java
│   │   │   │   ├── WeatherApiService.java
│   │   │   │   └── TagoApiService.java
│   │   │   ├── viewmodels/         # ViewModel
│   │   │   ├── helpers/            # 헬퍼 클래스
│   │   │   └── utils/              # 유틸리티
│   │   ├── res/                    # 리소스 파일
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts

DaySync_Server/
├── app/
│   ├── main.py                     # FastAPI 앱
│   ├── database.py                 # DB 연결
│   ├── models.py                   # ORM 모델
│   ├── schemas.py                  # Pydantic 스키마
│   └── routers/                    # API 라우터
│       ├── users.py
│       ├── ai_chat.py
│       ├── calendar_alarm.py
│       └── routes.py
├── requirements.txt
└── run.py
```

### 아키텍처 설계 원칙

**MVVM 아키텍처 (Android)**
- Model-View-ViewModel 패턴을 통한 관심사 분리
- LiveData를 활용한 반응형 UI 업데이트
- Repository 패턴으로 데이터 계층 추상화

**RESTful API 설계 (Backend)**
- 리소스 기반의 명확한 엔드포인트 구조
- HTTP 메서드를 통한 CRUD 작업 정의
- Pydantic을 통한 요청/응답 검증

**모듈화 및 재사용성**
- 기능별 모듈 분리로 유지보수성 향상
- Helper 클래스를 통한 공통 기능 재사용
- 인터페이스 기반 설계로 확장성 확보

## 설치 및 실행

### 사전 요구사항
- **Android Studio**: Arctic Fox 이상 (JDK 21 포함)
- **Python**: 3.11 이상
- **MySQL**: 8.0 이상
- **Android Device**: API 24 (Android 7.0) 이상

### 환경 설정

**1. 저장소 복제**
```bash
git clone https://github.com/Sjoneon/DaySync.git
git clone https://github.com/Sjoneon/DaySync_Server.git
```

**2. 백엔드 설정**
```bash
cd DaySync_Server

# 가상환경 생성 및 활성화
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# 의존성 설치
pip install -r requirements.txt

# 환경 변수 설정 (.env 파일)
DB_HOST=your_db_host
DB_PORT=3306
DB_USER=your_db_user
DB_PASSWORD=your_db_password
DB_NAME=daysync_db
GEMINI_API_KEY=your_gemini_api_key

# 데이터베이스 초기화
mysql -u root -p < daysyncdata.sql

# 서버 실행
python run.py
```

**3. Android 앱 설정**
```bash
cd DaySync

# local.properties 파일 설정
NAVER_CLIENT_ID=your_naver_client_id
NAVER_CLIENT_SECRET=your_naver_client_secret
TMAP_API_KEY=your_tmap_api_key
TAGO_API_KEY=your_tago_api_key
WEATHER_API_KEY=your_weather_api_key
API_BASE_URL=http://your_server_url:8000

# Android Studio에서 프로젝트 열기
# Build -> Make Project (Ctrl+F9)
# Run -> Run 'app' (Shift+F10)
```

## 주요 특징

### 사용자 경험
- **직관적 UI**: Material Design 기반의 현대적인 인터페이스
- **음성 지원**: 텍스트 입력 없이 음성으로 모든 기능 사용 가능
- **실시간 반응**: AI 응답 및 교통 정보의 실시간 업데이트
- **개인화**: 사용자 패턴 학습을 통한 맞춤형 경로 추천

### 기술적 특징
- **AI Function Calling**: 자연어를 통한 직접적인 기능 실행
- **비동기 처리**: FastAPI의 async/await 패턴으로 높은 처리량 구현
- **캐싱 전략**: 자주 조회되는 경로 및 날씨 정보 캐싱으로 성능 최적화
- **보안**: API 키 관리 및 UUID 기반 사용자 식별로 보안 강화

## 개발 과정에서 고려한 점

### 문제 해결 접근
- **사용자 편의성**: 복잡한 일정 관리를 자연스러운 대화로 단순화
- **실시간성**: 교통 정보의 시의성을 확보하기 위한 캐싱 및 API 최적화
- **확장성**: 향후 기능 추가를 고려한 모듈형 구조 설계

### 기술적 도전과 해결
- **AI 통합**: Gemini Function Calling을 활용하여 구조화된 데이터 추출
- **다중 API 연동**: TMAP, TAGO, Naver Maps 등 여러 API의 일관된 인터페이스 구현
- **성능 최적화**: 데이터베이스 인덱싱 및 쿼리 최적화로 응답 시간 단축
- **사용자 경험**: 음성 인식과 텍스트 입력의 자연스러운 전환

## 학습 성과

### 기술적 성장
- **Android 개발**: MVVM 아키텍처와 Jetpack 라이브러리 활용 능력 향상
- **백엔드 개발**: FastAPI를 통한 RESTful API 설계 및 구현 경험
- **AI 통합**: LLM의 Function Calling 기능을 실제 서비스에 적용
- **클라우드 인프라**: AWS EC2, RDS를 활용한 서버 운영 경험

### 소프트웨어 개발 경험
- **풀스택 개발**: 프론트엔드부터 백엔드, 데이터베이스까지 전체 스택 구현
- **API 설계**: RESTful API 설계 원칙과 문서화의 중요성 체득
- **협업 도구**: Git을 활용한 버전 관리 및 프로젝트 문서화 경험

## 프로젝트 회고

### 성공 요인

**AI 기술의 실용적 적용**
Gemini AI의 Function Calling 기능을 활용하여 자연어로 복잡한 일정 관리를 수행할 수 있게 구현했습니다. 이를 통해 사용자는 "내일 오후 3시에 회의 잡아줘"와 같은 자연스러운 대화만으로 일정을 등록할 수 있습니다.

**실시간 데이터 통합**
여러 외부 API를 통합하여 실시간 교통 정보와 날씨 정보를 제공함으로써 사용자의 일정 관리를 보다 현실적이고 유용하게 만들었습니다.

**모듈화된 설계**
기능별로 명확하게 분리된 모듈 구조 덕분에 새로운 기능 추가 및 유지보수가 용이했습니다.

### 개선 사항

**버스 GPS 위치정보 미사용**
실시간 버스 GPS 위치정보를 활용하려 했으나, 대량의 데이터 처리로 인해 경로 검색 시간이 과도하게 증가하는 문제가 발생했습니다. 이를 해결하기 위해 정류장 순서 기반의 간이 처리 방식을 채택했지만, GPS 위치정보를 사용할 경우 더 정확한 버스 대기시간 및 목적지 도착 예상시간을 제공할 수 있을 것으로 생각합니다. 향후 데이터 처리 최적화 및 캐싱 전략 개선을 통해 GPS 기반 실시간 추적 기능을 도입할 필요가 있습니다.

**테스트 자동화**
단위 테스트와 통합 테스트를 초기부터 작성하지 못해 버그 발견이 늦어진 경우가 있었습니다. 이후 프로젝트에서는 TDD 방식을 도입할 계획이 있습니다.

**사용자 피드백 반영**
제한적인 사용자 테스트로 인해 다양한 사용 시나리오와 UX 문제를 충분히 검증하지 못했습니다. 향후 더 폭넓은 피드백 수집을 통한 개선이 필요합니다.

## 향후 계획

- **오프라인 모드**: 네트워크 없이도 기본 기능 사용 가능하도록 로컬 DB 구현
- **위젯 지원**: 홈 화면 위젯을 통한 빠른 일정 확인 및 등록
- **소셜 기능**: 일정 공유 및 그룹 일정 관리 기능
- **다국어 지원**: 영어, 중국어 등 다국어 인터페이스 제공
- **웨어러블 연동**: 스마트워치와의 연동을 통한 편의성 향상

## 개발자 정보

- **개발자**: 송재원
- **학과**: 컴퓨터공학과
- **소속**: 서원대학교
- **개발 기간**: 2025.03 - 2025.12
