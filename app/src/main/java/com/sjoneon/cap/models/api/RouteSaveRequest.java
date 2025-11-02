package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;
import com.sjoneon.cap.fragments.RouteFragment;
import java.util.List;

public class RouteSaveRequest {
    @SerializedName("start_lat")
    private double startLat;

    @SerializedName("start_lng")
    private double startLng;

    @SerializedName("end_lat")
    private double endLat;

    @SerializedName("end_lng")
    private double endLng;

    @SerializedName("route_data")
    private List<RouteDataItem> routeData;

    @SerializedName("user_uuid")
    private String userUuid;

    public RouteSaveRequest(double startLat, double startLng, double endLat, double endLng,
                            List<RouteDataItem> routeData, String userUuid) {
        this.startLat = startLat;
        this.startLng = startLng;
        this.endLat = endLat;
        this.endLng = endLng;
        this.routeData = routeData;
        this.userUuid = userUuid;
    }

    // Getters and Setters
    public double getStartLat() { return startLat; }
    public void setStartLat(double startLat) { this.startLat = startLat; }
    public double getStartLng() { return startLng; }
    public void setStartLng(double startLng) { this.startLng = startLng; }
    public double getEndLat() { return endLat; }
    public void setEndLat(double endLat) { this.endLat = endLat; }
    public double getEndLng() { return endLng; }
    public void setEndLng(double endLng) { this.endLng = endLng; }
    public List<RouteDataItem> getRouteData() { return routeData; }
    public void setRouteData(List<RouteDataItem> routeData) { this.routeData = routeData; }
    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }

    // RouteInfo를 RouteDataItem으로 변환하는 헬퍼 메서드
    public static RouteDataItem fromRouteInfo(RouteFragment.RouteInfo routeInfo) {
        return new RouteDataItem(
                routeInfo.getType(),
                routeInfo.getDuration(),
                routeInfo.getBusWaitTime(),
                routeInfo.getBusNumber(),
                routeInfo.getStartStopName(),
                routeInfo.getEndStopName(),
                routeInfo.getBusRideTime(),
                routeInfo.getWalkingTimeToStartStop(),
                routeInfo.getWalkingTimeToDestination(),
                routeInfo.getDirectionInfo(),
                routeInfo.getStartStopLat(),
                routeInfo.getStartStopLng(),
                routeInfo.getEndStopLat(),
                routeInfo.getEndStopLng(),
                routeInfo.getDestinationLat(),
                routeInfo.getDestinationLng()
        );
    }

    public static class RouteDataItem {
        @SerializedName("type")
        private String type;

        @SerializedName("duration")
        private int duration;

        @SerializedName("bus_wait_time")
        private int busWaitTime;

        @SerializedName("bus_number")
        private String busNumber;

        @SerializedName("start_stop_name")
        private String startStopName;

        @SerializedName("end_stop_name")
        private String endStopName;

        @SerializedName("bus_ride_time")
        private int busRideTime;

        @SerializedName("walking_time_to_start_stop")
        private int walkingTimeToStartStop;

        @SerializedName("walking_time_to_destination")
        private int walkingTimeToDestination;

        @SerializedName("direction_info")
        private String directionInfo;

        @SerializedName("start_stop_lat")
        private double startStopLat;

        @SerializedName("start_stop_lng")
        private double startStopLng;

        @SerializedName("end_stop_lat")
        private double endStopLat;

        @SerializedName("end_stop_lng")
        private double endStopLng;

        @SerializedName("destination_lat")
        private double destinationLat;

        @SerializedName("destination_lng")
        private double destinationLng;

        public RouteDataItem(String type, int duration, int busWaitTime, String busNumber,
                             String startStopName, String endStopName, int busRideTime,
                             int walkingTimeToStartStop, int walkingTimeToDestination,
                             String directionInfo, double startStopLat, double startStopLng,
                             double endStopLat, double endStopLng, double destinationLat,
                             double destinationLng) {
            this.type = type;
            this.duration = duration;
            this.busWaitTime = busWaitTime;
            this.busNumber = busNumber;
            this.startStopName = startStopName;
            this.endStopName = endStopName;
            this.busRideTime = busRideTime;
            this.walkingTimeToStartStop = walkingTimeToStartStop;
            this.walkingTimeToDestination = walkingTimeToDestination;
            this.directionInfo = directionInfo;
            this.startStopLat = startStopLat;
            this.startStopLng = startStopLng;
            this.endStopLat = endStopLat;
            this.endStopLng = endStopLng;
            this.destinationLat = destinationLat;
            this.destinationLng = destinationLng;
        }

        // Getters (필요한 경우 Setters도 추가)
        public String getType() { return type; }
        public int getDuration() { return duration; }
        public int getBusWaitTime() { return busWaitTime; }
        public String getBusNumber() { return busNumber; }
        public String getStartStopName() { return startStopName; }
        public String getEndStopName() { return endStopName; }
        public int getBusRideTime() { return busRideTime; }
        public int getWalkingTimeToStartStop() { return walkingTimeToStartStop; }
        public int getWalkingTimeToDestination() { return walkingTimeToDestination; }
        public String getDirectionInfo() { return directionInfo; }
        public double getStartStopLat() { return startStopLat; }
        public double getStartStopLng() { return startStopLng; }
        public double getEndStopLat() { return endStopLat; }
        public double getEndStopLng() { return endStopLng; }
        public double getDestinationLat() { return destinationLat; }
        public double getDestinationLng() { return destinationLng; }
    }
}