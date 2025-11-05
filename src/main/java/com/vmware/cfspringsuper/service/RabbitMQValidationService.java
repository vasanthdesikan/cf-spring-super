package com.vmware.cfspringsuper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for validating RabbitMQ transactions
 */
@Slf4j
@Service
public class RabbitMQValidationService {

    private final RabbitTemplate rabbitTemplate;
    private static final String DEFAULT_EXCHANGE = "";
    private static final String DEFAULT_QUEUE = "validation_test_queue";

    @Autowired(required = false)
    public RabbitMQValidationService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

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
}

