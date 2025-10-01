package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 국토교통부_버스 노선정보 조회 서비스의 '노선별 경유 정류소 목록 조회' 응답을 위한 개선된 데이터 클래스
 * 상행/하행 구분 및 정류장 순서 문제 해결을 위한 추가 필드 포함
 */
public class TagoBusRouteStationResponse {

    @SerializedName("response")
    public ResponseData response;

    public static class ResponseData {
        @SerializedName("header")
        public Header header;

        @SerializedName("body")
        public Body body;
    }

    public static class Header {
        @SerializedName("resultCode")
        public String resultCode;

        @SerializedName("resultMsg")
        public String resultMsg;
    }

    public static class Body {
        @SerializedName("items")
        public Items items;

        @SerializedName("numOfRows")
        public int numOfRows;

        @SerializedName("pageNo")
        public int pageNo;

        @SerializedName("totalCount")
        public int totalCount;
    }

    public static class Items {
        @SerializedName("item")
        public List<RouteStation> item;
    }

    /**
     * 개선된 노선 정류장 클래스 - 다양한 순서 및 방향 필드 포함
     */
    public static class RouteStation {
        // 기본 좌표 정보
        @SerializedName("gpslati")
        public double gpslati; // 위도

        @SerializedName("gpslong")
        public double gpslong; // 경도

        // 정류장 기본 정보
        @SerializedName("nodeid")
        public String nodeid; // 정류소 ID

        @SerializedName("nodenm")
        public String nodenm; // 정류소명

        @SerializedName("nodeord")
        public String nodeord; // 정류소 순번 (문자열)

        // 순서 관련 필드들 (API마다 다를 수 있음)
        @SerializedName("ord")
        public int ord; // 순번 (기본)

        @SerializedName("seq")
        public int seq; // 순서

        @SerializedName("sequence")
        public int sequence; // 시퀀스

        @SerializedName("stationSeq")
        public int stationSeq; // 역순

        @SerializedName("arrivalSeq")
        public int arrivalSeq; // 도착 순서

        @SerializedName("stationOrd")
        public int stationOrd; // 정류장 순서

        @SerializedName("routeSeq")
        public int routeSeq; // 노선 순서

        // 방향 관련 필드들
        @SerializedName("direction")
        public String direction; // 방향 정보

        @SerializedName("updown")
        public String updown; // 상행/하행 구분

        @SerializedName("updowncd")
        public String updowncd; // 상행/하행 코드

        @SerializedName("directCd")
        public String directCd; // 방향 코드

        @SerializedName("directType")
        public String directType; // 방향 타입

        @SerializedName("routeDirection")
        public String routeDirection; // 노선 방향

        @SerializedName("way")
        public String way; // 방향 (왕복 구분)

        // 추가 노선 정보
        @SerializedName("routeid")
        public String routeid; // 노선 ID

        @SerializedName("routeno")
        public String routeno; // 노선 번호

        @SerializedName("citycode")
        public String citycode; // 도시 코드

        @SerializedName("citynm")
        public String citynm; // 도시명

        // 정류장 추가 정보
        @SerializedName("nodeNo")
        public String nodeNo; // 정류장 번호

        @SerializedName("stationId")
        public String stationId; // 역 ID

        @SerializedName("stationNm")
        public String stationNm; // 역명

        // 운행 관련 정보
        @SerializedName("arrivalTime")
        public String arrivalTime; // 도착 시간

        @SerializedName("departureTime")
        public String departureTime; // 출발 시간

        @SerializedName("travelTime")
        public int travelTime; // 통행 시간

        // 거리 정보
        @SerializedName("distance")
        public double distance; // 거리

        @SerializedName("distFromStart")
        public double distFromStart; // 시작점부터 거리

        /**
         * 여러 순서 필드 중 유효한 값을 반환
         * @return 정류장 순서 (유효한 값이 없으면 -1)
         */
        public int getValidOrder() {
            // 우선순위: ord > seq > stationSeq > sequence > routeSeq > stationOrd > arrivalSeq
            if (ord > 0) return ord;
            if (seq > 0) return seq;
            if (stationSeq > 0) return stationSeq;
            if (sequence > 0) return sequence;
            if (routeSeq > 0) return routeSeq;
            if (stationOrd > 0) return stationOrd;
            if (arrivalSeq > 0) return arrivalSeq;

            // 문자열 형태의 순번도 체크
            if (nodeord != null && !nodeord.trim().isEmpty()) {
                try {
                    return Integer.parseInt(nodeord);
                } catch (NumberFormatException e) {
                    // 숫자로 변환할 수 없는 경우 무시
                }
            }

            return -1; // 순서 정보 없음
        }

