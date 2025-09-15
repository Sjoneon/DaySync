// app/src/main/java/com/sjoneon/cap/BusDirectionAnalyzer.java

package com.sjoneon.cap;

import android.util.Log;
import java.util.List;
import java.util.ArrayList;

/**
 * 버스 회차 방향을 정확히 분석하는 클래스 (방향 정보 표시 문제 해결 포함)
 * A-B 회차 노선에서 현재 버스가 어느 구간(A→B 또는 B→A)을 운행 중인지 판별
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
        public String correctDestination;      // 🆕 정확한 목적지 정보
        public boolean isForwardDirection;     // 🆕 정방향 여부

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
     * 종합적인 회차 방향 분석 (방향 정보 표시 문제 해결 포함)
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

        // 3. 정류장 인덱스 찾기 (개선된 방식)
        int startIndex = findStationIndexEnhanced(routeStations, startStop);
        int endIndex = findStationIndexEnhanced(routeStations, endStop);

        if (startIndex == -1 || endIndex == -1) {
            Log.w(TAG, "정류장 인덱스 찾기 실패: start=" + startIndex + ", end=" + endIndex);
            return new RouteDirectionInfo(false, "UNKNOWN", "정류장 위치 불명", 0);
        }

        // 4. 🆕 정확한 방향 정보 분석
        DirectionAnalysisResult directionResult = analyzeCurrentDirection(
                startIndex, endIndex, terminalInfo, routeStations, bus.routeno);

        // 5. 다중 방향 분석
        List<DirectionAnalysis> analyses = new ArrayList<>();

        // 5-1. API 방향 정보 기반 분석
        analyses.add(analyzeByApiDirectionInfo(startStop, endStop, bus, routeStations, startIndex, endIndex));

        // 5-2. 회차점 기반 분석 (개선됨)
        analyses.add(analyzeByTerminalPositionEnhanced(startIndex, endIndex, terminalInfo, routeStations, directionResult));

        // 5-3. 정류장 순서 기반 분석
        analyses.add(analyzeByStationOrder(startIndex, endIndex, routeStations));

        // 5-4. 좌표 기반 분석
        analyses.add(analyzeByCoordinates(startStop, endStop, routeStations, startIndex, endIndex));

        // 6. 종합 판정 (방향 정보 포함)
        return synthesizeAnalysisEnhanced(analyses, bus.routeno, directionResult);
    }

    /**
     * 🆕 현재 운행 방향 분석 (방향 정보 표시 문제 해결 핵심)
     */
    private static DirectionAnalysisResult analyzeCurrentDirection(
            int startIndex, int endIndex, TerminalInfo terminalInfo,
            List<TagoBusRouteStationResponse.RouteStation> routeStations, String busNumber) {

        DirectionAnalysisResult result = new DirectionAnalysisResult();

        int totalStations = routeStations.size();
        int midPoint = totalStations / 2;

        // 🔍 회차점이 있는 경우 정확한 방향 판별
        if (!terminalInfo.middleTerminals.isEmpty()) {
            // 중간 회차점을 기준으로 구간 분할
            TerminalPoint firstTerminal = terminalInfo.middleTerminals.get(0);
            int terminalIndex = firstTerminal.index;

            if (startIndex < terminalIndex && endIndex < terminalIndex) {
                // 첫 번째 구간 (A→회차점)
                result.isForwardDirection = true;
                result.currentSegment = "전반부";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(terminalIndex).nodenm);
                result.directionDescription = result.destinationName + "방면 (상행)";

            } else if (startIndex > terminalIndex && endIndex > terminalIndex) {
                // 두 번째 구간 (회차점→A)
                result.isForwardDirection = false;
                result.currentSegment = "후반부";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(0).nodenm);
                result.directionDescription = result.destinationName + "방면 (하행)";

            } else if (startIndex < terminalIndex && endIndex > terminalIndex) {
                // 전체 구간 횡단
                result.isForwardDirection = true;
                result.currentSegment = "전체구간";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(routeStations.size() - 1).nodenm);
                result.directionDescription = result.destinationName + "방면 (상행)";

            } else {
                // 회차점 근처 - 불분명
                result.isForwardDirection = startIndex < endIndex;
                result.currentSegment = "회차점근처";
                result.destinationName = "목적지";
                result.directionDescription = "방향 정보 없음";
            }

            Log.d(TAG, String.format("🎯 %s번 회차점 기반 방향 분석: %s → %s",
                    busNumber, result.currentSegment, result.directionDescription));

        } else {
            // 🔍 회차점이 없는 경우 기존 방식
            double progressRatio = (double) startIndex / (totalStations - 1);

            if (progressRatio < 0.5) {
                result.isForwardDirection = true;
                result.currentSegment = "전반부";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(routeStations.size() - 1).nodenm);
                result.directionDescription = result.destinationName + "방면 (상행)";
            } else {
                result.isForwardDirection = false;
                result.currentSegment = "후반부";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(0).nodenm);
                result.directionDescription = result.destinationName + "방면 (하행)";
            }

            Log.d(TAG, String.format("🎯 %s번 기본 방향 분석: %s → %s",
                    busNumber, result.currentSegment, result.directionDescription));
        }

        return result;
    }

    /**
     * 🆕 개선된 정류장 인덱스 찾기
     */
    private static int findStationIndexEnhanced(List<TagoBusRouteStationResponse.RouteStation> stations,
                                                TagoBusStopResponse.BusStop targetStop) {
        // 1차: 정확한 노드 ID 매칭
        for (int i = 0; i < stations.size(); i++) {
            TagoBusRouteStationResponse.RouteStation station = stations.get(i);
            if (station.nodeid != null && station.nodeid.equals(targetStop.nodeid)) {
                return i;
            }
        }

        // 2차: 정류장명 정확 매칭
        for (int i = 0; i < stations.size(); i++) {
            TagoBusRouteStationResponse.RouteStation station = stations.get(i);
            if (station.nodenm != null && targetStop.nodenm != null) {
                if (station.nodenm.equals(targetStop.nodenm)) {
                    return i;
                }
            }
        }

        // 3차: 정류장명 정규화 매칭
        for (int i = 0; i < stations.size(); i++) {
            TagoBusRouteStationResponse.RouteStation station = stations.get(i);
            if (station.nodenm != null && targetStop.nodenm != null) {
                if (normalizeStopName(station.nodenm).equals(normalizeStopName(targetStop.nodenm))) {
                    return i;
                }
            }
        }

        // 4차: 좌표 기반 근접 매칭 (50m 이내로 더 엄격하게)
        for (int i = 0; i < stations.size(); i++) {
            TagoBusRouteStationResponse.RouteStation station = stations.get(i);
            if (station.gpslati > 0 && station.gpslong > 0) {
                double distance = calculateDistance(
                        station.gpslati, station.gpslong,
                        targetStop.gpslati, targetStop.gpslong
                );
                if (distance <= 50) { // 100m -> 50m로 더 엄격하게
                    return i;
                }
            }
        }

        return -1; // 못 찾음
    }

    /**
     * 회차점/종점 정보 분석
     */
    private static TerminalInfo analyzeTerminals(List<TagoBusRouteStationResponse.RouteStation> stations) {
        TerminalInfo info = new TerminalInfo();

        // 첫 번째와 마지막 정류장을 종점으로 가정
        if (!stations.isEmpty()) {
            TagoBusRouteStationResponse.RouteStation firstStation = stations.get(0);
            TagoBusRouteStationResponse.RouteStation lastStation = stations.get(stations.size() - 1);

            info.startTerminal = firstStation.nodenm;
            info.endTerminal = lastStation.nodenm;
            info.startTerminalIndex = 0;
            info.endTerminalIndex = stations.size() - 1;

            // 중간 회차점 찾기 (이름에 "종점", "터미널", "차고지" 포함)
            for (int i = 1; i < stations.size() - 1; i++) {
                String stationName = stations.get(i).nodenm;
                if (stationName != null && (
                        stationName.contains("종점") ||
                                stationName.contains("터미널") ||
                                stationName.contains("차고지") ||
                                stationName.contains("회차"))) {
                    info.middleTerminals.add(new TerminalPoint(stationName, i));
                    Log.d(TAG, "중간 회차점 발견: " + stationName + " (인덱스: " + i + ")");
                }
            }
        }

        return info;
    }

    /**
     * API 방향 정보 기반 분석
     */
    private static DirectionAnalysis analyzeByApiDirectionInfo(
            TagoBusStopResponse.BusStop startStop,
            TagoBusStopResponse.BusStop endStop,
            TagoBusArrivalResponse.BusArrival bus,
            List<TagoBusRouteStationResponse.RouteStation> routeStations,
            int startIndex, int endIndex) {

        DirectionAnalysis analysis = new DirectionAnalysis("API_DIRECTION");

        try {
            // 출발 정류장의 방향 정보 확인
            TagoBusRouteStationResponse.RouteStation startStation = routeStations.get(startIndex);

            String directionInfo = startStation.getDirectionInfo();
            Boolean isUpDirection = startStation.isUpDirection();

            Log.d(TAG, String.format("API 방향 정보: direction=%s, updown=%s, updowncd=%s",
                    directionInfo, startStation.updown, startStation.updowncd));

            // 상행/하행 정보가 있는 경우
            if (isUpDirection != null) {
                boolean expectForward = startIndex < endIndex;
                boolean apiSaysForward = isUpDirection; // 상행이면 정방향으로 가정

                if (expectForward == apiSaysForward) {
                    analysis.isValid = true;
                    analysis.confidence = 80;
                    analysis.segment = isUpDirection ? "정방향(상행)" : "역방향(하행)";
                    analysis.reason = "API 방향 정보와 정류장 순서 일치";
                } else {
                    analysis.isValid = false;
                    analysis.confidence = 70;
                    analysis.segment = "회차 구간 불일치";
                    analysis.reason = "API 방향 정보와 정류장 순서 불일치";
                }
            } else {
                // 방향 정보가 불분명한 경우
                analysis.isValid = true;  // 일단 허용
                analysis.confidence = 30;
                analysis.segment = "방향 정보 불명";
                analysis.reason = "API에서 명확한 방향 정보 없음";
            }

        } catch (Exception e) {
            Log.e(TAG, "API 방향 정보 분석 실패", e);
            analysis.isValid = false;
            analysis.confidence = 0;
            analysis.reason = "API 방향 정보 분석 오류";
        }

        return analysis;
    }

    /**
     * 🆕 개선된 회차점 위치 기반 분석
     */
    private static DirectionAnalysis analyzeByTerminalPositionEnhanced(
            int startIndex, int endIndex, TerminalInfo terminalInfo,
            List<TagoBusRouteStationResponse.RouteStation> routeStations,
            DirectionAnalysisResult directionResult) {

        DirectionAnalysis analysis = new DirectionAnalysis("TERMINAL_POSITION");

        try {
            // 방향 분석 결과를 기반으로 판정
            if (directionResult.isForwardDirection) {
                analysis.isValid = startIndex < endIndex;
                analysis.segment = directionResult.currentSegment;
                analysis.confidence = 80;
                analysis.reason = "회차점 기반 정방향 운행 확인";
            } else {
                analysis.isValid = startIndex < endIndex; // 여전히 순서상 앞으로 가야 함
                analysis.segment = directionResult.currentSegment;
                analysis.confidence = 75;
                analysis.reason = "회차점 기반 역방향 운행 확인";
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
     * 정류장 순서 기반 분석
     */
    private static DirectionAnalysis analyzeByStationOrder(
            int startIndex, int endIndex,
            List<TagoBusRouteStationResponse.RouteStation> routeStations) {

        DirectionAnalysis analysis = new DirectionAnalysis("STATION_ORDER");

        boolean isForward = startIndex < endIndex;
        analysis.isValid = isForward;
        analysis.confidence = 60; // 중간 신뢰도
        analysis.segment = isForward ? "순방향" : "역방향";
        analysis.reason = String.format("정류장 순서: %d → %d", startIndex, endIndex);

        return analysis;
    }

    /**
     * 좌표 기반 분석
     */
    private static DirectionAnalysis analyzeByCoordinates(
            TagoBusStopResponse.BusStop startStop,
            TagoBusStopResponse.BusStop endStop,
            List<TagoBusRouteStationResponse.RouteStation> routeStations,
            int startIndex, int endIndex) {

        DirectionAnalysis analysis = new DirectionAnalysis("COORDINATES");

        try {
            // 좌표 기반 거리 및 방향 계산
            double distance = calculateDistance(
                    startStop.gpslati, startStop.gpslong,
                    endStop.gpslati, endStop.gpslong
            );

            // 노선 경로와 직선 거리 비교
            double routeDistance = calculateRouteDistance(routeStations, startIndex, endIndex);
            double ratio = routeDistance / distance;

            // 비율이 너무 크면 회차를 통한 우회 경로일 가능성
            if (ratio > 2.5) { // 2.0 -> 2.5로 더 관대하게 조정
                analysis.isValid = false;
                analysis.confidence = 40;
                analysis.segment = "우회 경로";
                analysis.reason = String.format("경로/직선 거리 비율: %.2f (우회 의심)", ratio);
            } else {
                analysis.isValid = true;
                analysis.confidence = 50;
                analysis.segment = "직진 경로";
                analysis.reason = String.format("경로/직선 거리 비율: %.2f (정상)", ratio);
            }

        } catch (Exception e) {
            Log.e(TAG, "좌표 기반 분석 실패", e);
            analysis.isValid = true; // 기본값으로 허용
            analysis.confidence = 20;
            analysis.reason = "좌표 분석 오류";
        }

        return analysis;
    }

    /**
     * 🆕 여러 분석 결과를 종합하여 최종 판정 (방향 정보 포함)
     */
    private static RouteDirectionInfo synthesizeAnalysisEnhanced(List<DirectionAnalysis> analyses,
                                                                 String busNumber, DirectionAnalysisResult directionResult) {
        int totalConfidence = 0;
        int validCount = 0;
        int invalidCount = 0;
        StringBuilder reasonBuilder = new StringBuilder();

        for (DirectionAnalysis analysis : analyses) {
            totalConfidence += analysis.confidence;
            if (analysis.isValid) {
                validCount++;
            } else {
                invalidCount++;
            }

            reasonBuilder.append(String.format("[%s: %s, 신뢰도:%d%%] ",
                    analysis.method, analysis.reason, analysis.confidence));
        }

        // 종합 판정 로직
        boolean finalValid;
        String finalSegment;
        String finalDescription;
        int finalConfidence = totalConfidence / analyses.size();

        if (validCount > invalidCount) {
            finalValid = true;
            finalSegment = "승차 가능";
            finalDescription = directionResult.directionDescription; // 🆕 정확한 방향 정보 사용
        } else if (invalidCount > validCount) {
            finalValid = false;
            finalSegment = "회차 대기";
            finalDescription = "회차 후 " + directionResult.directionDescription;
        } else {
            // 동점인 경우 신뢰도 높은 분석 우선
            DirectionAnalysis highest = analyses.stream()
                    .max((a, b) -> Integer.compare(a.confidence, b.confidence))
                    .orElse(analyses.get(0));

            finalValid = highest.isValid;
            finalSegment = highest.segment;
            finalDescription = finalValid ? directionResult.directionDescription :
                    "회차 후 " + directionResult.directionDescription;
        }

        Log.i(TAG, String.format("🎯 %s번 버스 종합 판정: %s (신뢰도: %d%%, 사유: %s)",
                busNumber, finalValid ? "승차가능" : "회차대기", finalConfidence, reasonBuilder.toString()));

        return new RouteDirectionInfo(finalValid, finalSegment, finalDescription, finalConfidence,
                directionResult.destinationName, directionResult.isForwardDirection);
    }

    // ===== 유틸리티 메서드들 =====

    private static String normalizeStopName(String name) {
        if (name == null) return "";
        return name.replaceAll("[\\s·.-]", "").toLowerCase();
    }

    private static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6371000; // 지구 반지름 (미터)

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private static double calculateRouteDistance(List<TagoBusRouteStationResponse.RouteStation> stations,
                                                 int startIndex, int endIndex) {
        double totalDistance = 0;

        for (int i = startIndex; i < endIndex && i < stations.size() - 1; i++) {
            TagoBusRouteStationResponse.RouteStation current = stations.get(i);
            TagoBusRouteStationResponse.RouteStation next = stations.get(i + 1);

            if (current.gpslati > 0 && current.gpslong > 0 &&
                    next.gpslati > 0 && next.gpslong > 0) {
                totalDistance += calculateDistance(
                        current.gpslati, current.gpslong,
                        next.gpslati, next.gpslong
                );
            }
        }

        return totalDistance;
    }

    /**
     * 🆕 정류장 이름에서 방향 정보를 추출 (개선됨)
     */
    private static String extractDirectionFromStationName(String stationName) {
        if (stationName == null || stationName.trim().isEmpty()) {
            return "목적지";
        }

        // 터미널, 역, 대학교 등 주요 목적지 추출
        if (stationName.contains("터미널")) {
            return stationName.replaceAll("정류장|정류소", "").trim();
        }
        if (stationName.contains("역")) {
            return stationName.replaceAll("정류장|정류소", "").trim();
        }
        if (stationName.contains("대학교") || stationName.contains("대학")) {
            return stationName.replaceAll("정류장|정류소", "").trim();
        }
        if (stationName.contains("병원")) {
            return stationName.replaceAll("정류장|정류소", "").trim();
        }
        if (stationName.contains("시청") || stationName.contains("구청")) {
            return stationName.replaceAll("정류장|정류소", "").trim();
        }
        if (stationName.contains("종점") || stationName.contains("차고지")) {
            return stationName.replaceAll("정류장|정류소|종점|차고지", "").trim();
        }

        // 일반적인 경우 앞의 주요 단어 추출
        String[] words = stationName.split("[\\s·.-]");
        if (words.length > 0) {
            String mainWord = words[0].replaceAll("정류장|정류소", "").trim();
            if (mainWord.length() > 1) {
                return mainWord;
            }
        }

        return stationName.replaceAll("정류장|정류소", "").trim();
    }

    // ===== 내부 클래스들 =====

    private static class TerminalInfo {
        String startTerminal;
        String endTerminal;
        int startTerminalIndex;
        int endTerminalIndex;
        List<TerminalPoint> middleTerminals = new ArrayList<>();

        @Override
        public String toString() {
            return String.format("시점:%s(%d), 종점:%s(%d), 중간회차:%d개",
                    startTerminal, startTerminalIndex, endTerminal, endTerminalIndex, middleTerminals.size());
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

    private static class DirectionAnalysis {
        String method;           // 분석 방법
        boolean isValid;         // 유효 여부
        int confidence;          // 신뢰도 (0-100)
        String segment;          // 구간 정보
        String reason;           // 판정 사유

        DirectionAnalysis(String method) {
            this.method = method;
            this.isValid = false;
            this.confidence = 0;
            this.segment = "UNKNOWN";
            this.reason = "";
        }
    }

    /**
     * 🆕 방향 분석 결과 클래스
     */
    private static class DirectionAnalysisResult {
        boolean isForwardDirection;     // 정방향 여부
        String currentSegment;          // 현재 구간
        String destinationName;         // 목적지 이름
        String directionDescription;    // 방향 설명

        DirectionAnalysisResult() {
            this.isForwardDirection = true;
            this.currentSegment = "UNKNOWN";
            this.destinationName = "목적지";
            this.directionDescription = "방향 정보 없음";
        }
    }
}