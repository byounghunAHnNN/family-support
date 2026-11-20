package com.familysupport.app.collect.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familysupport.app.collect.domain.FeedException;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 재시도 규칙 — 로컬 가짜 서버로 복지로의 "HTTP 200 + resultCode=99" 간헐 오류를 흉내 낸다. */
class PublicDataHttpTest {

    private static final String OK = "<r><resultCode>0</resultCode><totalCount>1</totalCount><servList><servId>1</servId></servList></r>";
    private static final String ERROR_99 = "<r><resultCode>99</resultCode><resultMessage>UNKNOWN_ERROR</resultMessage></r>";
    private static final String SECRET = "SECRET-KEY-1234";

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private List<String> responses;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/list", exchange -> {
            int n = calls.getAndIncrement();
            byte[] body = responses.get(Math.min(n, responses.size() - 1)).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/list";
    }

    @Test
    void 본문_오류면_다시_시도해_성공한다() {
        responses = List.of(ERROR_99, OK);
        XmlItems.Page page = new PublicDataHttp(SECRET).get("테스트", url(), Map.of("pageNo", "1"),
                Duration.ofSeconds(5), 4, body -> XmlItems.parse(body, "servList"));
        assertThat(page.items()).hasSize(1);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void 끝까지_실패하면_마지막_사유를_담되_인증키는_넣지_않는다() {
        responses = List.of(ERROR_99);
        assertThatThrownBy(() -> new PublicDataHttp(SECRET).get("테스트", url(), Map.of(),
                Duration.ofSeconds(5), 2, body -> XmlItems.parse(body, "servList")))
                .isInstanceOf(FeedException.class)
                .hasMessageContaining("2회 시도")
                .hasMessageContaining("UNKNOWN_ERROR")
                .hasMessageNotContaining(SECRET);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void 인증키가_없으면_호출하지_않는다() {
        responses = List.of(OK);
        assertThatThrownBy(() -> new PublicDataHttp("").get("테스트", url(), Map.of(),
                Duration.ofSeconds(5), 1, body -> body))
                .isInstanceOf(FeedException.class)
                .hasMessageContaining("DATA_GO_KR_KEY");
        assertThat(calls.get()).isZero();
    }
}
