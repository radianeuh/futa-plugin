package com.github.futa.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.futa.dto.ApiResponse;
import com.github.futa.dto.ConfirmRequest;
import com.github.futa.dto.Ticket;
import com.github.futa.dto.TicketRequest;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class NodeClient {
    private final String serverUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    public ComponentLogger log;

    public NodeClient(String serverUrl) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();

        log = ComponentLogger.logger("NodeClient");
    }

    /**
     * 请求取票
     */
    public Ticket requestTicket() {
        try {
            TicketRequest request = new TicketRequest();
            String requestBody = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/queue/ticket/request"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("取票请求失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
                return null;
            }

            ApiResponse<Ticket> apiResponse = objectMapper.readValue(response.body(),
                    new TypeReference<ApiResponse<Ticket>>() {
                    });

            if (apiResponse.isSuccess()) {
                log.info("节点 取票成功: 号码 {}, 票据ID: {}",
                        apiResponse.getData().getNumber(), apiResponse.getData().getTicketId());
                return apiResponse.getData();
            } else {
                log.warn("节点 取票失败: {}", apiResponse.getMessage());
                return null;
            }

        } catch (Exception e) {
            log.error("节点 取票异常", e);
            return null;
        }
    }

    /**
     * 确认票据
     */
    public boolean confirmTicket(String ticketId, boolean justLogin) {
        try {
            ConfirmRequest request = new ConfirmRequest(ticketId);
            request.setJustLogin(justLogin);

            String requestBody = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/queue/ticket/confirm"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("确认票据请求失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
                return false;
            }

            ApiResponse apiResponse = objectMapper.readValue(response.body(),
                    new TypeReference<>() {
                    });

            if (apiResponse.isSuccess()) {
                log.info("节点确认票据 {} 成功", ticketId);
                return true;
            } else {
                log.warn("节点确认票据 {} 失败: {}", ticketId, apiResponse.getMessage());
                return false;
            }

        } catch (Exception e) {
            log.error("节点确认票据 {} 异常", ticketId, e);
            return false;
        }
    }


    /**
     * 检查服务器健康状态
     */
    public boolean isServerHealthy() {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/api/queue/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;

        } catch (Exception e) {
            log.debug("健康检查失败", e);
            return false;
        }
    }

    /**
     * 异步请求取票
     */
    public CompletableFuture<Ticket> requestTicketAsync() {
        return CompletableFuture.supplyAsync(this::requestTicket);
    }

    /**
     * 异步确认票据
     */
    public CompletableFuture<Boolean> confirmTicketAsync(String ticketId, boolean justLogin) {
        return CompletableFuture.supplyAsync(() -> confirmTicket(ticketId, justLogin));
    }

    /**
     * 模拟业务处理（验票过程）
     */
    public void simulateBusinessProcess(int processTimeSeconds) {
        try {
            log.info("节点 开始处理业务，预计耗时 {} 秒", processTimeSeconds);
            Thread.sleep(processTimeSeconds * 1000L);
            log.info("节点 业务处理完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("节点 业务处理被中断");
        }
    }

    /**
     * 完整的取票和确认流程
     */
    public boolean processTicket(int businessProcessSeconds) {
        // 1. 请求取票
        Ticket ticket = requestTicket();
        if (ticket == null) {
            return false;
        }

        try {
            // 2. 模拟业务处理
            simulateBusinessProcess(businessProcessSeconds);

            // 3. 确认票据
            return confirmTicket(ticket.getTicketId(), false);

        } catch (Exception e) {
            log.error("节点 处理票据 {} 时发生异常", ticket.getTicketId(), e);
            return false;
        }
    }


    public String getServerUrl() {
        return serverUrl;
    }
}
