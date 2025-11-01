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
 * 버스 회차 방향을 정확히 분석하는 클래스
 * 동명 정류장(상행/하행)을 모두 검색하여 회차점 통과 없는 최적 경로 선택
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
     * 종합적인 회차 방향 분석 (동명 정류장 모두 검증)
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

        // 출발지와 도착지의 모든 정류장 인덱스 찾기
        List<Integer> startIndices = findAllStationIndices(routeStations, startStop);
        List<Integer> endIndices = findAllStationIndices(routeStations, endStop);

        // 출발지를 못 찾으면 즉시 실패
        if (startIndices.isEmpty()) {
            Log.w(TAG, "출발지 정류장 인덱스 찾기 실패");
            return new RouteDirectionInfo(false, "UNKNOWN", "출발지 정보 없음", 0);
        }

        // 도착지를 못 찾았을 때는 경로를 제공하지 않음
        if (endIndices.isEmpty()) {
            Log.w(TAG, String.format("%s번: 도착지 '%s' 정류장을 노선에서 찾지 못함 - 경로 제외",
                    bus.routeno, endStop.nodenm));
            return new RouteDirectionInfo(false, "UNKNOWN", "도착지 정류장 없음", 0);
        }

        Log.d(TAG, String.format("출발지 '%s' 후보: %d개, 도착지 '%s' 후보: %d개",
                startStop.nodenm, startIndices.size(), endStop.nodenm, endIndices.size()));

        // 모든 조합 중 회차점을 넘지 않는 최적 경로 찾기
        RouteDirectionInfo bestRoute = null;
        int bestStartIndex = -1;
        int bestEndIndex = -1;

        for (int startIndex : startIndices) {
            for (int endIndex : endIndices) {
                Log.d(TAG, String.format("경로 검증 중: 출발(%d) -> 도착(%d)", startIndex, endIndex));

                // 회차점 통과 여부 검증
                boolean crossesTurnaround = checkIfCrossesTurnaround(startIndex, endIndex, terminalInfo, routeStations);

                if (!crossesTurnaround) {
                    Log.i(TAG, String.format("유효한 경로 발견: 출발(%d) -> 도착(%d) [회차점 미통과]", startIndex, endIndex));

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
                    RouteDirectionInfo currentRoute = synthesizeAnalysisEnhanced(analyses, bus.routeno, directionResult);

                    // 최적 경로 선택 (신뢰도가 더 높은 경로 우선)
                    if (bestRoute == null || currentRoute.confidence > bestRoute.confidence) {
                        bestRoute = currentRoute;
                        bestStartIndex = startIndex;
                        bestEndIndex = endIndex;
                    }
                } else {
                    Log.d(TAG, String.format("회차점 통과 경로: 출발(%d) -> 도착(%d) [제외됨]", startIndex, endIndex));
                }
            }
        }

        if (bestRoute != null) {
            Log.i(TAG, String.format("%s번 버스 최적 경로 선택: 출발(%d) -> 도착(%d), 신뢰도: %d%%",
                    bus.routeno, bestStartIndex, bestEndIndex, bestRoute.confidence));
            return bestRoute;
        }

        // 모든 조합이 회차점을 통과하는 경우
        Log.w(TAG, String.format("%s번: 모든 경로가 회차점 통과 - 승차 불가", bus.routeno));
        return new RouteDirectionInfo(false, "회차구간통과",
                "회차점을 넘어가는 경로 (승차 불가)", 0);
    }

    /**
     * 회차점 통과 여부 검증
     */
    private static boolean checkIfCrossesTurnaround(int startIndex, int endIndex,
                                                    TerminalInfo terminalInfo,
                                                    List<TagoBusRouteStationResponse.RouteStation> routeStations) {

        // 편도 버스는 회차점 통과 검증 불필요
        if (terminalInfo.middleTerminals.isEmpty()) {
            String startName = terminalInfo.startTerminal.name;
            String endName = terminalInfo.endTerminal.name;

            if (startName.equals(endName)) {
                // 회차 버스지만 중간 회차점을 못 찾은 경우
                int estimatedTurnaroundIndex = routeStations.size() / 2;

                Log.d(TAG, String.format("회차점 추정: 인덱스 %d (전체 %d개 정류장)",
                        estimatedTurnaroundIndex, routeStations.size()));

                // 출발지와 도착지가 회차점을 사이에 두는지 확인
                if (startIndex < estimatedTurnaroundIndex && endIndex > estimatedTurnaroundIndex) {
                    Log.w(TAG, String.format("회차점 통과 감지: 출발(%d) < 회차점(%d) < 도착(%d)",
                            startIndex, estimatedTurnaroundIndex, endIndex));
                    return true;
                }

                if (startIndex > estimatedTurnaroundIndex && endIndex < estimatedTurnaroundIndex) {
                    Log.w(TAG, String.format("역방향 회차점 통과 감지: 도착(%d) < 회차점(%d) < 출발(%d)",
                            endIndex, estimatedTurnaroundIndex, startIndex));
                    return true;
                }
            }

            return false;
        }

        // 회차점이 발견된 경우
        for (TerminalPoint turnaround : terminalInfo.middleTerminals) {
            int turnaroundIndex = turnaround.index;

            // 출발지와 도착지가 회차점을 사이에 두는지 확인
            if (startIndex < turnaroundIndex && endIndex > turnaroundIndex) {
                Log.w(TAG, String.format("회차점 '%s' 통과 감지: 출발(%d) < 회차점(%d) < 도착(%d)",
                        turnaround.name, startIndex, turnaroundIndex, endIndex));
                return true;
            }

            // 역방향으로 회차점을 넘는 경우
            if (startIndex > turnaroundIndex && endIndex < turnaroundIndex) {
                Log.w(TAG, String.format("역방향 회차점 '%s' 통과 감지: 도착(%d) < 회차점(%d) < 출발(%d)",
                        turnaround.name, endIndex, turnaroundIndex, startIndex));
                return true;
            }
        }

        return false;
    }

    /**
     * 회차점/종점 분석
     */
    private static TerminalInfo analyzeTerminals(List<TagoBusRouteStationResponse.RouteStation> routeStations) {
        TerminalInfo terminalInfo = new TerminalInfo();

        if (routeStations.isEmpty()) return terminalInfo;

        terminalInfo.startTerminal = new TerminalPoint(routeStations.get(0).nodenm, 0);
        terminalInfo.endTerminal = new TerminalPoint(
                routeStations.get(routeStations.size() - 1).nodenm,
                routeStations.size() - 1);

        // 중복 방지
        Set<String> addedTerminals = new HashSet<>();

        String prevDirection = null;
        boolean hasDirectionInfo = false;

        // 1차: updowncd로 방향 전환 감지
        for (int i = 1; i < routeStations.size() - 1; i++) {
            TagoBusRouteStationResponse.RouteStation station = routeStations.get(i);
            String stationName = station.nodenm;

            if (station.updowncd != null && !station.updowncd.isEmpty() && !station.updowncd.equals("0")) {
                hasDirectionInfo = true;

                if (prevDirection != null && !prevDirection.equals(station.updowncd)) {
                    if (!addedTerminals.contains(stationName)) {
                        terminalInfo.middleTerminals.add(new TerminalPoint(stationName, i));
                        addedTerminals.add(stationName);
                        Log.d(TAG, String.format("회차점 발견 (방향전환): %s (인덱스: %d, %s -> %s)",
                                stationName, i, prevDirection, station.updowncd));
                    }
                }

                prevDirection = station.updowncd;
            }
        }

        // 2차: 방향 정보가 없으면 이름 기반 탐지
        if (!hasDirectionInfo || terminalInfo.middleTerminals.isEmpty()) {
            Log.d(TAG, "방향 정보 없음 - 정류장 이름 기반 회차점 탐지");

            for (int i = 1; i < routeStations.size() - 1; i++) {
                String stationName = routeStations.get(i).nodenm;

                if (stationName != null &&
                        (stationName.contains("종점") || stationName.contains("터미널"))) {
                    if (!addedTerminals.contains(stationName)) {
                        terminalInfo.middleTerminals.add(new TerminalPoint(stationName, i));
                        addedTerminals.add(stationName);
                        Log.d(TAG, String.format("회차점 발견 (이름기반): %s (인덱스: %d)",
                                stationName, i));
                    }
                }
            }
        }

        Log.d(TAG, String.format("회차점 분석 완료: 총 %d개 회차점 발견", terminalInfo.middleTerminals.size()));
        return terminalInfo;
    }

    /**
     * 현재 운행 방향 분석
     */
    private static DirectionAnalysisResult analyzeCurrentDirectionEnhanced(
            int startIndex, int endIndex, TerminalInfo terminalInfo,
            List<TagoBusRouteStationResponse.RouteStation> routeStations, String busNumber) {

        DirectionAnalysisResult result = new DirectionAnalysisResult();

        if (terminalInfo.middleTerminals.isEmpty()) {
            result.isForwardDirection = startIndex < endIndex;
            result.currentSegment = result.isForwardDirection ? "전반부" : "후반부";
            result.destinationName = result.isForwardDirection ?
                    terminalInfo.endTerminal.name : terminalInfo.startTerminal.name;
            result.directionDescription = result.destinationName + "방면";
        } else {
            TerminalPoint firstTurnaround = terminalInfo.middleTerminals.get(0);

            if (startIndex < firstTurnaround.index && endIndex < firstTurnaround.index) {
                result.isForwardDirection = true;
                result.currentSegment = "1구간";
                result.destinationName = firstTurnaround.name;
                result.directionDescription = firstTurnaround.name + "방면 (상행)";
            } else if (startIndex > firstTurnaround.index && endIndex > firstTurnaround.index) {
                result.isForwardDirection = true;
                result.currentSegment = "2구간";
                result.destinationName = terminalInfo.endTerminal.name;
                result.directionDescription = terminalInfo.endTerminal.name + "방면 (상행)";
            } else {
                result.currentSegment = "회차구간";
                result.directionDescription = "회차점을 넘어가는 구간";
            }
        }

        return result;
    }

    /**
     * 노선에서 동일한 이름을 가진 모든 정류장의 인덱스 찾기
     * 예: "질구지" 검색 시 index 18과 41 모두 반환
     */
    private static List<Integer> findAllStationIndices(
            List<TagoBusRouteStationResponse.RouteStation> routeStations,
            TagoBusStopResponse.BusStop targetStop) {

        List<Integer> indices = new ArrayList<>();
        List<Integer> partialMatchIndices = new ArrayList<>();

        Log.d(TAG, "=== 모든 정류장 인덱스 찾기 시작 ===");
        Log.d(TAG, "목표: " + targetStop.nodenm + " (ID: " + targetStop.nodeid + ")");
        Log.d(TAG, "노선 정류장 수: " + routeStations.size());

        if (targetStop.nodenm == null || targetStop.nodenm.trim().isEmpty()) {
            Log.w(TAG, "목표 정류장 이름이 없음");
            return indices;
        }

        String targetName = targetStop.nodenm.trim();

        // 1단계: 정확한 매칭 우선 검색
        for (int i = 0; i < routeStations.size(); i++) {
            TagoBusRouteStationResponse.RouteStation station = routeStations.get(i);

            if (station.nodenm == null) {
                continue;
            }

            String stationName = station.nodenm.trim();

            // 정확히 이름이 같은 경우
            if (stationName.equals(targetName)) {
                indices.add(i);
                Log.d(TAG, String.format("매칭 성공: '%s' (인덱스: %d)", stationName, i));
            }
            // 띄어쓰기와 괄호 제거 후 비교
            else if (removeSpacesAndParentheses(stationName).equals(removeSpacesAndParentheses(targetName))) {
                indices.add(i);
                Log.d(TAG, String.format("매칭 성공(정규화): '%s' = '%s' (인덱스: %d)", stationName, targetName, i));
            }
            // 한쪽이 다른 쪽을 포함하는 경우 (예: "도청" ↔ "충북도청")
            else if (stationName.contains(targetName) || targetName.contains(stationName)) {
                // 길이 차이가 5자 이내면 부분 매칭으로 인정
                if (Math.abs(stationName.length() - targetName.length()) <= 5) {
                    partialMatchIndices.add(i);
                    Log.d(TAG, String.format("부분 매칭 발견: '%s' ≈ '%s' (인덱스: %d)", stationName, targetName, i));
                }
            }
        }

        // 정확한 매칭이 없으면 부분 매칭 사용
        if (indices.isEmpty() && !partialMatchIndices.isEmpty()) {
            Log.w(TAG, "정확한 매칭 없음 - 부분 매칭 사용");
            indices.addAll(partialMatchIndices);
        }

        if (!indices.isEmpty()) {
            Log.i(TAG, String.format("'%s' 정류장 총 %d개 발견: %s",
                    targetName, indices.size(), indices.toString()));
        } else {
            Log.w(TAG, String.format("'%s' 정류장을 찾을 수 없음", targetName));
        }

        return indices;
    }

    /**
     * 기본 방향 분석
     */
    private static DirectionAnalysis analyzeByBasicDirection(int startIndex, int endIndex,
                                                             List<TagoBusRouteStationResponse.RouteStation> routeStations) {
        DirectionAnalysis analysis = new DirectionAnalysis("기본방향");
        analysis.isValid = startIndex < endIndex;
        analysis.confidence = 70;
        analysis.segment = analysis.isValid ? "정방향" : "역방향";
        analysis.reason = String.format("출발(%d) → 도착(%d)", startIndex, endIndex);
        return analysis;
    }

    /**
     * 터미널 위치 분석
     */
    private static DirectionAnalysis analyzeByTerminalPositionFixed(
            int startIndex, int endIndex, TerminalInfo terminalInfo,
            List<TagoBusRouteStationResponse.RouteStation> routeStations,
            DirectionAnalysisResult directionResult) {

        DirectionAnalysis analysis = new DirectionAnalysis("터미널위치");

        if (terminalInfo.middleTerminals.isEmpty()) {
            analysis.isValid = true;
            analysis.confidence = 50;
            analysis.segment = "편도노선";
            analysis.reason = "회차점 없음";
            return analysis;
        }

        TerminalPoint turnaround = terminalInfo.middleTerminals.get(0);
        boolean beforeTurnaround = startIndex < turnaround.index && endIndex < turnaround.index;
        boolean afterTurnaround = startIndex > turnaround.index && endIndex > turnaround.index;
        // 회차점에서 출발하는 경우 추가 (회차 후 목적지로 가는 정상 승차)
        boolean startAtTurnaround = startIndex == turnaround.index && endIndex > turnaround.index;

        analysis.isValid = beforeTurnaround || afterTurnaround || startAtTurnaround;
        analysis.confidence = analysis.isValid ? 90 : 10;
        analysis.segment = beforeTurnaround ? "1구간" : (afterTurnaround || startAtTurnaround ? "2구간" : "회차구간");
        analysis.reason = startAtTurnaround ?
                String.format("회차점(%d)에서 탑승 → 회차 후 목적지", turnaround.index) :
                String.format("회차점(%d) 기준 분석", turnaround.index);

        return analysis;
    }

    /**
     * 정류장 순서 분석
     */
    private static DirectionAnalysis analyzeByStationOrder(int startIndex, int endIndex,
                                                           List<TagoBusRouteStationResponse.RouteStation> routeStations) {
        DirectionAnalysis analysis = new DirectionAnalysis("정류장순서");
        analysis.isValid = startIndex < endIndex;
        analysis.confidence = 80;
        analysis.segment = analysis.isValid ? "순차구간" : "역순구간";
        analysis.reason = String.format("인덱스 차이: %d", Math.abs(endIndex - startIndex));
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

        DirectionAnalysis analysis = new DirectionAnalysis("좌표분석");

        if (startStop.gpslati == 0 || endStop.gpslati == 0) {
            analysis.isValid = true;
            analysis.confidence = 30;
            analysis.segment = "좌표없음";
            analysis.reason = "좌표 정보 부족";
            return analysis;
        }

        double startLat = startStop.gpslati;
        double endLat = endStop.gpslati;

        analysis.isValid = true;
        analysis.confidence = 60;
        analysis.segment = startLat < endLat ? "북향" : "남향";
        analysis.reason = String.format("위도 변화: %.4f", endLat - startLat);

        return analysis;
    }

    /**
     * 종합 분석 결과 합성
     */
    private static RouteDirectionInfo synthesizeAnalysisEnhanced(
            List<DirectionAnalysis> analyses, String busNumber, DirectionAnalysisResult directionResult) {

        int validCount = 0;
        int invalidCount = 0;
        int totalConfidence = 0;
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
        final int EARTH_RADIUS = 6371000;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    /**
     * 도착지 정류장을 노선에서 찾지 못했을 때 좌표 기반으로 방향 추론
     * 회차 노선에서 출발지가 2개 있을 때 어느 방향이 올바른지 판단
     */
    private static RouteDirectionInfo analyzeDirectionByCoordinatesAndTerminals(
            TagoBusStopResponse.BusStop startStop,
            TagoBusStopResponse.BusStop endStop,
            List<Integer> startIndices,
            TerminalInfo terminalInfo,
            List<TagoBusRouteStationResponse.RouteStation> routeStations,
            TagoBusArrivalResponse.BusArrival bus) {

        Log.d(TAG, bus.routeno + "번 버스 좌표 기반 방향 추론 시작");

        // 좌표 정보가 없으면 실패
        if (startStop.gpslati == 0 || startStop.gpslong == 0 ||
                endStop.gpslati == 0 || endStop.gpslong == 0) {
            Log.w(TAG, "좌표 정보 부족으로 방향 추론 불가");
            return new RouteDirectionInfo(false, "UNKNOWN", "좌표 정보 없음", 0);
        }

        // 출발지 후보가 1개뿐이면 해당 방향 선택
        if (startIndices.size() == 1) {
            int startIndex = startIndices.get(0);
            DirectionAnalysisResult result = analyzeCurrentDirectionEnhanced(
                    startIndex, startIndex, terminalInfo, routeStations, bus.routeno);

            Log.i(TAG, bus.routeno + "번: 출발지 1개 - " + result.directionDescription);
            return new RouteDirectionInfo(true, result.currentSegment,
                    result.directionDescription + " (좌표추정)", 60,
                    result.destinationName, result.isForwardDirection);
        }

        // 출발지 후보가 2개 이상일 때: 각 구간의 종점과 도착지 좌표 거리 비교
        RouteDirectionInfo bestDirection = null;
        double minDistance = Double.MAX_VALUE;
        int bestStartIndex = -1;

        for (int startIndex : startIndices) {
            // 이 출발지가 속한 구간 분석
            DirectionAnalysisResult segmentInfo = analyzeCurrentDirectionEnhanced(
                    startIndex, startIndex, terminalInfo, routeStations, bus.routeno);

            // 이 구간의 종점 좌표 찾기
            String destinationName = segmentInfo.destinationName;
            TagoBusRouteStationResponse.RouteStation destinationStation = null;

            for (TagoBusRouteStationResponse.RouteStation station : routeStations) {
                if (station.nodenm != null && station.nodenm.contains(destinationName)) {
                    destinationStation = station;
                    break;
                }
            }

            // 종점을 찾지 못하면 노선의 마지막 정류장 사용
            if (destinationStation == null) {
                if (startIndex < routeStations.size() / 2) {
                    // 전반부 구간이면 중간 회차점 사용
                    if (!terminalInfo.middleTerminals.isEmpty()) {
                        int turnaroundIndex = terminalInfo.middleTerminals.get(0).index;
                        destinationStation = routeStations.get(turnaroundIndex);
                    } else {
                        destinationStation = routeStations.get(routeStations.size() - 1);
                    }
                } else {
                    // 후반부 구간이면 종점 사용
                    destinationStation = routeStations.get(routeStations.size() - 1);
                }
            }

            // 종점 좌표가 있으면 도착지와의 거리 계산
            if (destinationStation != null && destinationStation.gpslati != 0) {
                double distance = calculateDistance(
                        endStop.gpslati, endStop.gpslong,
                        destinationStation.gpslati, destinationStation.gpslong);

                Log.d(TAG, String.format("출발지 인덱스 %d (%s 방면): 도착지까지 거리 %.0fm",
                        startIndex, segmentInfo.destinationName, distance));

                // 가장 가까운 종점을 가진 구간 선택
                if (distance < minDistance) {
                    minDistance = distance;
                    bestStartIndex = startIndex;
                    bestDirection = new RouteDirectionInfo(
                            true,
                            segmentInfo.currentSegment,
                            segmentInfo.directionDescription + " (좌표추정)",
                            55, // 좌표 기반이므로 중간 신뢰도
                            segmentInfo.destinationName,
                            segmentInfo.isForwardDirection
                    );
                }
            }
        }

        if (bestDirection != null) {
            Log.i(TAG, String.format("%s번 버스 좌표 기반 방향 추론 성공: 출발지 인덱스 %d 선택, 거리 %.0fm",
                    bus.routeno, bestStartIndex, minDistance));
            return bestDirection;
        }

        // 좌표 기반 추론도 실패
        Log.w(TAG, bus.routeno + "번: 좌표 기반 방향 추론 실패");
        return new RouteDirectionInfo(false, "UNKNOWN", "방향 추론 실패", 0);
    }

    /**
     * 문자열에서 띄어쓰기, 괄호, 특수문자 제거
     * "충북 도청(본관)" → "충북도청본관"
     */
    private static String removeSpacesAndParentheses(String str) {
        if (str == null) return "";
        return str.replaceAll("[\\s()\\[\\]\\-_]", "");
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