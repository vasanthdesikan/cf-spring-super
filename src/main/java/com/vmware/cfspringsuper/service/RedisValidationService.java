package com.vmware.cfspringsuper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Service for validating Redis/Valkey transactions
 */
@Slf4j
@Service
public class RedisValidationService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisValidationService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Map<String, Object> validateTransaction(String operation, Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("service", "Redis/Valkey");
        result.put("operation", operation);

        if (redisTemplate == null) {
            result.put("status", "error");
            result.put("message", "Redis/Valkey service not configured");
            return result;
        }

        try {
            switch (operation.toLowerCase()) {
                case "read":
                case "get":
                    result.putAll(performGet(data));
                    break;
                case "write":
                case "set":
                    result.putAll(performSet(data));
                    break;
                case "delete":
                case "del":
                    result.putAll(performDelete(data));
                    break;
                case "exists":
                    result.putAll(performExists(data));
                    break;
                case "keys":
                    result.putAll(performKeys(data));
                    break;
                default:
                    result.put("status", "error");
                    result.put("message", "Unsupported operation: " + operation);
                    return result;
            }

            result.put("status", "success");
        } catch (Exception e) {
            log.error("Redis/Valkey validation error: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    private Map<String, Object> performGet(Map<String, Object> data) {
        String key = (String) data.getOrDefault("key", "test");
        String value = (String) redisTemplate.opsForValue().get(key);
        
        Map<String, Object> result = new HashMap<>();
        if (value != null) {
            result.put("found", true);
            result.put("key", key);
            result.put("value", value);
        } else {
            result.put("found", false);
            result.put("key", key);
        }
        return result;
    }

    private Map<String, Object> performSet(Map<String, Object> data) {
        String key = (String) data.getOrDefault("key", "test_" + System.currentTimeMillis());
        String value = (String) data.getOrDefault("value", "test_value");
        Integer ttl = data.containsKey("ttl") ? Integer.parseInt(data.get("ttl").toString()) : null;
        
        if (ttl != null && ttl > 0) {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttl));
        } else {
            redisTemplate.opsForValue().set(key, value);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("key", key);
        result.put("value", value);
        result.put("ttl", ttl);
        return result;
    }

    private Map<String, Object> performDelete(Map<String, Object> data) {
        String key = (String) data.getOrDefault("key", "test");
        Boolean deleted = redisTemplate.delete(key);
        
        Map<String, Object> result = new HashMap<>();
        result.put("key", key);
        result.put("deleted", deleted);
        return result;
    }

    private Map<String, Object> performExists(Map<String, Object> data) {
        String key = (String) data.getOrDefault("key", "test");
        Boolean exists = redisTemplate.hasKey(key);
        
        Map<String, Object> result = new HashMap<>();
        result.put("key", key);
        result.put("exists", exists);
        return result;
    }

    private Map<String, Object> performKeys(Map<String, Object> data) {
        String pattern = (String) data.getOrDefault("pattern", "*");
        Set<String> keys = redisTemplate.keys(pattern);
        
        Map<String, Object> result = new HashMap<>();
        result.put("pattern", pattern);
        result.put("count", keys != null ? keys.size() : 0);
        result.put("keys", keys);
        return result;
    }
}

