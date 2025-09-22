// app/src/main/java/com/sjoneon/cap/BusDirectionAnalyzer.java

package com.sjoneon.cap;

import android.util.Log;
import java.util.List;
import java.util.ArrayList;

/**
 * 버스 회차 방향을 정확히 분석하는 클래스 (회차 구간 방향 판정 문제 해결)
 * A-B-A 회차 노선에서 현재 버스가 어느 구간(A→B 또는 B→A)을 운행 중인지 정확히 판별
 */
public class BusDirectionAnalyzer {
    private static final String TAG = "BusDirectionAnalyzer";

    /**
     * 회차 방향 정보를 담는 내부 클래스
     */
    public static class RouteDirectionInfo {
        public boolean isValidDirection;        // 올바른 방향 여부
        public String currentSegment;          // 현재 운행 구간 (A→B 또는 B→A)
        public String directionDescription;    // 방향 설명
        public int confidence;                 // 신뢰도 (0-100)
        public String correctDestination;      // 정확한 목적지 정보
        public boolean isForwardDirection;     // 정방향 여부

        public RouteDirectionInfo(boolean isValid, String segment, String description, int confidence) {
            this.isValidDirection = isValid;
            this.currentSegment = segment;
            this.directionDescription = description;
            this.confidence = confidence;
            this.correctDestination = "목적지";
            this.isForwardDirection = true;
        }

        public RouteDirectionInfo(boolean isValid, String segment, String description, int confidence,
                                  String destination, boolean isForward) {
            this.isValidDirection = isValid;
            this.currentSegment = segment;
            this.directionDescription = description;
            this.confidence = confidence;
            this.correctDestination = destination;
            this.isForwardDirection = isForward;
        }
    }

    /**
     * 🔧 완전히 수정된 회차 방향 분석 (회차 구간 간 이동 차단)
     */
    public static RouteDirectionInfo analyzeRouteDirection(
            TagoBusStopResponse.BusStop startStop,
            TagoBusStopResponse.BusStop endStop,
            TagoBusArrivalResponse.BusArrival bus,
            List<TagoBusRouteStationResponse.RouteStation> routeStations) {

        Log.d(TAG, "🔍 " + bus.routeno + "번 버스 회차 방향 분석 시작");

        // 1. 기본 유효성 검사
        if (routeStations == null || routeStations.isEmpty()) {
            return new RouteDirectionInfo(false, "UNKNOWN", "노선 정보 없음", 0);
        }

        // 2. 회차점/종점 분석
        TerminalInfo terminalInfo = analyzeTerminals(routeStations);
        Log.d(TAG, "회차점 분석: " + terminalInfo.toString());

        // 3. 정류장 인덱스 찾기
        int startIndex = findStationIndexEnhanced(routeStations, startStop);
        int endIndex = findStationIndexEnhanced(routeStations, endStop);

        if (startIndex == -1 || endIndex == -1) {
            Log.w(TAG, "정류장 인덱스 찾기 실패: start=" + startIndex + ", end=" + endIndex);
            return new RouteDirectionInfo(false, "UNKNOWN", "회차 버스", 0);
        }

        // 4. 🔧 핵심 수정: 회차점 기준 구간 분석 및 상호 구간 이동 차단
        SegmentAnalysisResult segmentResult = analyzeRouteSegments(routeStations, startIndex, endIndex, terminalInfo);

        if (!segmentResult.isSameSegment) {
            // 서로 다른 구간(상행↔하행) 간 이동은 불가능
            Log.w(TAG, String.format("🚨 %s번: 상행↔하행 간 이동 차단 - 출발(%s, index:%d, %s구간) → 도착(%s, index:%d, %s구간)",
                    bus.routeno,
                    routeStations.get(startIndex).nodenm, startIndex, segmentResult.startSegmentName,
                    routeStations.get(endIndex).nodenm, endIndex, segmentResult.endSegmentName));

            return new RouteDirectionInfo(false, "회차 구간 간 이동",
                    segmentResult.startSegmentName + "에서 " + segmentResult.endSegmentName + "로 직접 이동 불가", 0);
        }

        // 5. 같은 구간 내 이동인 경우에만 승차 허용
        Log.i(TAG, String.format("✅ %s번: 동일 구간 내 이동 확인 - %s구간 (%s → %s)",
                bus.routeno, segmentResult.startSegmentName,
                routeStations.get(startIndex).nodenm, routeStations.get(endIndex).nodenm));

        // 6. 방향 분석
        DirectionAnalysisResult directionResult = analyzeDirection(routeStations, startIndex, endIndex, terminalInfo);

        return new RouteDirectionInfo(true, segmentResult.startSegmentName,
                directionResult.directionDescription, 85,
                directionResult.destinationName, directionResult.isForwardDirection);
    }

