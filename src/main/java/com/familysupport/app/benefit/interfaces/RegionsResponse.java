package com.familysupport.app.benefit.interfaces;

import java.util.List;

/** 화면 지역 선택 목록. */
public record RegionsResponse(List<Sido> sidos) {

    public record Sido(String name, List<String> sigungus) {
    }
}
