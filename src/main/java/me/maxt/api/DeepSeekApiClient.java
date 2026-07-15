package me.maxt.api;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class DeepSeekApiClient implements ChatApiClient {

    private final String apiUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    public DeepSeekApiClient(String apiUrl, String apiKey) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public String sendNonStream(String requestBody) throws Exception {
        HttpRequest request = buildRequest(requestBody);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body();
        }
        throw new ApiException(response.statusCode(), response.body());
    }

    @Override
    public InputStream sendStream(String requestBody) throws Exception {
        HttpRequest request = buildRequest(requestBody);
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() == 200) {
            return response.body();
        }
        String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
        throw new ApiException(response.statusCode(), errorBody);
    }

    private HttpRequest buildRequest(String requestBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
    }
}
