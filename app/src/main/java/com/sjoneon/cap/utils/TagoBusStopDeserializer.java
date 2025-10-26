package com.sjoneon.cap.utils;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.sjoneon.cap.models.api.TagoBusStopResponse;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * TagoBusStopResponse의 items 필드 파싱을 위한 Custom Deserializer
 * API가 데이터 없을 때 "NO_DATA" 문자열을 반환하는 문제 해결
 */
public class TagoBusStopDeserializer implements JsonDeserializer<TagoBusStopResponse.Body> {

    @Override
    public TagoBusStopResponse.Body deserialize(JsonElement json, Type typeOfT,
                                                JsonDeserializationContext context)
            throws JsonParseException {

        TagoBusStopResponse.Body body = new TagoBusStopResponse.Body();

        JsonObject bodyObject = json.getAsJsonObject();
        JsonElement itemsElement = bodyObject.get("items");

        // items 필드가 없거나 null인 경우
        if (itemsElement == null || itemsElement.isJsonNull()) {
            body.items = null;
            return body;
        }

        // items가 문자열인 경우 (예: "NO_DATA")
        if (itemsElement.isJsonPrimitive() && itemsElement.getAsJsonPrimitive().isString()) {
            body.items = null;
            return body;
        }

        // items가 객체가 아닌 경우
        if (!itemsElement.isJsonObject()) {
            body.items = null;
            return body;
        }

        JsonObject itemsObject = itemsElement.getAsJsonObject();
        JsonElement innerItemElement = itemsObject.get("item");

        // item 필드가 없거나 null인 경우
        if (innerItemElement == null || innerItemElement.isJsonNull()) {
            body.items = null;
            return body;
        }

        List<TagoBusStopResponse.BusStop> stopList = new ArrayList<>();

        // 배열 또는 단일 객체 처리
        if (innerItemElement.isJsonArray()) {
            stopList = context.deserialize(innerItemElement,
                    new TypeToken<List<TagoBusStopResponse.BusStop>>(){}.getType());
        } else if (innerItemElement.isJsonObject()) {
            TagoBusStopResponse.BusStop singleStop = context.deserialize(innerItemElement,
                    TagoBusStopResponse.BusStop.class);
            stopList.add(singleStop);
        }

        // Items 컨테이너 생성
        TagoBusStopResponse.Items container = new TagoBusStopResponse.Items();
        container.item = stopList;

        // JsonElement로 변환하여 body에 저장
        body.items = new Gson().toJsonTree(container);

        return body;
    }
}