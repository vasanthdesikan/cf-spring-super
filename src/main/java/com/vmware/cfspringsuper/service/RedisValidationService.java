package com.vmware.cfspringsuper.service;

import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandInterruptedException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for validating Redis/Valkey transactions
 * Implements proper exception handling based on Lettuce Redis Java client best practices
 */
@Slf4j
@Service
public class RedisValidationService {

    @Autowired(required = false)
    @Nullable
    private RedisTemplate<String, Object> redisTemplate;

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
                case "listall":
                    result.putAll(performListAll(data));
                    break;
                default:
                    result.put("status", "error");
                    result.put("message", "Unsupported operation: " + operation);
                    return result;
            }

            result.put("status", "success");
        } catch (RedisConnectionException e) {
            log.error("Redis connection error: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("errorType", "RedisConnectionException");
            result.put("message", "Failed to connect to Redis: " + e.getMessage());
            result.put("suggestion", "Check if Redis server is running, network connectivity, and configuration (host/port/password)");
        } catch (RedisCommandTimeoutException e) {
            log.error("Redis command timeout: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("errorType", "RedisCommandTimeoutException");
            result.put("message", "Command timed out: " + e.getMessage());
            result.put("suggestion", "Redis server may be overloaded or network latency is high. Consider increasing timeout or optimizing the command");
        } catch (RedisCommandInterruptedException e) {
            log.error("Redis command interrupted: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("errorType", "RedisCommandInterruptedException");
            result.put("message", "Command was interrupted: " + e.getMessage());
            result.put("suggestion", "Command execution was interrupted. This may occur during application shutdown or thread interruption");
        } catch (RedisCommandExecutionException e) {
            log.error("Redis command execution error: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("errorType", "RedisCommandExecutionException");
            result.put("message", "Command execution failed: " + e.getMessage());
            result.put("suggestion", "Check command syntax, data types, and Redis server error message for details");
        } catch (RedisException e) {
            log.error("Redis error: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("errorType", "RedisException");
            result.put("message", "Redis operation failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during Redis operation: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("errorType", e.getClass().getSimpleName());
            result.put("message", "Unexpected error: " + e.getMessage());
        }

        return result;
    }

    private Map<String, Object> performGet(Map<String, Object> data) {
        String key = (String) data.getOrDefault("key", "test");
        
        try {
            String value = (String) redisTemplate.opsForValue().get(key);
            
            Map<String, Object> result = new HashMap<>();
            if (value != null) {
                result.put("found", true);
                result.put("key", key);
                result.put("value", value);
            } else {
                result.put("found", false);
                result.put("key", key);
                result.put("message", "Key does not exist");
            }
            return result;
        } catch (Exception e) {
            log.error("Error performing GET operation for key {}: {}", key, e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> performSet(Map<String, Object> data) {
        String key = (String) data.getOrDefault("key", "test_" + System.currentTimeMillis());
        String value = (String) data.getOrDefault("value", "test_value");
        Integer ttl = null;
        
        // Parse TTL if provided
        if (data.containsKey("ttl")) {
            Object ttlObj = data.get("ttl");
            if (ttlObj instanceof Number) {
                ttl = ((Number) ttlObj).intValue();
            } else if (ttlObj != null) {
                try {
                    ttl = Integer.parseInt(ttlObj.toString());
                } catch (NumberFormatException e) {
                    log.warn("Invalid TTL value: {}, ignoring", ttlObj);
                }
            }
        }
        
        try {
            if (ttl != null && ttl > 0) {
                redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttl));
                log.debug("Set key {} with value and TTL {} seconds", key, ttl);
            } else {
                redisTemplate.opsForValue().set(key, value);
                log.debug("Set key {} with value (no TTL)", key);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("key", key);
            result.put("value", value);
            result.put("ttl", ttl);
            result.put("set", true);
            return result;
        } catch (Exception e) {
            log.error("Error performing SET operation for key {}: {}", key, e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> performDelete(Map<String, Object> data) {
        String key = (String) data.getOrDefault("key", "test");
        
        try {
            Boolean deleted = redisTemplate.delete(key);
            
            Map<String, Object> result = new HashMap<>();
            result.put("key", key);
            result.put("deleted", deleted != null && deleted);
            return result;
        } catch (Exception e) {
            log.error("Error performing DELETE operation for key {}: {}", key, e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> performExists(Map<String, Object> data) {
        String key = (String) data.getOrDefault("key", "test");
        
        try {
            Boolean exists = redisTemplate.hasKey(key);
            
            Map<String, Object> result = new HashMap<>();
            result.put("key", key);
            result.put("exists", exists != null && exists);
            return result;
        } catch (Exception e) {
            log.error("Error performing EXISTS operation for key {}: {}", key, e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> performKeys(Map<String, Object> data) {
        String pattern = (String) data.getOrDefault("pattern", "*");
        
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            
            Map<String, Object> result = new HashMap<>();
            result.put("pattern", pattern);
            result.put("count", keys != null ? keys.size() : 0);
            result.put("keys", keys != null ? new ArrayList<>(keys) : new ArrayList<>());
            
            if (keys != null && keys.size() > 1000) {
                result.put("warning", "Large number of keys returned. Consider using SCAN for production use.");
            }
            
            return result;
        } catch (Exception e) {
            log.error("Error performing KEYS operation with pattern {}: {}", pattern, e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> performListAll(Map<String, Object> data) {
        String pattern = (String) data.getOrDefault("pattern", "*");
        
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            
            List<Map<String, Object>> keyValuePairs = new ArrayList<>();
            if (keys != null) {
                int processed = 0;
                int maxKeys = 1000; // Limit to prevent memory issues
                
                for (String key : keys) {
                    if (processed >= maxKeys) {
                        log.warn("Limiting key-value pairs to {} to prevent memory issues", maxKeys);
                        break;
                    }
                    
                    try {
                        Object value = redisTemplate.opsForValue().get(key);
                        Map<String, Object> pair = new HashMap<>();
                        pair.put("key", key);
                        pair.put("value", value != null ? value.toString() : null);
                        keyValuePairs.add(pair);
                        processed++;
                    } catch (Exception e) {
                        log.warn("Error getting value for key {}: {}", key, e.getMessage());
                        // Continue with next key
                    }
                }
                
                if (keys.size() > maxKeys) {
                    log.warn("Only processed {} of {} keys. Consider using a more specific pattern.", maxKeys, keys.size());
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("keyValuePairs", keyValuePairs);
            result.put("count", keyValuePairs.size());
            result.put("pattern", pattern);
            
            return result;
        } catch (Exception e) {
            log.error("Error performing LISTALL operation with pattern {}: {}", pattern, e.getMessage());
            throw e;
        }
    }
}
