// app/src/main/java/com/sjoneon/cap/BusDirectionAnalyzer.java

package com.sjoneon.cap.unils;

import android.util.Log;

import com.sjoneon.cap.models.api.TagoBusArrivalResponse;
import com.sjoneon.cap.models.api.TagoBusRouteStationResponse;
import com.sjoneon.cap.models.api.TagoBusStopResponse;

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
     * 🔧 수정된 종합적인 회차 방향 분석 (회차 구간 방향 판정 개선)
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
            return new RouteDirectionInfo(false, "UNKNOWN", "회차 버스", 0);
        }

        // 4. 🔧 핵심 수정: 회차 구간 방향 정확한 분석
        DirectionAnalysisResult directionResult = analyzeCurrentDirectionEnhanced(
                startIndex, endIndex, terminalInfo, routeStations, bus.routeno);

        // 5. 다중 방향 분석
        List<DirectionAnalysis> analyses = new ArrayList<>();

        // 5-1. 🔧 수정된 기본 방향 정보 기반 분석 (API 필드 없음)
        analyses.add(analyzeByBasicDirection(startIndex, endIndex, routeStations));

        // 5-2. 🔧 수정된 회차점 기반 분석
        analyses.add(analyzeByTerminalPositionFixed(startIndex, endIndex, terminalInfo, routeStations, directionResult));

        // 5-3. 정류장 순서 기반 분석
        analyses.add(analyzeByStationOrder(startIndex, endIndex, routeStations));

        // 5-4. 좌표 기반 분석 (올바른 필드명 사용)
        analyses.add(analyzeByCoordinatesFixed(startStop, endStop, routeStations, startIndex, endIndex));

        // 6. 종합 판정 (방향 정보 포함)
        return synthesizeAnalysisEnhanced(analyses, bus.routeno, directionResult);
    }

    /**
     * 🔧 수정된 현재 운행 방향 분석 (회차 구간 방향 판정 핵심 개선)
     */
    private static DirectionAnalysisResult analyzeCurrentDirectionEnhanced(
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

            // 🔧 핵심 수정: 회차 구간별 정확한 방향 판정
            if (startIndex < terminalIndex && endIndex < terminalIndex) {
                // 첫 번째 구간 (시점→회차점)
                result.isForwardDirection = true;
                result.currentSegment = "전반부";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(terminalIndex).nodenm);
                result.directionDescription = result.destinationName + "방면 (상행)";

            } else if (startIndex > terminalIndex && endIndex > terminalIndex) {
                // 🔧 핵심 수정: 두 번째 구간 (회차점→시점) - 역방향으로 정확히 판정
                result.isForwardDirection = false;
                result.currentSegment = "후반부 (회차 구간)";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(0).nodenm);
                result.directionDescription = result.destinationName + "방면 (하행)";

                // 🚨 회차 구간에서는 추가 검증 필요
                Log.w(TAG, String.format("🚨 %s번: 회차 구간 감지 - %s(인덱스:%d) → %s(인덱스:%d), 회차점: %d",
                        busNumber, routeStations.get(startIndex).nodenm, startIndex,
                        routeStations.get(endIndex).nodenm, endIndex, terminalIndex));

            } else if (startIndex < terminalIndex && endIndex > terminalIndex) {
                // 전체 구간 횡단 - 회차점을 넘어가는 경우
                result.isForwardDirection = true;
                result.currentSegment = "전체구간";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(routeStations.size() - 1).nodenm);
                result.directionDescription = result.destinationName + "방면 (상행)";

            } else {
                // 🔧 회차점→시점 구간 (역방향)
                result.isForwardDirection = false;
                result.currentSegment = "역방향 구간";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(0).nodenm);
                result.directionDescription = result.destinationName + "방면 (하행)";
            }

        } else {
            // 🔍 일반적인 왕복 노선 (첫 번째/마지막 정류장이 같은 경우)
            if (startIndex < midPoint && endIndex < midPoint) {
                // 전반부 구간
                result.isForwardDirection = true;
                result.currentSegment = "전반부";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(routeStations.size() - 1).nodenm);
                result.directionDescription = result.destinationName + "방면 (상행)";

            } else if (startIndex > midPoint && endIndex > midPoint) {
                // 🔧 후반부 구간 - 역방향으로 정확히 판정
                result.isForwardDirection = false;
                result.currentSegment = "후반부 (회차 구간)";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(0).nodenm);
                result.directionDescription = result.destinationName + "방면 (하행)";

                Log.w(TAG, String.format("🚨 %s번: 후반부 회차 구간 감지 - %s(인덱스:%d) → %s(인덱스:%d)",
                        busNumber, routeStations.get(startIndex).nodenm, startIndex,
                        routeStations.get(endIndex).nodenm, endIndex));

            } else {
                // 중간점을 넘나드는 경우
                result.isForwardDirection = startIndex < endIndex;
                result.currentSegment = startIndex < endIndex ? "전반부" : "후반부";
                result.destinationName = extractDirectionFromStationName(
                        routeStations.get(startIndex < endIndex ? routeStations.size() - 1 : 0).nodenm);
                result.directionDescription = result.destinationName + "방면 (" +
                        (startIndex < endIndex ? "상행" : "하행") + ")";
            }
        }

        return result;
    }

    /**
     * 🔧 수정된 회차점 위치 기반 분석 (회차 구간 방향 판정 개선)
     */
    private static DirectionAnalysis analyzeByTerminalPositionFixed(
            int startIndex, int endIndex, TerminalInfo terminalInfo,
            List<TagoBusRouteStationResponse.RouteStation> routeStations,
            DirectionAnalysisResult directionResult) {

        DirectionAnalysis analysis = new DirectionAnalysis("TERMINAL_POSITION");

        try {
            // 🔧 핵심 수정: 회차 구간에서는 역방향 판정
            if (directionResult.currentSegment.contains("후반부") ||
                    directionResult.currentSegment.contains("회차 구간") ||
                    !directionResult.isForwardDirection) {

                // 회차 구간에서는 승차 불가능 (회차 대기 필요)
                analysis.isValid = false;
                analysis.segment = "회차 대기 필요";
                analysis.confidence = 85; // 높은 신뢰도로 회차 대기 판정
                analysis.reason = "회차 구간에서 목적지 도달 불가 - 반대편 정류장 이용 필요";

                Log.w(TAG, String.format("🚨 회차 구간 판정: %s → %s, 정류장 순서: %d → %d",
                        routeStations.get(startIndex).nodenm, routeStations.get(endIndex).nodenm,
                        startIndex, endIndex));

            } else {
                // 정방향 구간에서는 기존 로직 적용
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
     * 🔧 수정된 기본 방향 정보 기반 분석 (API 필드 없으므로 기본 로직 사용)
     */
    private static DirectionAnalysis analyzeByBasicDirection(
            int startIndex, int endIndex,
            List<TagoBusRouteStationResponse.RouteStation> routeStations) {

        DirectionAnalysis analysis = new DirectionAnalysis("BASIC_DIRECTION");

        try {
            // API에서 방향 정보를 제공하지 않으므로 기본적인 순서 기반 판정
            boolean isForward = startIndex < endIndex;

            analysis.isValid = isForward;
            analysis.confidence = 40; // 낮은 신뢰도 (API 정보 없음)
            analysis.segment = isForward ? "순방향 추정" : "역방향 추정";
            analysis.reason = "API 방향 정보 없음, 정류장 순서로 추정";

        } catch (Exception e) {
            Log.e(TAG, "기본 방향 정보 분석 실패", e);
            analysis.isValid = false;
            analysis.confidence = 0;
            analysis.reason = "기본 방향 정보 분석 오류";
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
        analysis.reason = "정류장 순서: " + startIndex + " → " + endIndex;

        return analysis;
    }

    /**
     * 🔧 수정된 좌표 기반 분석 (올바른 필드명 사용)
     */
    private static DirectionAnalysis analyzeByCoordinatesFixed(
            TagoBusStopResponse.BusStop startStop,
            TagoBusStopResponse.BusStop endStop,
            List<TagoBusRouteStationResponse.RouteStation> routeStations,
            int startIndex, int endIndex) {

        DirectionAnalysis analysis = new DirectionAnalysis("COORDINATES");

        try {
            // 실제 경로 거리 계산
            double routeDistance = calculateRouteDistance(routeStations, startIndex, endIndex);

            // 🔧 수정된 직선 거리 계산 (올바른 필드명 사용)
            double directDistance = calculateDirectDistanceFixed(startStop, endStop);

            if (directDistance > 0) {
                double ratio = routeDistance / directDistance;
                analysis.confidence = ratio < 2.0 ? 60 : (ratio < 5.0 ? 50 : 40);
                analysis.segment = "경로분석";
                analysis.reason = String.format("경로/직선 거리 비율: %.2f%s",
                        ratio, ratio > 5.0 ? " (우회 의심)" : " (정상)");
                analysis.isValid = ratio < 10.0; // 너무 우회하면 의심
            } else {
                analysis.confidence = 50;
                analysis.segment = "거리분석불가";
                analysis.reason = "좌표 정보 부족";
                analysis.isValid = true;
            }

        } catch (Exception e) {
            Log.e(TAG, "좌표 기반 분석 실패", e);
            analysis.isValid = true;
            analysis.confidence = 50;
            analysis.reason = "좌표 분석 오류";
        }

        return analysis;
    }

    /**
     * 🔧 수정된 종합 판정 (회차 구간 방향 판정 반영)
     */
    private static RouteDirectionInfo synthesizeAnalysisEnhanced(
            List<DirectionAnalysis> analyses, String busNumber, DirectionAnalysisResult directionResult) {

        int validCount = 0;
        int invalidCount = 0;
        int totalConfidence = 0;
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

        // 🔧 핵심 수정: 회차 구간은 무조건 회차 대기 판정
        boolean finalValid;
        String finalSegment;
        String finalDescription;
        int finalConfidence = totalConfidence / analyses.size();

        if (directionResult.currentSegment.contains("회차 구간") ||
                directionResult.currentSegment.contains("후반부") ||
                !directionResult.isForwardDirection) {

            // 🚨 회차 구간에서는 무조건 회차 대기
            finalValid = false;
            finalSegment = "회차대기";
            finalDescription = "회차 후 " + directionResult.directionDescription;
            finalConfidence = Math.max(finalConfidence, 75); // 높은 신뢰도로 회차 대기

        } else if (validCount > invalidCount) {
            finalValid = true;
            finalSegment = "승차가능";
            finalDescription = directionResult.directionDescription;
        } else if (invalidCount > validCount) {
            finalValid = false;
            finalSegment = "회차대기";
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

    // ================================================================================================
    // 지원 클래스들 및 유틸리티 메서드들
    // ================================================================================================

    /**
     * 방향 분석 결과를 담는 클래스
     */
    private static class DirectionAnalysisResult {
        boolean isForwardDirection = true;
        String currentSegment = "전반부";
        String destinationName = "목적지";
        String directionDescription = "목적지방면 (상행)";
    }

    /**
     * 개별 분석 결과를 담는 클래스
     */
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
     * 터미널 지점 정보
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
        TerminalInfo terminalInfo = new TerminalInfo();

        if (routeStations.isEmpty()) return terminalInfo;

        // 시작점과 끝점 설정
        terminalInfo.startTerminal = new TerminalPoint(routeStations.get(0).nodenm, 0);
        terminalInfo.endTerminal = new TerminalPoint(
                routeStations.get(routeStations.size() - 1).nodenm,
                routeStations.size() - 1);

        // 중간 회차점 찾기 (종점, 터미널 등이 포함된 이름)
        for (int i = 1; i < routeStations.size() - 1; i++) {
            String stationName = routeStations.get(i).nodenm;
            if (stationName.contains("종점") || stationName.contains("터미널") ||
                    stationName.contains("차고지") || stationName.contains("공영차고")) {
                terminalInfo.middleTerminals.add(new TerminalPoint(stationName, i));
                Log.d(TAG, "중간 회차점 발견: " + stationName + " (인덱스: " + i + ")");
            }
        }

        return terminalInfo;
    }

    /**
     * 개선된 정류장 인덱스 찾기
     */
    private static int findStationIndexEnhanced(List<TagoBusRouteStationResponse.RouteStation> routeStations,
                                                TagoBusStopResponse.BusStop targetStop) {
        // 1차: ID로 정확 매칭
        for (int i = 0; i < routeStations.size(); i++) {
            if (routeStations.get(i).nodeid.equals(targetStop.nodeid)) {
                return i;
            }
        }

        // 2차: 이름으로 매칭
        for (int i = 0; i < routeStations.size(); i++) {
            if (routeStations.get(i).nodenm.equals(targetStop.nodenm)) {
                return i;
            }
        }

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
     * 경로 거리 계산 (정류장간 거리의 합)
     */
    private static double calculateRouteDistance(List<TagoBusRouteStationResponse.RouteStation> routeStations,
                                                 int startIndex, int endIndex) {
        // 간단한 구현: 정류장 개수 기반으로 추정
        return Math.abs(endIndex - startIndex) * 500; // 정류장간 평균 500m 가정
    }

    /**
     * 🔧 수정된 직선 거리 계산 (올바른 필드명 사용)
     */
    private static double calculateDirectDistanceFixed(TagoBusStopResponse.BusStop startStop,
                                                       TagoBusStopResponse.BusStop endStop) {
        try {
            // 🔧 올바른 필드명 사용: gpslati, gpslong
            double lat1 = startStop.gpslati;
            double lon1 = startStop.gpslong;
            double lat2 = endStop.gpslati;
            double lon2 = endStop.gpslong;

            // 하버사인 공식으로 거리 계산
            double R = 6371000; // 지구 반지름 (미터)
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);
            double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                            Math.sin(dLon/2) * Math.sin(dLon/2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

            return R * c;
        } catch (Exception e) {
            Log.e(TAG, "직선 거리 계산 실패", e);
            return 0;
        }
    }
}