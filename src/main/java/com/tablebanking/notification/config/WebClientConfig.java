package com.tablebanking.notification.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import javax.net.ssl.SSLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class WebClientConfig {

    @Value("${webclient.connection.timeout-ms:5000}")
    private int connectionTimeout;

    @Value("${webclient.read.timeout-ms:30000}")
    private int readTimeout;

    @Value("${webclient.write.timeout-ms:10000}")
    private int writeTimeout;

    @Value("${webclient.response.timeout-ms:30000}")
    private int responseTimeout;

    @Value("${webclient.pool.max-connections:500}")
    private int maxConnections;

    @Value("${webclient.pool.max-idle-time-ms:20000}")
    private int maxIdleTime;

    @Value("${webclient.pool.max-life-time-ms:60000}")
    private int maxLifeTime;

    @Value("${webclient.pool.pending-acquire-timeout-ms:45000}")
    private int pendingAcquireTimeout;

    @Value("${webclient.pool.evict-in-background-ms:30000}")
    private int evictInBackground;

    @Value("${webclient.ssl.trust-all:false}")
    private boolean trustAllCertificates;

    @Value("${webclient.logging.enabled:true}")
    private boolean loggingEnabled;

    @Value("${webclient.max-in-memory-size-mb:10}")
    private int maxInMemorySizeMb;

    /**
     * Primary WebClient bean for general HTTP calls
     */
    @Bean
    public WebClient webClient() {
        return createBaseWebClientBuilder()
                .filter(addCorrelationId())
                .build();
    }

    /**
     * WebClient specifically configured for Infobip SMS API
     */
    @Bean("infobipWebClient")
    public WebClient infobipWebClient(
            @Value("${infobip.base-url:https://api.infobip.com}") String baseUrl,
            @Value("${infobip.api-key:}") String apiKey) {

        return createBaseWebClientBuilder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "App " + apiKey)
                .filter(addCorrelationId())
                .filter(handleErrors())
                .build();
    }

    /**
     * WebClient for webhook/callback endpoints (external services calling us)
     */
    @Bean("webhookWebClient")
    public WebClient webhookWebClient() {
        return createBaseWebClientBuilder()
                .build();
    }

    /**
     * Create a base WebClient.Builder with common configuration
     */
    private WebClient.Builder createBaseWebClientBuilder() {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient()))
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(maxInMemorySizeMb * 1024 * 1024))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequest())
                .filter(logResponse());
    }

    /**
     * Configure HTTP client with connection pooling and timeouts
     */
    private HttpClient httpClient() {
        // Connection provider with pooling
        ConnectionProvider connectionProvider = ConnectionProvider.builder("notification-pool")
                .maxConnections(maxConnections)
                .maxIdleTime(Duration.ofMillis(maxIdleTime))
                .maxLifeTime(Duration.ofMillis(maxLifeTime))
                .pendingAcquireTimeout(Duration.ofMillis(pendingAcquireTimeout))
                .evictInBackground(Duration.ofMillis(evictInBackground))
                .metrics(true) // Enable metrics for monitoring
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
                .responseTimeout(Duration.ofMillis(responseTimeout))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeout, TimeUnit.MILLISECONDS)))
                .compress(true) // Enable compression
                .keepAlive(true); // Enable keep-alive

        // Configure SSL if needed
        if (trustAllCertificates) {
            log.warn("SSL certificate validation is DISABLED. This should only be used in development!");
            httpClient = httpClient.secure(sslContextSpec -> {
                try {
                    sslContextSpec.sslContext(
                            SslContextBuilder.forClient()
                                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                    .build()
                    );
                } catch (SSLException e) {
                    throw new RuntimeException("Failed to configure SSL context", e);
                }
            });
        }

        return httpClient;
    }

    /**
     * Log outgoing requests
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            if (loggingEnabled) {
                log.info("HTTP Request: {} {} | Correlation-ID: {}",
                        clientRequest.method(),
                        clientRequest.url(),
                        clientRequest.headers().getFirst("X-Correlation-ID"));

                if (log.isDebugEnabled()) {
                    clientRequest.headers().forEach((name, values) -> {
                        if (!name.equalsIgnoreCase(HttpHeaders.AUTHORIZATION)) {
                            values.forEach(value -> log.debug("Request Header: {}={}", name, value));
                        } else {
                            log.debug("Request Header: {}=[REDACTED]", name);
                        }
                    });
                }
            }
            return Mono.just(clientRequest);
        });
    }

    /**
     * Log incoming responses
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (loggingEnabled) {
                log.info("HTTP Response: Status {} | Correlation-ID: {}",
                        clientResponse.statusCode(),
                        clientResponse.headers().asHttpHeaders().getFirst("X-Correlation-ID"));

                if (log.isDebugEnabled()) {
                    clientResponse.headers().asHttpHeaders().forEach((name, values) ->
                            values.forEach(value -> log.debug("Response Header: {}={}", name, value)));
                }
            }
            return Mono.just(clientResponse);
        });
    }

    /**
     * Add correlation ID to all requests for tracing
     */
    private ExchangeFilterFunction addCorrelationId() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            String correlationId = clientRequest.headers().getFirst("X-Correlation-ID");
            if (correlationId == null || correlationId.isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }

            return Mono.just(ClientRequest.from(clientRequest)
                    .header("X-Correlation-ID", correlationId)
                    .header("X-Request-ID", UUID.randomUUID().toString())
                    .build());
        });
    }

    /**
     * Handle common HTTP errors
     */
    private ExchangeFilterFunction handleErrors() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (clientResponse.statusCode().isError()) {
                return clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("No response body")
                        .flatMap(body -> {
                            log.error("HTTP Error: {} - {}",
                                    clientResponse.statusCode(),
                                    truncateBody(body));

                            // Return the response as-is, let the caller handle it
                            return Mono.just(clientResponse);
                        });
            }
            return Mono.just(clientResponse);
        });
    }

    /**
     * Truncate response body for logging
     */
    private String truncateBody(String body) {
        int maxLength = 500;
        if (body != null && body.length() > maxLength) {
            return body.substring(0, maxLength) + "...[truncated]";
        }
        return body;
    }
}