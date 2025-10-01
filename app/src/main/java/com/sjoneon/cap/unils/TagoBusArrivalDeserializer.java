package com.sjoneon.cap.unils;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.sjoneon.cap.models.api.TagoBusArrivalResponse;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

// TagoBusArrivalResponse의 "items" 필드를 위한 사용자 정의 Deserializer
// [수정] 단일 객체(Object) 또는 배열(Array) 형태의 "item" 필드를 모두 처리하도록 수정
public class TagoBusArrivalDeserializer implements JsonDeserializer<TagoBusArrivalResponse.Body> {

    @Override
    public TagoBusArrivalResponse.Body deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        TagoBusArrivalResponse.Body body = new TagoBusArrivalResponse.Body();

        JsonObject bodyObject = json.getAsJsonObject();
        JsonElement itemsElement = bodyObject.get("items");

        // items 필드가 비어있거나(예: "") JsonObject가 아닌 경우 (정상: {"item":...} )
        if (itemsElement == null || itemsElement.isJsonNull() || !itemsElement.isJsonObject()) {
            body.items = null;
            return body;
        }

        JsonObject itemsObject = itemsElement.getAsJsonObject();
        JsonElement innerItemElement = itemsObject.get("item");

        // items 객체 내부에 item 필드가 없거나 null인 경우
        if (innerItemElement == null || innerItemElement.isJsonNull()) {
            body.items = null;
            return body;
        }

        List<TagoBusArrivalResponse.BusArrival> busList = new ArrayList<>();

        // [핵심 수정] API 응답이 배열인지 객체인지 확인
        if (innerItemElement.isJsonArray()) {
            // 1. 결과가 여러 개 (배열) -> 그대로 List로 변환
            busList = context.deserialize(innerItemElement, new TypeToken<List<TagoBusArrivalResponse.BusArrival>>(){}.getType());

        } else if (innerItemElement.isJsonObject()) {
            // 2. 결과가 1개 (객체) -> 객체를 1개짜리 List로 만들어서 추가
            TagoBusArrivalResponse.BusArrival singleBus = context.deserialize(innerItemElement, TagoBusArrivalResponse.BusArrival.class);
            busList.add(singleBus);
        }
        // (else: 배열도 객체도 아니면 빈 리스트가 됨)

        // 표준화된 ItemsContainer 객체 생성
        TagoBusArrivalResponse.ItemsContainer container = new TagoBusArrivalResponse.ItemsContainer();
        container.item = busList;

        // RouteFragment가 항상 동일한 구조로 파싱할 수 있도록,
        // {"item": [...]} 형태를 가지는 JsonElement로 변환하여 body에 저장
        body.items = new Gson().toJsonTree(container);

        return body;
    }
}