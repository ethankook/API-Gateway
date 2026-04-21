package com.scheduler.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

  @Value("${scheduler.http-client.connect-timeout-ms:5000}")
  private int connectTimeoutMs;

  @Value("${scheduler.http-client.read-timeout-ms:30000}")
  private int readTimeoutMs;

  @Value("${scheduler.http-client.max-connections:50}")
  private int maxConnections;

  @Bean
  public RestClient schedulerRestClient() {
    PoolingHttpClientConnectionManager connectionManager =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(maxConnections)
            .setMaxConnPerRoute(20)
            .build();

    CloseableHttpClient httpClient =
        HttpClients.custom()
            .setConnectionManager(connectionManager)
            .evictExpiredConnections()
            .evictIdleConnections(TimeValue.ofSeconds(30))
            .build();

    HttpComponentsClientHttpRequestFactory requestFactory =
        new HttpComponentsClientHttpRequestFactory(httpClient);

    requestFactory.setConnectionRequestTimeout(connectTimeoutMs);

    return RestClient.builder()
        .requestFactory(requestFactory)
        .defaultHeader("Content-Type", "application/json")
        .build();
  }
}
