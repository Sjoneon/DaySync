# 데이터베이스 스키마

## 개요

DaySync는 **MySQL 8.0+**를 관계형 데이터베이스로 사용하며, **정규화된 스키마 설계**를 통해 데이터 무결성을 보장합니다.

---

## 데이터베이스 구조

### ERD (Entity Relationship Diagram)

```mermaid
  erDiagram
      users ||--o{ sessions
      users ||--o{ calendars
      users ||--o{ alarms
      users ||--o{ notifications
      users ||--o{ favorite_places
      
      sessions ||--o{ messages
      
      calendars ||--o{ calendar_event_notifications
      
      bus_routes ||--o{ bus_route_stations
      bus_route_stations }o--|| bus_stops
      
      users {
          varchar uuid PK
          varchar nickname
          datetime created_at
          datetime last_active
          boolean is_deleted
      }
      
      sessions {
          int id PK
          varchar user_uuid FK
          varchar title
          datetime created_at
          datetime updated_at
      }
      
      calendars {
          int id PK
          varchar user_uuid FK
          varchar title
          text description
          datetime start_time
          datetime end_time
          varchar location
          varchar category
      }
```

---

## 테이블 구조

### 1. 사용자 및 세션 관리

#### users - 사용자 기본 정보

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| uuid | VARCHAR(36) | PK, INDEX | UUID 기반 사용자 식별자 |
| nickname | VARCHAR(50) | NOT NULL | 사용자 닉네임 |
| created_at | DATETIME | DEFAULT NOW() | 계정 생성 시간 |
| last_active | DATETIME | DEFAULT NOW() | 마지막 활동 시간 |
| is_deleted | BOOLEAN | DEFAULT FALSE | 논리적 삭제 플래그 |

**인덱스**: uuid, is_deleted

---

#### sessions - AI 대화 세션

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| id | INT | PK, AUTO_INCREMENT | 세션 ID |
| user_uuid | VARCHAR(36) | FK, INDEX | 사용자 UUID |
| title | VARCHAR(100) | NULL | 세션 제목 |
| created_at | DATETIME | DEFAULT NOW() | 세션 생성 시간 |
| updated_at | DATETIME | ON UPDATE NOW() | 마지막 업데이트 시간 |

**관계**: user_uuid → users.uuid (ON DELETE CASCADE)

---

#### messages - AI 세션별 메시지

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| id | INT | PK, AUTO_INCREMENT | 메시지 ID |
| session_id | INT | FK, INDEX | 세션 ID |
| role | ENUM('user', 'assistant') | NOT NULL | 메시지 역할 |
| content | TEXT | NOT NULL | 메시지 내용 |
| created_at | DATETIME | DEFAULT NOW() | 메시지 생성 시간 |

**관계**: session_id → sessions.id (ON DELETE CASCADE)

---

### 2. 일정 및 알람

#### calendars - 사용자 일정 정보

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| id | INT | PK, AUTO_INCREMENT | 일정 ID |
| user_uuid | VARCHAR(36) | FK, INDEX | 사용자 UUID |
| title | VARCHAR(100) | NOT NULL | 일정 제목 |
| description | TEXT | NULL | 일정 설명 |
| start_time | DATETIME | NOT NULL, INDEX | 시작 시간 |
| end_time | DATETIME | NULL | 종료 시간 |
| location | VARCHAR(200) | NULL | 장소 |
| category | VARCHAR(50) | NULL | 카테고리 |
| created_at | DATETIME | DEFAULT NOW() | 생성 시간 |

**복합 인덱스**: (user_uuid, start_time)

---

#### alarms - 알람 설정

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| id | INT | PK, AUTO_INCREMENT | 알람 ID |
| user_uuid | VARCHAR(36) | FK, INDEX | 사용자 UUID |
| alarm_time | TIME | NOT NULL | 알람 시간 |
| label | VARCHAR(100) | NULL | 알람 라벨 |
| is_active | BOOLEAN | DEFAULT TRUE | 활성화 여부 |
| repeat_days | VARCHAR(20) | NULL | 반복 요일 (예: "1,2,3,4,5") |
| created_at | DATETIME | DEFAULT NOW() | 생성 시간 |

---

#### notifications - 앱 내 알림 목록

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| id | INT | PK, AUTO_INCREMENT | 알림 ID |
| user_uuid | VARCHAR(36) | FK, INDEX | 사용자 UUID |
| title | VARCHAR(100) | NOT NULL | 알림 제목 |
| message | TEXT | NOT NULL | 알림 내용 |
| type | ENUM('event', 'alarm', 'system') | NOT NULL | 알림 타입 |
| is_read | BOOLEAN | DEFAULT FALSE | 읽음 여부 |
| created_at | DATETIME | DEFAULT NOW() | 생성 시간 |

**복합 인덱스**: (user_uuid, is_read, created_at)

---

### 3. 사용자 설정

