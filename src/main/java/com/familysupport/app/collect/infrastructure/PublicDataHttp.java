package com.familysupport.app.collect.infrastructure;

import com.familysupport.app.collect.domain.FeedException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 공공데이터포털 계열 API GET 호출. 인증키(serviceKey)를 붙이고, 실패하면 정해진 횟수만큼 다시 시도한다.
 * 오류 메시지에는 URL을 넣지 않는다 — URL에 인증키가 들어 있어 수집 기록에 남으면 안 되기 때문.
 */
@Component
public class PublicDataHttp {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String serviceKey;

    public PublicDataHttp(@Value("${family.api.service-key}") String serviceKey) {
        this.serviceKey = serviceKey;
    }

    /** 시도 사이 대기 — 일시 오류가 풀릴 시간을 준다. */
    private static final Duration RETRY_WAIT = Duration.ofSeconds(3);

    /**
     * 호출 후 parser로 해석까지 한다. HTTP 오류·연결 오류뿐 아니라 <b>해석 실패(FeedException)도 다시 시도</b>한다 —
     * 복지로 지자체는 HTTP 200에 본문 resultCode=99(서버 내부 시간 초과로 보임)를 간헐적으로 돌려준다
     * (같은 요청이 0.5초 성공 두 번, 30초 뒤 99 한 번 — 2026-11-16 실측).
     *
     * @param name     오류 메시지에 쓸 호출 이름(예: "복지로 지자체 인천광역시 2쪽")
     * @param timeout  응답 대기 시간
     * @param attempts 최대 시도 횟수
     * @throws FeedException 인증키 없음, 모든 시도 실패(마지막 사유 포함)
     */
    public <T> T get(String name, String baseUrl, Map<String, String> params, Duration timeout, int attempts,
            Function<String, T> parser) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new FeedException("인증키(DATA_GO_KR_KEY)가 설정되지 않았습니다.");
        }
        Map<String, String> all = new LinkedHashMap<>();
        all.put("serviceKey", serviceKey);
        all.putAll(params);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "?" + query(all)))
                .timeout(timeout)
                .header("Accept", "application/json, application/xml")
                .GET()
                .build();
        String lastError = "";
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                if (attempt > 1) {
                    Thread.sleep(RETRY_WAIT.toMillis());
                }
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 200) {
                    return parser.apply(response.body());
                }
                lastError = "HTTP " + response.statusCode();
            } catch (FeedException e) {
                lastError = e.getMessage();
            } catch (IOException e) {
                lastError = e.getClass().getSimpleName();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FeedException(name + " 호출 중단");
            }
        }
        throw new FeedException(name + " 실패(" + attempts + "회 시도): " + lastError);
    }

    /** 이름·값을 URL 인코딩한다. 정부24 조건 이름의 [ ] : 는 그대로 둔다(예: cond[서비스ID::EQ]). */
    static String query(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> encode(e.getKey()).replace("%5B", "[").replace("%5D", "]").replace("%3A", ":")
                        + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
