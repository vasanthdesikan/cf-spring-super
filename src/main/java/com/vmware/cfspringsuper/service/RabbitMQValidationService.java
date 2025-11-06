package com.vmware.cfspringsuper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for validating RabbitMQ transactions
 */
@Slf4j
@Service
public class RabbitMQValidationService {

    @Autowired(required = false)
    @Nullable
    private RabbitTemplate rabbitTemplate;
    
    @Autowired
    private Map<String, List<com.vmware.cfspringsuper.config.VcapServicesConfig.ServiceCredentials>> serviceCredentials;
    
    private static final String DEFAULT_EXCHANGE = "";
    private static final String DEFAULT_QUEUE = "validation_test_queue";

    public Map<String, Object> validateTransaction(String operation, Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("service", "RabbitMQ");
        result.put("operation", operation);

        if (rabbitTemplate == null) {
            result.put("status", "error");
            result.put("message", "RabbitMQ service not configured");
            return result;
        }

        try {
            switch (operation.toLowerCase()) {
                case "send":
                case "publish":
                    result.putAll(performSend(data));
                    break;
                case "receive":
                case "consume":
                    result.putAll(performReceive(data));
                    break;
                case "queue":
                    result.putAll(performQueueInfo(data));
                    break;
                case "listqueues":
                    result.putAll(performListQueues());
                    break;
                default:
                    result.put("status", "error");
                    result.put("message", "Unsupported operation: " + operation);
                    return result;
            }

            result.put("status", "success");
        } catch (Exception e) {
            log.error("RabbitMQ validation error: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    private Map<String, Object> performSend(Map<String, Object> data) {
        String exchange = (String) data.getOrDefault("exchange", DEFAULT_EXCHANGE);
        String routingKey = (String) data.getOrDefault("routingKey", DEFAULT_QUEUE);
        String message = (String) data.getOrDefault("message", "test_message_" + System.currentTimeMillis());
        
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        
        Map<String, Object> result = new HashMap<>();
        result.put("exchange", exchange.isEmpty() ? "(default)" : exchange);
        result.put("routingKey", routingKey);
        result.put("message", message);
        result.put("sent", true);
        return result;
    }

    private Map<String, Object> performReceive(Map<String, Object> data) {
        String queue = (String) data.getOrDefault("queue", DEFAULT_QUEUE);
        
        // Try to receive a message (with timeout)
        Object received = rabbitTemplate.receiveAndConvert(queue, 5000);
        
        Map<String, Object> result = new HashMap<>();
        result.put("queue", queue);
        if (received != null) {
            result.put("received", true);
            result.put("message", received.toString());
        } else {
            result.put("received", false);
            result.put("message", "No message available in queue");
        }
        return result;
    }

    private Map<String, Object> performQueueInfo(Map<String, Object> data) {
        String queue = (String) data.getOrDefault("queue", DEFAULT_QUEUE);
        
        // Declare queue to ensure it exists
        try {
            rabbitTemplate.execute(channel -> {
                channel.queueDeclare(queue, true, false, false, null);
                return null;
            });
        } catch (Exception e) {
            log.warn("Error declaring queue: {}", e.getMessage());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("queue", queue);
        result.put("declared", true);
        result.put("note", "Queue information retrieval requires management API");
        return result;
    }

    private Map<String, Object> performListQueues() {
        List<Map<String, Object>> queues = new ArrayList<>();
        
        try {
            // Try to use RabbitMQ Management API if available
            String managementUri = getManagementUri();
            if (managementUri != null && !managementUri.isEmpty()) {
                queues = listQueuesViaManagementApi(managementUri);
            } else {
                // Fallback: try to list queues using AMQP channel
                queues = listQueuesViaAmqp();
            }
        } catch (Exception e) {
            log.warn("Error listing queues: {}", e.getMessage());
            // Fallback to AMQP method
            try {
                queues = listQueuesViaAmqp();
            } catch (Exception ex) {
                log.error("Error listing queues via AMQP: {}", ex.getMessage());
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("queues", queues);
        result.put("count", queues.size());
        return result;
    }
    
    private String getManagementUri() {
        // Get management URI from VCAP_SERVICES
        List<com.vmware.cfspringsuper.config.VcapServicesConfig.ServiceCredentials> rabbitServices = 
            serviceCredentials.get("rabbitmq");
        if (rabbitServices != null && !rabbitServices.isEmpty()) {
            String uri = rabbitServices.get(0).getManagementUri();
            if (uri != null && !uri.isEmpty()) {
                return uri + "/api/queues";
            }
        }
        return null;
    }
    
    private List<Map<String, Object>> listQueuesViaManagementApi(String managementUri) {
        List<Map<String, Object>> queues = new ArrayList<>();
        try {
            HttpClient client = HttpClient.newHttpClient();
            
            // Get credentials from VCAP_SERVICES
            String username = "guest";
            String password = "guest";
            List<com.vmware.cfspringsuper.config.VcapServicesConfig.ServiceCredentials> rabbitServices = 
                serviceCredentials.get("rabbitmq");
            if (rabbitServices != null && !rabbitServices.isEmpty()) {
                com.vmware.cfspringsuper.config.VcapServicesConfig.ServiceCredentials creds = rabbitServices.get(0);
                if (creds.getUsername() != null) {
                    username = creds.getUsername();
                }
                if (creds.getPassword() != null) {
                    password = creds.getPassword();
                }
            }
            
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(managementUri))
                .header("Authorization", "Basic " + encodedAuth)
                .GET()
                .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode queuesJson = mapper.readTree(response.body());
                
                for (JsonNode queue : queuesJson) {
                    Map<String, Object> queueInfo = new HashMap<>();
                    queueInfo.put("name", queue.get("name").asText());
                    queueInfo.put("messages", queue.has("messages") ? queue.get("messages").asInt() : 0);
                    queueInfo.put("consumers", queue.has("consumers") ? queue.get("consumers").asInt() : 0);
                    queueInfo.put("vhost", queue.has("vhost") ? queue.get("vhost").asText() : "/");
                    queues.add(queueInfo);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list queues via Management API: {}", e.getMessage());
        }
        return queues;
    }
    
    private List<Map<String, Object>> listQueuesViaAmqp() {
        List<Map<String, Object>> queues = new ArrayList<>();
        try {
            // Use AMQP to try discovering queues
            // Note: This is limited - AMQP doesn't have a standard way to list all queues
            // We can only check specific queue names
            rabbitTemplate.execute(channel -> {
                // Try common queue patterns
                java.util.List<String> commonQueuePatterns = java.util.List.of(
                    "amq.gen-", "amq.default", "validation_test_queue"
                );
                
                for (String pattern : commonQueuePatterns) {
                    try {
                        if (pattern.endsWith("-")) {
                            // Dynamic queues - try to enumerate
                            for (int i = 0; i < 100; i++) {
                                String queueName = pattern + i;
                                try {
                                    channel.queueDeclarePassive(queueName);
                                    Map<String, Object> info = new HashMap<>();
                                    info.put("name", queueName);
                                    info.put("exists", true);
                                    queues.add(info);
                                } catch (Exception e) {
                                    // Queue doesn't exist
                                    break;
                                }
                            }
                        } else {
                            channel.queueDeclarePassive(pattern);
                            Map<String, Object> info = new HashMap<>();
                            info.put("name", pattern);
                            info.put("exists", true);
                            queues.add(info);
                        }
                    } catch (Exception e) {
                        // Queue doesn't exist
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Error listing queues via AMQP: {}", e.getMessage());
        }
        return queues;
    }
}