    /**
     * 🔧 새로운 메서드: 회차점 기준 구간 분석
     */
    private static SegmentAnalysisResult analyzeRouteSegments(
            List<TagoBusRouteStationResponse.RouteStation> routeStations,
            int startIndex, int endIndex, TerminalInfo terminalInfo) {

        SegmentAnalysisResult result = new SegmentAnalysisResult();

        // 주요 회차점 찾기 (보통 중간 지점에 위치)
        int primaryTerminalIndex = findPrimaryTerminalIndex(terminalInfo, routeStations.size());

        if (primaryTerminalIndex == -1) {
            // 회차점이 없는 경우 - 단순 직선 노선
            result.isSameSegment = startIndex <= endIndex; // 순방향만 허용
            result.startSegmentName = "직선구간";
            result.endSegmentName = "직선구간";
            return result;
        }

        // 🔧 핵심: 회차점 기준으로 상행/하행 구간 판정
        String startSegment = (startIndex <= primaryTerminalIndex) ? "상행" : "하행";
        String endSegment = (endIndex <= primaryTerminalIndex) ? "상행" : "하행";

        result.startSegmentName = startSegment;
        result.endSegmentName = endSegment;
        result.isSameSegment = startSegment.equals(endSegment);
        result.primaryTerminalIndex = primaryTerminalIndex;

        Log.d(TAG, String.format("구간 분석: 출발(%d→%s), 도착(%d→%s), 회차점(%d), 동일구간: %b",
                startIndex, startSegment, endIndex, endSegment, primaryTerminalIndex, result.isSameSegment));

        return result;
    }

    /**
     * 주요 회차점 인덱스 찾기
     */
    private static int findPrimaryTerminalIndex(TerminalInfo terminalInfo, int totalStations) {
        if (terminalInfo.middleTerminals.isEmpty()) {
            return -1;
        }

        // 중간 회차점 중에서 가장 중앙에 위치한 것을 선택
        int bestIndex = -1;
        double bestCentrality = Double.MAX_VALUE;

        for (TerminalPoint terminal : terminalInfo.middleTerminals) {
            double centrality = Math.abs((double)terminal.index / totalStations - 0.5);
            if (centrality < bestCentrality) {
                bestCentrality = centrality;
                bestIndex = terminal.index;
            }
        }

        return bestIndex;
    }

    /**
     * 방향 분석 결과를 담는 클래스
     */
    private static class DirectionAnalysisResult {
        boolean isForwardDirection = true;
        String currentSegment = "상행";
        String destinationName = "목적지";
        String directionDescription = "목적지방면 (상행)";
    }

    /**
     * 구간 분석 결과를 담는 클래스
     */
    private static class SegmentAnalysisResult {
        boolean isSameSegment = false;
        String startSegmentName = "알수없음";
        String endSegmentName = "알수없음";
        int primaryTerminalIndex = -1;
    }

