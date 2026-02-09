'''mermaid
    sequenceDiagram
        actor User as 사용자
        participant RF as RouteFragment
        participant API as DaySyncApiService
        participant Server as FastAPI Server
        participant TAGO as 국토교통부 API
        participant TMAP as TMAP API
        participant DB as MySQL Database
    
        User->>RF: 출발지/목적지 입력
        User->>RF: 경로 검색 버튼 클릭
        
        RF->>API: 경로 검색 요청
        Note over RF,API: 출발지, 목적지,<br/>출발 시간 전달
        
        API->>Server: 경로 탐색 요청
        
        Server->>DB: 캐시 데이터 확인
        Note over Server,DB: 최근 5분 이내<br/>동일 경로 조회
        
        alt 캐시 존재
            DB-->>Server: 캐싱된 경로 반환
        else 캐시 없음
            Server->>TAGO: 주변 정류장 검색
            TAGO-->>Server: 정류장 목록 반환
            
            Server->>TAGO: 버스 노선 정보 조회
            TAGO-->>Server: 노선 정보 반환
            
            Server->>TAGO: 실시간 도착 정보 조회
            TAGO-->>Server: 도착 예정 시간 반환
            
            Server->>Server: 버스 경로 분석
            Note over Server: 상행/하행 구분<br/>회차점 체크<br/>유효 경로 필터링
            
            Server->>TMAP: 도보 경로 계산
            TMAP-->>Server: 도보 경로 반환
            
            Server->>Server: 최적 경로 선정
            Note over Server: 버스+도보 조합<br/>소요시간 계산
            
            Server->>DB: 경로 캐싱
            Note over Server,DB: 5분간 유효
        end
        
        Server-->>API: 경로 목록 반환
        Note over Server,API: 버스 정보, 소요시간,<br/>환승 정보
        
        API-->>RF: 경로 데이터 수신
        
        RF->>RF: 화면 갱신
        
        RF->>User: 추천 경로 표시
        Note over RF,User: 버스 번호, 정류장,<br/>소요시간, 요금
'''
