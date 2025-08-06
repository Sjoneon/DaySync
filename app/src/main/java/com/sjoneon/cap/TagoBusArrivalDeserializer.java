package com.sjoneon.cap;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.Collections;

// TagoBusArrivalResponse의 "items" 필드를 위한 사용자 정의 Deserializer
public class TagoBusArrivalDeserializer implements JsonDeserializer<TagoBusArrivalResponse.Body> {

    @Override
    public TagoBusArrivalResponse.Body deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        TagoBusArrivalResponse.Body body = new TagoBusArrivalResponse.Body();

        // items 필드가 객체(Object) 형태인지 확인
        if (json.getAsJsonObject().get("items").isJsonObject()) {
            // 정상적으로 객체일 경우, ItemsContainer 클래스로 파싱
            TagoBusArrivalResponse.ItemsContainer itemsContainer = new Gson().fromJson(json.getAsJsonObject().get("items"), TagoBusArrivalResponse.ItemsContainer.class);
            // 원본 items 필드에 다시 할당 (실제 사용은 아래에서 처리)
            body.items = new Gson().toJsonTree(itemsContainer);
        } else {
            // 객체가 아닐 경우 (예: 빈 문자열 ""), null 또는 빈 객체로 처리
            body.items = null;
        }
        return body;
    }
}