package com.fashionagent.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fashionagent.schedule.place.KakaoLocalClient;
import com.fashionagent.schedule.place.PlaceInfo;
import com.sun.net.httpserver.HttpServer;

/** 카카오 응답 모양을 흉내 낸 로컬 서버로 파싱과 실패 처리를 확인합니다. */
class KakaoLocalClientTest {

    private HttpServer server;
    private final AtomicReference<String> auth = new AtomicReference<>();
    private volatile int status = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/local/search/keyword.json", ex -> {
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] body = """
                    {"documents":[{"place_name":"더채플앳청담","category_name":"가정,생활 > 예식장","x":"127.0","y":"37.5"}],
                     "meta":{"total_count":1}}""".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
            ex.sendResponseHeaders(status, status == 200 ? body.length : -1);
            if (status == 200) {
                ex.getResponseBody().write(body);
            }
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void 장소_종류를_읽는다() {
        PlaceInfo info = new KakaoLocalClient("test-key", baseUrl()).find("더채플앳청담").orElseThrow();
        assertThat(info.name()).isEqualTo("더채플앳청담");
        assertThat(info.categoryName()).isEqualTo("가정,생활 > 예식장");
        assertThat(info.category()).isEqualTo("예식장");
        assertThat(auth.get()).isEqualTo("KakaoAK test-key");
    }

    @Test
    void 실패하면_빈_값() {
        status = 401;
        assertThat(new KakaoLocalClient("bad-key", baseUrl()).find("더채플앳청담")).isEmpty();
    }

    @Test
    void 키가_없으면_부르지_않는다() {
        assertThat(new KakaoLocalClient("", baseUrl()).find("더채플앳청담")).isEmpty();
        assertThat(auth.get()).isNull();
    }
}
