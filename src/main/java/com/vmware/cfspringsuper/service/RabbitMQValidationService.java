package com.vmware.cfspringsuper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for validating RabbitMQ transactions
 */
@Slf4j
@Service
public class RabbitMQValidationService {

    @Autowired(required = false)
    @Nullable
    private RabbitTemplate rabbitTemplate;
    
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
            // Try to get queue information using RabbitMQ management API pattern
            // Since we may not have management API access, we'll use a workaround:
            // Try to declare and get info for common queues
            List<String> commonQueues = List.of("validation_test_queue", "amq.gen", "amq.default");
            
            for (String queueName : commonQueues) {
                try {
                    Map<String, Object> queueInfo = rabbitTemplate.execute(channel -> {
                        try {
                            channel.queueDeclarePassive(queueName);
                            // Queue exists
                            Map<String, Object> info = new HashMap<>();
                            info.put("name", queueName);
                            info.put("exists", true);
                            return info;
                        } catch (Exception e) {
                            // Queue doesn't exist or we can't access it
                            return null;
                        }
                    });
                    
                    if (queueInfo != null) {
                        queues.add(new HashMap<>(queueInfo));
                    }
                } catch (Exception e) {
                    // Ignore errors for individual queues
                }
            }
        } catch (Exception e) {
            log.warn("Error listing queues: {}", e.getMessage());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("queues", queues);
        result.put("count", queues.size());
        result.put("note", "Full queue listing requires RabbitMQ Management API");
        return result;
    }
}

