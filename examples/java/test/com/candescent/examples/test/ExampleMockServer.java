package com.candescent.examples.test;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-process HTTP mock for example subprocess runs (mirrors preload-mock.ts + mock-fetch.ts).
 */
final class ExampleMockServer implements AutoCloseable {
    private final MockWebServer server = new MockWebServer();

    ExampleMockServer() throws IOException {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String url = request.getRequestUrl() != null
                        ? request.getRequestUrl().toString()
                        : "";
                String method = request.getMethod() != null ? request.getMethod() : "GET";
                return MockFixtures.defaultFixtureForUrl(url, method);
            }
        });
        server.start();
    }

    String baseUrl() {
        return server.url("/").toString().replaceAll("/$", "");
    }

    Map<String, String> mockEnvOverrides() {
        String base = baseUrl();
        Map<String, String> env = new LinkedHashMap<>();
        env.put("CANDESCENT_MOCK_HTTP", "1");
        env.put("CANDESCENT_BASE_URL", base);
        env.put("CANDESCENT_APIGEE_BASE_URL", base);
        return env;
    }

    @Override
    public void close() throws IOException {
        server.shutdown();
    }
}
