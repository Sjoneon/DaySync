@startuml
title 버스 경로 검색 - 설계 수준

actor 사용자 as User
participant RouteFragment as RF
participant DaySyncApiService as API
participant "FastAPI Server" as Server
participant "국토교통부 API" as TAGO
participant "TMAP API" as TMAP
database "MySQL Database" as DB

User -> RF: 출발지/목적지 입력
User -> RF: 경로 검색 버튼 클릭

RF -> API: 경로 검색 요청
note right
  출발지, 목적지,
  출발 시간 전달
end note

API -> Server: 경로 탐색 요청

Server -> DB: 캐시 데이터 확인
note right
  최근 5분 이내
  동일 경로 조회
end note

alt 캐시 존재
    DB --> Server: 캐싱된 경로 반환
else 캐시 없음
    Server -> TAGO: 주변 정류장 검색
    TAGO --> Server: 정류장 목록 반환
    
    Server -> TAGO: 버스 노선 정보 조회
    TAGO --> Server: 노선 정보 반환
    
    Server -> TAGO: 실시간 도착 정보 조회
    TAGO --> Server: 도착 예정 시간 반환
    
    Server -> Server: 버스 경로 분석
    note right
      상행/하행 구분
      회차점 체크
      유효 경로 필터링
    end note
    
    Server -> TMAP: 도보 경로 계산
    TMAP --> Server: 도보 경로 반환
    
    Server -> Server: 최적 경로 선정
    note right
      버스+도보 조합
      소요시간 계산
    end note
    
    Server -> DB: 경로 캐싱
    note right
      5분간 유효
    end note
end

Server --> API: 경로 목록 반환
note left
  버스 정보, 소요시간,
  환승 정보
end note

API --> RF: 경로 데이터 수신

RF -> RF: 화면 갱신

RF -> User: 추천 경로 표시
note right
  버스 번호, 정류장,
  소요시간, 요금
end note

@enduml