        /**
         * 방향 정보를 종합하여 상행/하행 판단
         * @return 방향 정보 (UP: 상행, DOWN: 하행, UNKNOWN: 불명)
         */
        public String getDirectionInfo() {
            // 1차: updown 필드 체크
            if (updown != null && !updown.trim().isEmpty()) {
                String normalized = updown.toLowerCase().trim();
                if (normalized.contains("up") || normalized.contains("상행") || normalized.equals("1")) {
                    return "UP";
                } else if (normalized.contains("down") || normalized.contains("하행") || normalized.equals("2")) {
                    return "DOWN";
                }
            }

            // 2차: updowncd 필드 체크
            if (updowncd != null && !updowncd.trim().isEmpty()) {
                String code = updowncd.trim();
                if (code.equals("1") || code.equals("U") || code.toUpperCase().equals("UP")) {
                    return "UP";
                } else if (code.equals("2") || code.equals("D") || code.toUpperCase().equals("DOWN")) {
                    return "DOWN";
                }
            }

            // 3차: direction 필드 체크
            if (direction != null && !direction.trim().isEmpty()) {
                String dir = direction.toLowerCase().trim();
                if (dir.contains("up") || dir.contains("상행") || dir.contains("정방향")) {
                    return "UP";
                } else if (dir.contains("down") || dir.contains("하행") || dir.contains("역방향")) {
                    return "DOWN";
                }
            }

            // 4차: directCd 필드 체크
            if (directCd != null && !directCd.trim().isEmpty()) {
                String code = directCd.trim();
                if (code.equals("1") || code.equals("A") || code.toUpperCase().equals("FWD")) {
                    return "UP";
                } else if (code.equals("2") || code.equals("B") || code.toUpperCase().equals("BWD")) {
                    return "DOWN";
                }
            }

            return "UNKNOWN";
        }

        /**
         * 상행/하행 여부를 boolean으로 반환
         * @return true: 상행, false: 하행, null: 불명
         */
        public Boolean isUpDirection() {
            String direction = getDirectionInfo();
            if ("UP".equals(direction)) {
                return true;
            } else if ("DOWN".equals(direction)) {
                return false;
            }
            return null; // 방향 불명
        }

        /**
         * 정류장 정보 유효성 검증
         * @return 필수 정보가 모두 있으면 true
         */
        public boolean isValid() {
            return nodeid != null && !nodeid.trim().isEmpty() &&
                    nodenm != null && !nodenm.trim().isEmpty() &&
                    gpslati > 0 && gpslong > 0;
        }

        /**
         * 정류장 간 거리 계산
         * @param other 다른 정류장
         * @return 거리 (미터 단위)
         */
        public double calculateDistanceTo(RouteStation other) {
            if (other == null || !this.isValid() || !other.isValid()) {
                return Double.MAX_VALUE;
            }

            double earthRadius = 6371000; // 지구 반지름 (미터)

            double dLat = Math.toRadians(other.gpslati - this.gpslati);
            double dLng = Math.toRadians(other.gpslong - this.gpslong);

            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(this.gpslati)) * Math.cos(Math.toRadians(other.gpslati)) *
                            Math.sin(dLng / 2) * Math.sin(dLng / 2);

            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            return earthRadius * c;
        }

        /**
         * 정류장과 특정 좌표 간 거리 계산
         * @param latitude 위도
         * @param longitude 경도
         * @return 거리 (미터 단위)
         */
        public double calculateDistanceTo(double latitude, double longitude) {
            if (!this.isValid()) {
                return Double.MAX_VALUE;
            }

            double earthRadius = 6371000;

            double dLat = Math.toRadians(latitude - this.gpslati);
            double dLng = Math.toRadians(longitude - this.gpslong);

            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(this.gpslati)) * Math.cos(Math.toRadians(latitude)) *
                            Math.sin(dLng / 2) * Math.sin(dLng / 2);

            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            return earthRadius * c;
        }

        /**
         * 정류장 정보를 문자열로 표현
         * @return 정류장 정보 문자열
         */
        @Override
        public String toString() {
            return String.format("RouteStation{name='%s', id='%s', order=%d, direction='%s', lat=%.6f, lng=%.6f}",
                    nodenm, nodeid, getValidOrder(), getDirectionInfo(), gpslati, gpslong);
        }

        /**
         * 두 정류장이 같은지 비교 (ID 기준)
         * @param obj 비교할 객체
         * @return 같으면 true
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;

            RouteStation that = (RouteStation) obj;
            return nodeid != null ? nodeid.equals(that.nodeid) : that.nodeid == null;
        }

        /**
         * 해시코드 생성 (ID 기준)
         * @return 해시코드
         */
        @Override
        public int hashCode() {
            return nodeid != null ? nodeid.hashCode() : 0;
        }
    }
}