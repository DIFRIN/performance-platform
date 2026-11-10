package com.performance.platform.transport.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.performance.platform.transport.AgentLifecycleEvent;
import com.performance.platform.transport.codec.AgentSignalDecoder;
import com.performance.platform.transport.config.HttpListenerProperties;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;
import com.performance.platform.transport.tls.TransportTls;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class HttpTransportListener implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HttpTransportListener.class);
    private static final int MAX_REQUEST_BYTES = 16 * 1024 * 1024;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    HttpTransportListener(HttpListenerProperties props, TransportTls tls, String basePath,
                          boolean agent, ObjectMapper mapper, HttpExecutionTransport transport) throws IOException {
        var address = new InetSocketAddress(props.bindAddress(), props.port());
        if (tls.enabled()) {
            tls.requireIdentity();
            var https = HttpsServer.create(address, 0);
            https.setHttpsConfigurator(new HttpsConfigurator(tls.context()) {
                @Override
                public void configure(HttpsParameters parameters) {
                    parameters.setSSLParameters(tls.parameters(false));
                }
            });
            server = https;
        } else {
            server = HttpServer.create(address, 0);
        }
        server.setExecutor(executor);
        server.createContext(basePath, exchange -> {
            try (exchange) {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                boolean allowed = agent ? path.equals(basePath) || path.equals(basePath + "/signals")
                        : path.equals(basePath + "/events") || path.equals(basePath + "/agents");
                if (!allowed) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                var body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
                if (body.length > MAX_REQUEST_BYTES) {
                    exchange.sendResponseHeaders(413, -1);
                    return;
                }
                try {
                    if (agent && path.equals(basePath)) {
                        transport.acceptTask(mapper.readValue(body, TaskExecutionRequest.class));
                    } else if (agent) {
                        transport.acceptSignal(AgentSignalDecoder.decode(mapper, mapper.readTree(body)));
                    } else if (path.equals(basePath + "/events")) {
                        transport.acceptEvent(mapper.readValue(body, ExecutionEvent.class));
                    } else {
                        transport.acceptAgentEvent(mapper.readValue(body, AgentLifecycleEvent.class));
                    }
                    exchange.sendResponseHeaders(202, -1);
                } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
                    log.warn("action=transport_http_invalid_request path={} error={}", path, e.getMessage());
                    exchange.sendResponseHeaders(400, -1);
                } catch (RuntimeException e) {
                    log.error("action=transport_http_handler_failed path={}", path, e);
                    exchange.sendResponseHeaders(500, -1);
                }
            } catch (IOException e) {
                log.warn("action=transport_http_io_failed error={}", e.getMessage());
            }
        });
    }

    void start() {
        server.start();
        log.info("action=transport_http_listener_started address={}", server.getAddress());
    }

    int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdown();
    }
}