#### favorite_places - 즐겨찾는 장소

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| id | INT | PK, AUTO_INCREMENT | 장소 ID |
| user_uuid | VARCHAR(36) | FK, INDEX | 사용자 UUID |
| place_name | VARCHAR(100) | NOT NULL | 장소 이름 |
| address | VARCHAR(200) | NULL | 주소 |
| latitude | DECIMAL(10, 8) | NULL | 위도 |
| longitude | DECIMAL(11, 8) | NULL | 경도 |
| category | VARCHAR(50) | NULL | 카테고리 (집, 회사, 기타) |
| created_at | DATETIME | DEFAULT NOW() | 생성 시간 |

---

#### user_preferences - 사용자 앱 설정

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| id | INT | PK, AUTO_INCREMENT | 설정 ID |
| user_uuid | VARCHAR(36) | FK, UNIQUE | 사용자 UUID |
| theme | ENUM('light', 'dark') | DEFAULT 'light' | 테마 설정 |
| notification_enabled | BOOLEAN | DEFAULT TRUE | 알림 수신 여부 |
| default_alarm_time | INT | DEFAULT 30 | 기본 알람 시간 (분 전) |
| language | VARCHAR(10) | DEFAULT 'ko' | 언어 설정 |

---

### 4. 버스 정보

#### bus_stops - 버스 정류장 정보

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| id | INT | PK, AUTO_INCREMENT | 정류장 ID |
| stop_name | VARCHAR(100) | NOT NULL | 정류장 이름 |
| stop_id | VARCHAR(50) | NOT NULL, INDEX | 정류장 고유 ID |
| city_code | VARCHAR(20) | NOT NULL | 도시 코드 |
| latitude | DECIMAL(10, 8) | NOT NULL | 위도 |
| longitude | DECIMAL(11, 8) | NOT NULL | 경도 |

**복합 인덱스**: (stop_id, city_code)

---

#### bus_routes - 버스 노선 정보

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| id | INT | PK, AUTO_INCREMENT | 노선 ID |
| route_name | VARCHAR(50) | NOT NULL | 노선 번호 |
| route_id | VARCHAR(50) | NOT NULL, INDEX | 노선 고유 ID |
| city_code | VARCHAR(20) | NOT NULL | 도시 코드 |
| route_type | ENUM('local', 'express', 'intercity') | NULL | 노선 유형 |

---

#### bus_route_stations - 노선별 정류장 순서

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| id | INT | PK, AUTO_INCREMENT | ID |
| route_id | INT | FK | 노선 ID |
| stop_id | INT | FK | 정류장 ID |
| sequence | INT | NOT NULL | 정류장 순서 |

**복합 인덱스**: (route_id, sequence)

---

### 5. 캐시 테이블

#### route_cache - 경로 검색 결과 캐시

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| id | INT | PK, AUTO_INCREMENT | 캐시 ID |
| start_location | VARCHAR(200) | NOT NULL | 출발지 |
| end_location | VARCHAR(200) | NOT NULL | 도착지 |
| route_data | JSON | NOT NULL | 경로 데이터 |
| cached_at | DATETIME | DEFAULT NOW() | 캐시 시간 |
| expires_at | DATETIME | NOT NULL, INDEX | 만료 시간 (TTL: 30분) |

**복합 인덱스**: (start_location, end_location, expires_at)

---

#### weather_cache - 날씨 정보 캐시

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| id | INT | PK, AUTO_INCREMENT | 캐시 ID |
| location_key | VARCHAR(100) | NOT NULL, INDEX | 위치 키 |
| weather_data | JSON | NOT NULL | 날씨 데이터 |
| cached_at | DATETIME | DEFAULT NOW() | 캐시 시간 |
| expires_at | DATETIME | NOT NULL | 만료 시간 (TTL: 10분) |

---

#### bus_arrivals_cache - 실시간 도착 정보 캐시

| 컬럼명 | 타입 | 제약조건 | 설명 |
|-------|------|---------|------|
| id | INT | PK, AUTO_INCREMENT | 캐시 ID |
| stop_id | INT | FK, INDEX | 정류장 ID |
| route_id | INT | FK | 노선 ID |
| arrival_data | JSON | NOT NULL | 도착 정보 |
| cached_at | DATETIME | DEFAULT NOW() | 캐시 시간 |
| expires_at | DATETIME | NOT NULL, INDEX | 만료 시간 (TTL: 1분) |

---

## 인덱스 전략

### 단일 인덱스
- 기본 키 (PK)
- 외래 키 (FK)
- 자주 조회되는 컬럼 (uuid, created_at 등)

### 복합 인덱스
```sql
-- 일정 조회 최적화
(user_uuid, start_time)

-- 경로 캐시 조회
(start_location, end_location, expires_at)

-- 버스 노선 정류장 순서
(route_id, sequence)

-- 알림 조회
(user_uuid, is_read, created_at)
```

---

## 데이터베이스 생성

### 스키마 생성
```sql
CREATE DATABASE daysync_db 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;
```

---

## 데이터베이스 최적화

### 정기 작업
- 만료된 캐시 삭제 (매시간)
- 오래된 로그 삭제 (30일 이상)

### 백업 전략
- 일일 백업
- 주간 전체 백업

---

## 관련 문서

- [시스템 개요](./system-overview.md)
- [Android 아키텍처](./android-architecture.md)
- [백엔드 아키텍처](./backend-architecture.md)
