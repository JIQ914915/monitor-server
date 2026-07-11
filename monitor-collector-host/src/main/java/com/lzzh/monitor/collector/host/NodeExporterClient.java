package com.lzzh.monitor.collector.host;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** node_exporter HTTP 客户端：拉取 /metrics 文本。 */
@Component
public class NodeExporterClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient httpClient;

    public NodeExporterClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * 拉取 exporter 指标文本。
     *
     * @throws IOException 连接失败、超时或非 200 响应
     */
    public String fetch(String ip, int port, String path) throws IOException, InterruptedException {
        String normalizedPath = path == null || path.isBlank() ? "/metrics"
                : (path.startsWith("/") ? path : "/" + path);
        URI uri = URI.create("http://" + ip + ":" + port + normalizedPath);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("exporter 返回 HTTP " + response.statusCode() + "：" + uri);
        }
        return response.body();
    }
}
