package com.vmware.cfspringsuper.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition to check if RabbitMQ service is available in VCAP_SERVICES
 * Only processes RabbitMQConfig when RabbitMQ services are actually present
 */
public class RabbitMQAvailableCondition implements Condition {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // Get VCAP_SERVICES from environment
        String vcapServices = context.getEnvironment().getProperty("VCAP_SERVICES");
        if (vcapServices == null || vcapServices.trim().isEmpty()) {
            return false;
        }
        
        try {
            JsonNode root = objectMapper.readTree(vcapServices);
            // Check all service keys for rabbitmq
            var fields = root.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String serviceKey = entry.getKey().toLowerCase();
                if (serviceKey.contains("rabbit")) {
                    return true; // Found RabbitMQ service
                }
                
                // Check service entries
                JsonNode services = entry.getValue();
                if (services.isArray()) {
                    for (JsonNode service : services) {
                        // Check label
                        if (service.has("label")) {
                            String label = service.get("label").asText().toLowerCase();
                            if (label.contains("rabbit")) {
                                return true; // Found RabbitMQ service
                            }
                        }
                        // Check name
                        if (service.has("name")) {
                            String name = service.get("name").asText().toLowerCase();
                            if (name.contains("rabbit")) {
                                return true; // Found RabbitMQ service
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If we can't parse VCAP_SERVICES, assume RabbitMQ is not available
            return false;
        }
        
        return false; // No RabbitMQ service found
    }
}

