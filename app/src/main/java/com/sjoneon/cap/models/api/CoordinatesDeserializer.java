package com.sjoneon.cap.models.api;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * TMAP API의 다양한 형태의 'coordinates' JSON 구조를 파싱하기 위한 사용자 정의 Deserializer
 */
public class CoordinatesDeserializer implements JsonDeserializer<List<List<Double>>> {

    @Override
    public List<List<Double>> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        List<List<Double>> result = new ArrayList<>();
        if (!json.isJsonArray()) {
            return result;
        }

        // coordinates 배열을 순회
        for (JsonElement element : json.getAsJsonArray()) {
            // 배열의 각 항목이 또 다른 배열(좌표 쌍)인 경우
            if (element.isJsonArray()) {
                List<Double> coordPair = new ArrayList<>();
                boolean isValidPair = true;
                for (JsonElement coord : element.getAsJsonArray()) {
                    if (coord.isJsonPrimitive() && coord.getAsJsonPrimitive().isNumber()) {
                        coordPair.add(coord.getAsDouble());
                    } else {
                        isValidPair = false;
                        break;
                    }
                }
                if (isValidPair && coordPair.size() >= 2) {
                    result.add(coordPair);
                }
            }
        }
        return result;
    }
}