    /**
     * 방향 분석
     */
    private static DirectionAnalysisResult analyzeDirection(
            List<TagoBusRouteStationResponse.RouteStation> routeStations,
            int startIndex, int endIndex, TerminalInfo terminalInfo) {

        DirectionAnalysisResult result = new DirectionAnalysisResult();

        int primaryTerminalIndex = findPrimaryTerminalIndex(terminalInfo, routeStations.size());

        if (primaryTerminalIndex == -1) {
            // 회차점이 없는 직선 노선
            result.isForwardDirection = startIndex < endIndex;
            result.currentSegment = "직선구간";
            result.destinationName = extractDirectionFromStationName(
                    routeStations.get(routeStations.size() - 1).nodenm);
            result.directionDescription = result.destinationName + "방면";
        } else {
            // 회차 노선
            if (startIndex <= primaryTerminalIndex && endIndex <= primaryTerminalIndex) {
                // 상행 구간
                result.isForwardDirection = true;
                result.currentSegment = "상행";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(primaryTerminalIndex).nodenm);
                result.directionDescription = result.destinationName + "방면 (상행)";
            } else {
                // 하행 구간 (API 인덱스 순서대로 진행)
                result.isForwardDirection = true;  // API 순서로는 정방향
                result.currentSegment = "하행";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(0).nodenm);
                result.directionDescription = result.destinationName + "방면 (하행)";
            }
        }

        return result;
    }

    // ================================================================================================
    // 지원 메서드들 (기존 유지)
    // ================================================================================================

    /**
     * 회차점 정보를 담는 클래스
     */
    private static class TerminalInfo {
        List<TerminalPoint> middleTerminals = new ArrayList<>();
        TerminalPoint startTerminal;
        TerminalPoint endTerminal;

        @Override
        public String toString() {
            return String.format("시점:%s(%d), 종점:%s(%d), 중간회차:%d개",
                    startTerminal != null ? startTerminal.name : "없음",
                    startTerminal != null ? startTerminal.index : -1,
                    endTerminal != null ? endTerminal.name : "없음",
                    endTerminal != null ? endTerminal.index : -1,
                    middleTerminals.size());
        }
    }

    /**
     * 회차점을 나타내는 클래스
     */
    private static class TerminalPoint {
        String name;
        int index;

        TerminalPoint(String name, int index) {
            this.name = name;
            this.index = index;
        }
    }

    /**
     * 회차점/종점 분석
     */
    private static TerminalInfo analyzeTerminals(List<TagoBusRouteStationResponse.RouteStation> routeStations) {
        TerminalInfo info = new TerminalInfo();

        // 시작점과 끝점 설정
        if (!routeStations.isEmpty()) {
            info.startTerminal = new TerminalPoint(routeStations.get(0).nodenm, 0);
            info.endTerminal = new TerminalPoint(
                    routeStations.get(routeStations.size() - 1).nodenm,
                    routeStations.size() - 1);
        }

        // 중간 회차점 찾기 (종점, 터미널 등이 포함된 정류장명)
        for (int i = 1; i < routeStations.size() - 1; i++) {
            String stationName = routeStations.get(i).nodenm;
            if (isTerminalStation(stationName)) {
                info.middleTerminals.add(new TerminalPoint(stationName, i));
                Log.d(TAG, "중간 회차점 발견: " + stationName + " (인덱스: " + i + ")");
            }
        }

        return info;
    }

    /**
     * 회차점 여부 판단
     */
    private static boolean isTerminalStation(String stationName) {
        if (stationName == null) return false;

        String[] terminalKeywords = {"종점", "터미널", "종착", "차고지", "기점", "회차"};
        String lowerName = stationName.toLowerCase();

        for (String keyword : terminalKeywords) {
            if (lowerName.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 향상된 정류장 인덱스 찾기
     */
    private static int findStationIndexEnhanced(List<TagoBusRouteStationResponse.RouteStation> routeStations,
                                                TagoBusStopResponse.BusStop targetStop) {

        // 1차: 정류장 ID 정확 매칭
        for (int i = 0; i < routeStations.size(); i++) {
            TagoBusRouteStationResponse.RouteStation station = routeStations.get(i);
            if (station.nodeid != null && station.nodeid.equals(targetStop.nodeid)) {
                Log.v(TAG, "정류장 매칭 성공 (ID): " + targetStop.nodenm + " at index " + i);
                return i;
            }
        }

        // 2차: 정류장명 정확 매칭
        for (int i = 0; i < routeStations.size(); i++) {
            TagoBusRouteStationResponse.RouteStation station = routeStations.get(i);
            if (station.nodenm != null && targetStop.nodenm != null) {
                if (station.nodenm.equals(targetStop.nodenm)) {
                    Log.v(TAG, "정류장 매칭 성공 (이름): " + targetStop.nodenm + " at index " + i);
                    return i;
                }
            }
        }

        // 3차: 정류장명 정규화 매칭 (더 관대한 매칭)
        String normalizedTargetName = normalizeStopName(targetStop.nodenm);
        for (int i = 0; i < routeStations.size(); i++) {
            TagoBusRouteStationResponse.RouteStation station = routeStations.get(i);
            if (station.nodenm != null) {
                String normalizedStationName = normalizeStopName(station.nodenm);
                if (normalizedStationName.equals(normalizedTargetName)) {
                    Log.v(TAG, "정류장 매칭 성공 (정규화): " + targetStop.nodenm +
                            " -> " + station.nodenm + " at index " + i);
                    return i;
                }
            }
        }

        // 4차: 부분 문자열 매칭 (핵심 키워드가 포함된 경우)
        if (targetStop.nodenm != null && targetStop.nodenm.length() >= 2) {
            String targetCore = targetStop.nodenm.replaceAll("정류장|정류소|버스정류장|앞|뒤|입구|출구", "").trim();
            for (int i = 0; i < routeStations.size(); i++) {
                TagoBusRouteStationResponse.RouteStation station = routeStations.get(i);
                if (station.nodenm != null) {
                    String stationCore = station.nodenm.replaceAll("정류장|정류소|버스정류장|앞|뒤|입구|출구", "").trim();
                    // 핵심 키워드가 포함되어 있는지 확인
                    if (targetCore.length() >= 2 && stationCore.contains(targetCore)) {
                        Log.v(TAG, "정류장 매칭 성공 (키워드): " + targetStop.nodenm +
                                " -> " + station.nodenm + " at index " + i);
                        return i;
                    }
                    if (stationCore.length() >= 2 && targetCore.contains(stationCore)) {
                        Log.v(TAG, "정류장 매칭 성공 (키워드 역): " + targetStop.nodenm +
                                " -> " + station.nodenm + " at index " + i);
                        return i;
                    }
                }
            }
        }

        // 5차: 좌표 기반 근접 매칭 (반경을 점진적으로 확대)
        int[] radiusOptions = {50, 100, 200, 500}; // 점진적 확대
        for (int radius : radiusOptions) {
            for (int i = 0; i < routeStations.size(); i++) {
                TagoBusRouteStationResponse.RouteStation station = routeStations.get(i);
                if (station.gpslati > 0 && station.gpslong > 0 &&
                        targetStop.gpslati > 0 && targetStop.gpslong > 0) {
                    double distance = calculateDistance(
                            station.gpslati, station.gpslong,
                            targetStop.gpslati, targetStop.gpslong
                    );
                    if (distance <= radius) {
                        Log.v(TAG, "정류장 매칭 성공 (좌표 " + radius + "m): " + targetStop.nodenm +
                                " -> " + station.nodenm + " at index " + i + " (거리: " + (int)distance + "m)");
                        return i;
                    }
                }
            }
        }

        // 매칭 실패
        Log.w(TAG, "정류장 매칭 실패: " + targetStop.nodenm +
                " (ID: " + targetStop.nodeid + ", 좌표: " + targetStop.gpslati + "," + targetStop.gpslong + ")");
        return -1;
    }

    /**
     * 정류장명 정규화
     */
    private static String normalizeStopName(String stopName) {
        if (stopName == null) return "";

        return stopName.replaceAll("\\s+", "")  // 공백 제거
                .replaceAll("\\([^)]*\\)", "")  // 괄호와 내용 제거
                .replaceAll("[\\.,·]", "")  // 구두점 제거
                .toLowerCase()  // 소문자 변환
                .trim();
    }

    /**
     * 두 좌표 간 거리 계산 (미터 단위)
     */
    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // 지구 반지름 (미터)

        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat/2) * Math.sin(deltaLat/2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLon/2) * Math.sin(deltaLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return R * c;
    }

    /**
     * 정류장명에서 방향 정보 추출
     */
    private static String extractDirectionFromStationName(String stationName) {
        if (stationName == null) return "목적지";

        // 괄호 제거 및 정리
        String cleaned = stationName.replaceAll("\\([^)]*\\)", "").trim();

        // 너무 긴 경우 일부만 사용
        if (cleaned.length() > 6) {
            cleaned = cleaned.substring(0, 6);
        }

        return cleaned.isEmpty() ? "목적지" : cleaned;
    }
}