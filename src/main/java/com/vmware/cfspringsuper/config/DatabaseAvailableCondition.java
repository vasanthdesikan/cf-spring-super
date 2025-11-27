package com.vmware.cfspringsuper.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition to check if MySQL or PostgreSQL service is available in VCAP_SERVICES
 * Only processes DataSourceConfig when database services are actually present
 */
public class DatabaseAvailableCondition implements Condition {
    
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
            // Check all service keys for mysql or postgresql
            var fields = root.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String serviceKey = entry.getKey().toLowerCase();
                if (serviceKey.contains("mysql") || serviceKey.contains("postgres")) {
                    return true; // Found database service
                }
                
                // Check service entries
                JsonNode services = entry.getValue();
                if (services.isArray()) {
                    for (JsonNode service : services) {
                        // Check label
                        if (service.has("label")) {
                            String label = service.get("label").asText().toLowerCase();
                            if (label.contains("mysql") || label.contains("postgres")) {
                                return true; // Found database service
                            }
                        }
                        // Check name
                        if (service.has("name")) {
                            String name = service.get("name").asText().toLowerCase();
                            if (name.contains("mysql") || name.contains("postgres")) {
                                return true; // Found database service
                            }
                        }
                        // Check credentials for JDBC URL
                        if (service.has("credentials")) {
                            JsonNode creds = service.get("credentials");
                            // Handle nested credentials (user-provided services)
                            if (creds.has("credentials")) {
                                creds = creds.get("credentials");
                            }
                            if (creds.has("jdbcUrl") || creds.has("jdbc_url")) {
                                String jdbcUrl = creds.has("jdbcUrl") 
                                    ? creds.get("jdbcUrl").asText().toLowerCase()
                                    : creds.get("jdbc_url").asText().toLowerCase();
                                if (jdbcUrl.contains("mysql") || jdbcUrl.contains("postgresql")) {
                                    return true; // Found database service
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If we can't parse VCAP_SERVICES, assume database is not available
            return false;
        }
        
        return false; // No database service found
    }
}

