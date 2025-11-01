// app/src/main/java/com/sjoneon/cap/utils/BusDirectionAnalyzer.java

package com.sjoneon.cap.utils;

import android.util.Log;

import com.sjoneon.cap.models.api.TagoBusArrivalResponse;
import com.sjoneon.cap.models.api.TagoBusRouteStationResponse;
import com.sjoneon.cap.models.api.TagoBusStopResponse;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

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
        public boolean isValidDirection;
        public String currentSegment;
        public String directionDescription;
        public int confidence;
        public String correctDestination;
        public boolean isForwardDirection;

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
     * 종합적인 회차 방향 분석
     */
    public static RouteDirectionInfo analyzeRouteDirection(
            TagoBusStopResponse.BusStop startStop,
            TagoBusStopResponse.BusStop endStop,
            TagoBusArrivalResponse.BusArrival bus,
            List<TagoBusRouteStationResponse.RouteStation> routeStations) {

        Log.d(TAG, bus.routeno + "번 버스 회차 방향 분석 시작");

        if (routeStations == null || routeStations.isEmpty()) {
            return new RouteDirectionInfo(false, "UNKNOWN", "노선 정보 없음", 0);
        }

        // 회차점/종점 분석
        TerminalInfo terminalInfo = analyzeTerminals(routeStations);
        Log.d(TAG, "회차점 분석: " + terminalInfo.toString());

        // 정류장 인덱스 찾기
        int startIndex = findStationIndexEnhanced(routeStations, startStop);
        int endIndex = findStationIndexEnhanced(routeStations, endStop);

        if (startIndex == -1 || endIndex == -1) {
            Log.w(TAG, "정류장 인덱스 찾기 실패: start=" + startIndex + ", end=" + endIndex);
            return new RouteDirectionInfo(false, "UNKNOWN", "회차 버스", 0);
        }

        // 현재 운행 방향 분석
        DirectionAnalysisResult directionResult = analyzeCurrentDirectionEnhanced(
                startIndex, endIndex, terminalInfo, routeStations, bus.routeno);

        // 다중 방향 분석
        List<DirectionAnalysis> analyses = new ArrayList<>();
        analyses.add(analyzeByBasicDirection(startIndex, endIndex, routeStations));
        analyses.add(analyzeByTerminalPositionFixed(startIndex, endIndex, terminalInfo, routeStations, directionResult));
        analyses.add(analyzeByStationOrder(startIndex, endIndex, routeStations));
        analyses.add(analyzeByCoordinatesFixed(startStop, endStop, routeStations, startIndex, endIndex));

        // 종합 판정
        return synthesizeAnalysisEnhanced(analyses, bus.routeno, directionResult);
    }

    /**
     * 회차점/종점 분석 (중복 제거 로직 적용)
     */
    private static TerminalInfo analyzeTerminals(List<TagoBusRouteStationResponse.RouteStation> routeStations) {
        TerminalInfo terminalInfo = new TerminalInfo();

        if (routeStations.isEmpty()) return terminalInfo;

        terminalInfo.startTerminal = new TerminalPoint(routeStations.get(0).nodenm, 0);
        terminalInfo.endTerminal = new TerminalPoint(
                routeStations.get(routeStations.size() - 1).nodenm,
                routeStations.size() - 1);

        // 같은 회차점이 연속으로 나타나는 경우 방지
        Set<String> addedTerminals = new HashSet<>();

        for (int i = 1; i < routeStations.size() - 1; i++) {
            String stationName = routeStations.get(i).nodenm;

            // 이미 추가한 회차점은 스킵
            if (addedTerminals.contains(stationName)) {
                continue;
            }

            if (stationName.contains("종점") || stationName.contains("터미널") ||
                    stationName.contains("차고지") || stationName.contains("공영차고")) {
                terminalInfo.middleTerminals.add(new TerminalPoint(stationName, i));
                addedTerminals.add(stationName);
                Log.d(TAG, "중간 회차점 발견: " + stationName + " (인덱스: " + i + ")");
            }
        }

        return terminalInfo;
    }

    /**
     * 현재 운행 방향 분석
     */
    private static DirectionAnalysisResult analyzeCurrentDirectionEnhanced(
            int startIndex, int endIndex, TerminalInfo terminalInfo,
            List<TagoBusRouteStationResponse.RouteStation> routeStations, String busNumber) {

        DirectionAnalysisResult result = new DirectionAnalysisResult();

        int totalStations = routeStations.size();
        int midPoint = totalStations / 2;

        if (!terminalInfo.middleTerminals.isEmpty()) {
            TerminalPoint firstTerminal = terminalInfo.middleTerminals.get(0);
            int terminalIndex = firstTerminal.index;

            if (startIndex < terminalIndex && endIndex < terminalIndex) {
                // 첫 번째 구간 (시점→회차점)
                result.isForwardDirection = true;
                result.currentSegment = "전반부";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(terminalIndex).nodenm);
                result.directionDescription = result.destinationName + "방면 (상행)";

            } else if (startIndex > terminalIndex && endIndex > terminalIndex) {
                // 두 번째 구간 (회차점→종점)
                result.isForwardDirection = false;
                result.currentSegment = "후반부";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(routeStations.size() - 1).nodenm);
                result.directionDescription = result.destinationName + "방면 (하행)";

            } else {
                // 회차 구간 (startIndex와 endIndex가 회차점을 넘어감)
                result.isForwardDirection = false;
                result.currentSegment = "회차 구간";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(terminalIndex).nodenm);
                result.directionDescription = "회차 대기 필요";
            }
        } else {
            // 회차점이 없는 경우
            result.isForwardDirection = startIndex < endIndex;
            result.currentSegment = result.isForwardDirection ? "전반부" : "후반부";
            result.destinationName = extractDirectionFromStationName(
                    routeStations.get(startIndex < endIndex ? routeStations.size() - 1 : 0).nodenm);
            result.directionDescription = result.destinationName + "방면 (" +
                    (startIndex < endIndex ? "상행" : "하행") + ")";
        }

        return result;
    }

    /**
     * 회차점 위치 기반 분석
     */
    private static DirectionAnalysis analyzeByTerminalPositionFixed(
            int startIndex, int endIndex, TerminalInfo terminalInfo,
            List<TagoBusRouteStationResponse.RouteStation> routeStations,
            DirectionAnalysisResult directionResult) {

        DirectionAnalysis analysis = new DirectionAnalysis("TERMINAL_POSITION");

        try {
            if (directionResult.currentSegment.contains("후반부") ||
                    directionResult.currentSegment.contains("회차 구간") ||
                    !directionResult.isForwardDirection) {

                analysis.isValid = false;
                analysis.segment = "회차 대기 필요";
                analysis.confidence = 85;
                analysis.reason = "회차 구간에서 목적지 도달 불가";

                Log.w(TAG, String.format("회차 구간 판정: %s → %s, 정류장 순서: %d → %d",
                        routeStations.get(startIndex).nodenm, routeStations.get(endIndex).nodenm,
                        startIndex, endIndex));

            } else {
                analysis.isValid = startIndex < endIndex;
                analysis.segment = directionResult.currentSegment;
                analysis.confidence = 80;
                analysis.reason = "회차점 기반 정방향 운행 확인";
            }

        } catch (Exception e) {
            Log.e(TAG, "회차점 분석 실패", e);
            analysis.isValid = false;
            analysis.confidence = 0;
            analysis.reason = "회차점 분석 오류";
        }

        return analysis;
    }

    /**
     * 기본 방향 정보 기반 분석
     */
    private static DirectionAnalysis analyzeByBasicDirection(
            int startIndex, int endIndex,
            List<TagoBusRouteStationResponse.RouteStation> routeStations) {

        DirectionAnalysis analysis = new DirectionAnalysis("BASIC_DIRECTION");

        try {
            boolean isForward = startIndex < endIndex;

            analysis.isValid = isForward;
            analysis.confidence = 40;
            analysis.segment = isForward ? "전반부" : "후반부";
            analysis.reason = "기본 순서 기반 판정";

        } catch (Exception e) {
            Log.e(TAG, "기본 방향 분석 실패", e);
            analysis.isValid = false;
            analysis.confidence = 0;
            analysis.reason = "기본 방향 분석 오류";
        }

        return analysis;
    }

    /**
     * 정류장 순서 기반 분석
     */
    private static DirectionAnalysis analyzeByStationOrder(
            int startIndex, int endIndex,
            List<TagoBusRouteStationResponse.RouteStation> routeStations) {

        DirectionAnalysis analysis = new DirectionAnalysis("STATION_ORDER");

        try {
            boolean isValid = startIndex < endIndex;

            analysis.isValid = isValid;
            analysis.segment = isValid ? "정방향" : "역방향";
            analysis.confidence = 70;
            analysis.reason = "정류장 순서 기반 판정";

        } catch (Exception e) {
            Log.e(TAG, "정류장 순서 분석 실패", e);
            analysis.isValid = false;
            analysis.confidence = 0;
            analysis.reason = "정류장 순서 분석 오류";
        }

        return analysis;
    }

    /**
     * 좌표 기반 분석
     */
    private static DirectionAnalysis analyzeByCoordinatesFixed(
            TagoBusStopResponse.BusStop startStop,
            TagoBusStopResponse.BusStop endStop,
            List<TagoBusRouteStationResponse.RouteStation> routeStations,
            int startIndex, int endIndex) {

        DirectionAnalysis analysis = new DirectionAnalysis("COORDINATES");

        try {
            if (startIndex == -1 || endIndex == -1) {
                analysis.isValid = false;
                analysis.confidence = 0;
                analysis.reason = "좌표 정보 부족";
                return analysis;
            }

            double totalDistance = 0;
            for (int i = startIndex; i < endIndex && i < routeStations.size() - 1; i++) {
                TagoBusRouteStationResponse.RouteStation current = routeStations.get(i);
                TagoBusRouteStationResponse.RouteStation next = routeStations.get(i + 1);

                double distance = calculateDistance(
                        current.gpslati, current.gpslong,
                        next.gpslati, next.gpslong
                );
                totalDistance += distance;
            }

            analysis.isValid = totalDistance > 0;
            analysis.confidence = 60;
            analysis.segment = "좌표 기반";
            analysis.reason = String.format("경로 거리: %.1fm", totalDistance);

        } catch (Exception e) {
            Log.e(TAG, "좌표 분석 실패", e);
            analysis.isValid = false;
            analysis.confidence = 0;
            analysis.reason = "좌표 분석 오류";
        }

        return analysis;
    }

    /**
     * 종합 판정
     */
    private static RouteDirectionInfo synthesizeAnalysisEnhanced(
            List<DirectionAnalysis> analyses, String busNumber,
            DirectionAnalysisResult directionResult) {

        int totalConfidence = 0;
        int validCount = 0;
        int invalidCount = 0;
        StringBuilder reasonBuilder = new StringBuilder();

        for (DirectionAnalysis analysis : analyses) {
            if (analysis.isValid) {
                validCount++;
                totalConfidence += analysis.confidence;
            } else {
                invalidCount++;
            }
            reasonBuilder.append(analysis.method).append(":").append(analysis.isValid ? "승차가능" : "회차대기").append(", ");
        }

        boolean finalValid = validCount > invalidCount;
        int finalConfidence = analyses.isEmpty() ? 0 : totalConfidence / analyses.size();
        String finalSegment = directionResult.currentSegment;
        String finalDescription = directionResult.directionDescription;

        if (directionResult.currentSegment.contains("회차")) {
            finalValid = false;
            finalDescription = "회차 후 " + directionResult.directionDescription;
        }

        Log.i(TAG, String.format("%s번 버스 종합 판정: %s (신뢰도: %d%%, 사유: %s)",
                busNumber, finalValid ? "승차가능" : "회차대기", finalConfidence, reasonBuilder.toString()));

        return new RouteDirectionInfo(finalValid, finalSegment, finalDescription, finalConfidence,
                directionResult.destinationName, directionResult.isForwardDirection);
    }

    /**
     * 개선된 정류장 인덱스 찾기 (ID, 이름, 좌표 기반)
     */
    private static int findStationIndexEnhanced(List<TagoBusRouteStationResponse.RouteStation> routeStations,
                                                TagoBusStopResponse.BusStop targetStop) {
        Log.d(TAG, "=== 정류장 인덱스 찾기 시작 ===");
        Log.d(TAG, "목표: " + targetStop.nodenm + " (ID: " + targetStop.nodeid + ")");
        Log.d(TAG, "좌표: lat=" + targetStop.gpslati + ", lng=" + targetStop.gpslong);
        Log.d(TAG, "노선 정류장 수: " + routeStations.size());

        // 1단계: ID로 정확 매칭
        Log.d(TAG, "1단계: ID 매칭 시도");
        for (int i = 0; i < routeStations.size(); i++) {
            if (routeStations.get(i).nodeid != null &&
                    routeStations.get(i).nodeid.equals(targetStop.nodeid)) {
                Log.d(TAG, "✅ ID 매칭 성공: " + routeStations.get(i).nodenm + " (인덱스: " + i + ")");
                return i;
            }
        }
        Log.d(TAG, "ID 매칭 실패");

        // 2단계: 이름 유사도 매칭
        Log.d(TAG, "2단계: 이름 매칭 시도");
        for (int i = 0; i < routeStations.size(); i++) {
            String stationName = routeStations.get(i).nodenm;
            if (stationName != null && targetStop.nodenm != null) {
                if (stationName.contains(targetStop.nodenm) || targetStop.nodenm.contains(stationName)) {
                    Log.d(TAG, "✅ 이름 매칭 성공: " + stationName + " ≈ " + targetStop.nodenm + " (인덱스: " + i + ")");
                    return i;
                }
            }
        }
        Log.d(TAG, "이름 매칭 실패");

        // 3단계: 좌표 기반 매칭 (50m 이내)
        Log.d(TAG, "3단계: 좌표 매칭 시도 (50m 이내)");

        // 목표 정류장 좌표 체크
        if (targetStop.gpslati == 0 || targetStop.gpslong == 0) {
            Log.w(TAG, "❌ 목표 정류장 좌표가 0 → 좌표 매칭 불가");
            return -1;
        }

        int validCoordCount = 0;
        double minDistance = Double.MAX_VALUE;
        String closestStation = "";

        for (int i = 0; i < routeStations.size(); i++) {
            TagoBusRouteStationResponse.RouteStation station = routeStations.get(i);

            // 좌표가 유효한지 확인
            if (station.gpslati == 0 || station.gpslong == 0) {
                continue;
            }

            validCoordCount++;

            double distance = calculateDistance(
                    targetStop.gpslati, targetStop.gpslong,
                    station.gpslati, station.gpslong
            );

            // 최단거리 추적
            if (distance < minDistance) {
                minDistance = distance;
                closestStation = station.nodenm;
            }

            // 200m 이내는 로그 출력
            if (distance <= 200) {
                Log.d(TAG, String.format("  근처: %s (%.1fm, 인덱스: %d)",
                        station.nodenm, distance, i));
            }

            if (distance <= 50) {
                Log.d(TAG, String.format("✅ 좌표 매칭 성공: %s (%.1fm, 인덱스: %d)",
                        station.nodenm, distance, i));
                return i;
            }
        }

        Log.w(TAG, String.format("좌표 유효 정류장: %d개, 최단거리: %s (%.1fm)",
                validCoordCount, closestStation, minDistance));
        Log.w(TAG, "❌ 모든 매칭 실패");
        return -1;
    }

    /**
     * 정류장 이름에서 방향 정보 추출
     */
    private static String extractDirectionFromStationName(String stationName) {
        if (stationName.contains("종점")) {
            return stationName.replace("종점", "").trim();
        }
        if (stationName.contains("터미널")) {
            return stationName;
        }
        return stationName;
    }

    /**
     * 두 지점 간 거리 계산 (Haversine)
     */
    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS = 6371000; // 미터 단위

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    // 지원 클래스들
    private static class DirectionAnalysisResult {
        boolean isForwardDirection = true;
        String currentSegment = "전반부";
        String destinationName = "목적지";
        String directionDescription = "목적지방면 (상행)";
    }

    private static class DirectionAnalysis {
        String method;
        boolean isValid;
        int confidence;
        String segment;
        String reason;

        DirectionAnalysis(String method) {
            this.method = method;
        }
    }

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

    private static class TerminalPoint {
        String name;
        int index;

        TerminalPoint(String name, int index) {
            this.name = name;
            this.index = index;
        }
    }
}