package com.familysupport.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 임신·출산·육아·신혼 가구 지원사업 한눈에 보기. 공공 API를 매일 수집해 저장하고, 화면은 저장본을 조회한다. */
@SpringBootApplication
@EnableScheduling
public class FamilySupportApplication {

    public static void main(String[] args) {
        SpringApplication.run(FamilySupportApplication.class, args);
    }
}
