package com.sjoneon.cap;

/**
 * 주간 예보 데이터 모델 클래스
 * DB 모델로도 활용 가능하도록 설계되었습니다.
 */
public class WeeklyForecastItem {
    private final String date;          // 날짜 (예: "7/29")
    private final String dayOfWeek;     // 요일 (예: "월요일")
    private final String amCondition;   // 오전 날씨 상태 (예: "맑음")
    private final String pmCondition;   // 오후 날씨 상태 (예: "구름많음")
    private final int amPrecipitation;  // 오전 강수 확률
    private final int pmPrecipitation;  // 오후 강수 확률
    private final int minTemp;          // 최저 기온
    private final int maxTemp;          // 최고 기온

    public WeeklyForecastItem(String date, String dayOfWeek, String amCondition, String pmCondition,
                              int amPrecipitation, int pmPrecipitation, int minTemp, int maxTemp) {
        this.date = date;
        this.dayOfWeek = dayOfWeek;
        this.amCondition = amCondition;
        this.pmCondition = pmCondition;
        this.amPrecipitation = amPrecipitation;
        this.pmPrecipitation = pmPrecipitation;
        this.minTemp = minTemp;
        this.maxTemp = maxTemp;
    }

    // Getter 메서드
    public String getDate() { return date; }
    public String getDayOfWeek() { return dayOfWeek; }
    public String getAmCondition() { return amCondition; }
    public String getPmCondition() { return pmCondition; }
    public int getAmPrecipitation() { return amPrecipitation; }
    public int getPmPrecipitation() { return pmPrecipitation; }
    public int getMinTemp() { return minTemp; }
    public int getMaxTemp() { return maxTemp; }
}