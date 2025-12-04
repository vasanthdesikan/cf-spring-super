package com.vmware.cfspringsuper.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

/**
 * Configuration class to parse VCAP_SERVICES environment variable
 * Supports both standard Tanzu-provisioned services and User Provided Services (CUPS)
 */
@Slf4j
@Configuration
public class VcapServicesConfig {

    @Value("${VCAP_SERVICES:}")
    private String vcapServicesJson;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public Map<String, List<ServiceCredentials>> serviceCredentials() {
        Map<String, List<ServiceCredentials>> credentialsMap = new HashMap<>();
        
        if (vcapServicesJson == null || vcapServicesJson.trim().isEmpty()) {
            log.warn("VCAP_SERVICES is not set. Running in non-Cloud Foundry environment.");
            return credentialsMap;
        }

        try {
            JsonNode root = objectMapper.readTree(vcapServicesJson);
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String serviceKey = entry.getKey(); // e.g., "user-provided", "mysql", "postgresql", etc.
                JsonNode services = entry.getValue();

                if (services.isArray()) {
                    for (JsonNode service : services) {
                        ServiceCredentials creds = parseServiceCredentials(service);
                        // Mark if this is a user-provided service
                        creds.setUserProvided("user-provided".equals(serviceKey));
                        
                        // Determine service type based on name, label, or service key
                        String serviceType = determineServiceType(service, serviceKey, creds);
                        List<ServiceCredentials> credsList = credentialsMap.computeIfAbsent(serviceType, k -> new ArrayList<>());
                        credsList.add(creds);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing VCAP_SERVICES: {}", e.getMessage(), e);
        }

        return credentialsMap;
    }

    private String determineServiceType(JsonNode service, String serviceKey, ServiceCredentials creds) {
        // Check service name first - use word boundaries to avoid false positives
        if (service.has("name")) {
            String name = service.get("name").asText().toLowerCase();
            // Use more precise matching - exact match, starts with, ends with, or contains as whole word
            if (name.equals("mysql") || name.startsWith("mysql-") || name.endsWith("-mysql") || 
                name.matches(".*[^a-z]mysql[^a-z].*") || name.matches(".*[^a-z]mysql$") || name.matches("^mysql[^a-z].*")) {
                return "mysql";
            }
            if (name.equals("postgresql") || name.equals("postgres") ||
                name.startsWith("postgresql-") || name.startsWith("postgres-") ||
                name.endsWith("-postgresql") || name.endsWith("-postgres") ||
                name.matches(".*[^a-z]postgres[^a-z].*") || name.matches(".*[^a-z]postgresql[^a-z].*")) {
                return "postgresql";
            }
            if (name.equals("valkey") || name.equals("redis") ||
                name.startsWith("valkey-") || name.startsWith("redis-") ||
                name.endsWith("-valkey") || name.endsWith("-redis") ||
                name.matches(".*[^a-z]redis[^a-z].*") || name.matches(".*[^a-z]valkey[^a-z].*")) {
                return "valkey";
            }
            if (name.equals("rabbitmq") || name.equals("rabbit") ||
                name.startsWith("rabbitmq-") || name.startsWith("rabbit-") ||
                name.endsWith("-rabbitmq") || name.endsWith("-rabbit") ||
                name.matches(".*[^a-z]rabbit[^a-z].*") || name.matches(".*[^a-z]rabbitmq[^a-z].*")) {
                return "rabbitmq";
            }
        }
        
        // Check label - exact match or starts with
        if (service.has("label")) {
            String label = service.get("label").asText().toLowerCase();
            if (label.equals("mysql") || label.startsWith("mysql")) return "mysql";
            if (label.equals("postgresql") || label.equals("postgres") || 
                label.startsWith("postgresql") || label.startsWith("postgres")) return "postgresql";
            if (label.equals("valkey") || label.equals("redis") ||
                label.startsWith("valkey") || label.startsWith("redis")) return "valkey";
            if (label.equals("rabbitmq") || label.equals("rabbit") ||
                label.startsWith("rabbitmq") || label.startsWith("rabbit")) return "rabbitmq";
        }
        
        // Check service key (for standard services) - exact match or starts with
        String keyLower = serviceKey.toLowerCase();
        if (keyLower.equals("mysql") || keyLower.startsWith("mysql")) return "mysql";
        if (keyLower.equals("postgresql") || keyLower.equals("postgres") ||
            keyLower.startsWith("postgresql") || keyLower.startsWith("postgres")) return "postgresql";
        if (keyLower.equals("valkey") || keyLower.equals("redis") ||
            keyLower.startsWith("valkey") || keyLower.startsWith("redis")) return "valkey";
        if (keyLower.equals("rabbitmq") || keyLower.equals("rabbit") ||
            keyLower.startsWith("rabbitmq") || keyLower.startsWith("rabbit")) return "rabbitmq";
        
        // For user-provided services, check credentials structure - be more strict
        if ("user-provided".equals(serviceKey)) {
            // Check if it has jdbcUrl (database) - must contain jdbc: prefix
            if (creds.getJdbcUrl() != null) {
                String jdbcUrl = creds.getJdbcUrl().toLowerCase();
                if (jdbcUrl.contains("jdbc:mysql://") || jdbcUrl.contains("mysql://")) return "mysql";
                if (jdbcUrl.contains("jdbc:postgresql://") || jdbcUrl.contains("postgresql://")) return "postgresql";
            }
            // Only categorize as redis/valkey if service name explicitly indicates it
            if (creds.getHost() != null && creds.getPort() != null && creds.getDatabase() == null) {
                String serviceName = creds.getServiceName();
                if (serviceName != null) {
                    String nameLower = serviceName.toLowerCase();
                    if (nameLower.contains("redis") || nameLower.contains("valkey")) {
                        return "valkey";
                    }
                }
            }
        }
        
        // Don't auto-categorize - return original key to avoid false positives
        return serviceKey;
    }

    private ServiceCredentials parseServiceCredentials(JsonNode service) {
        ServiceCredentials creds = new ServiceCredentials();
        
        // Extract service metadata
        if (service.has("name")) {
            creds.setServiceName(service.get("name").asText());
        }
        if (service.has("label")) {
            creds.setLabel(service.get("label").asText());
        }
        
        // Handle credentials - can be nested (credentials.credentials) for user-provided services
        JsonNode credentials = null;
        if (service.has("credentials")) {
            JsonNode credsNode = service.get("credentials");
            // Check if credentials are nested (user-provided services)
            if (credsNode.has("credentials")) {
                credentials = credsNode.get("credentials");
            } else {
                credentials = credsNode;
            }
        }
        
        if (credentials != null) {
            // Extract host (try multiple field names)
            String host = extractString(credentials, "host", "hostname");
            if (host == null && credentials.has("hosts") && credentials.get("hosts").isArray()) {
                // PostgreSQL uses hosts array - take first element
                JsonNode hostsArray = credentials.get("hosts");
                if (hostsArray.size() > 0) {
                    host = hostsArray.get(0).asText();
                }
            }
            // Clean hostname immediately to remove <unresolved> markers
            if (host != null) {
                // Remove everything after / (including <unresolved>)
                if (host.contains("/")) {
                    host = host.substring(0, host.indexOf("/"));
                }
                // Remove <unresolved> marker if still present
                host = host.replace("<unresolved>", "").trim();
                if (host.isEmpty()) {
                    host = null;
                }
            }
            creds.setHost(host);
            
            // Extract port
            Integer port = extractInt(credentials, "port");
            // For RabbitMQ user-provided services, check protocols.amqp+ssl.port
            if (port == null && credentials.has("protocols")) {
                JsonNode protocols = credentials.get("protocols");
                if (protocols.has("amqp+ssl")) {
                    JsonNode amqpSsl = protocols.get("amqp+ssl");
                    if (amqpSsl.has("port")) {
                        port = amqpSsl.get("port").asInt();
                    }
                } else if (protocols.has("amqp")) {
                    JsonNode amqp = protocols.get("amqp");
                    if (amqp.has("port")) {
                        port = amqp.get("port").asInt();
                    }
                }
            }
            creds.setPort(port);
            
            // Extract database name (try multiple field names)
            creds.setDatabase(extractString(credentials, "database", "db", "name"));
            
            // Extract username (try multiple field names)
            creds.setUsername(extractString(credentials, "username", "user"));
            
            // Extract password
            creds.setPassword(extractString(credentials, "password"));
            
            // Extract JDBC URL (preferred for databases)
            creds.setJdbcUrl(extractString(credentials, "jdbcUrl", "jdbc_url"));
            
            // Extract URI
            creds.setUri(extractString(credentials, "uri"));
            
            // For RabbitMQ
            creds.setVirtualHost(extractString(credentials, "vhost", "virtual_host"));
            creds.setManagementUri(extractString(credentials, "management_uri"));
            
            // For Redis/Valkey - check TLS
            if (credentials.has("tls")) {
                creds.setTlsEnabled(credentials.get("tls").asBoolean(false));
            } else if (credentials.has("tls_port")) {
                // If tls_port exists, TLS is available
                creds.setTlsEnabled(true);
                creds.setTlsPort(extractInt(credentials, "tls_port"));
            }
            
            // For Redis/Valkey - service gateway support
            if (credentials.has("service_gateway_enabled")) {
                creds.setServiceGatewayEnabled(credentials.get("service_gateway_enabled").asBoolean(false));
            }
            if (credentials.has("service_gateway_access_port")) {
                creds.setServiceGatewayAccessPort(extractInt(credentials, "service_gateway_access_port"));
            }
        }
        
        return creds;
    }

    private String extractString(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asText();
            }
        }
        return null;
    }

    private Integer extractInt(JsonNode node, String key) {
        if (node.has(key) && !node.get(key).isNull()) {
            return node.get(key).asInt();
        }
        return null;
    }

    public static class ServiceCredentials {
        private String serviceName;
        private String label;
        private String host;
        private Integer port;
        private String database;
        private String username;
        private String password;
        private String uri;
        private String jdbcUrl;
        private String virtualHost;
        private String managementUri;
        private Boolean tlsEnabled = false;
        private Integer tlsPort;
        private Boolean serviceGatewayEnabled = false;
        private Integer serviceGatewayAccessPort;
        private Boolean userProvided = false;

        // Getters and Setters
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }

        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }

        public String getJdbcUrl() { return jdbcUrl; }
        public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

        public String getVirtualHost() { return virtualHost; }
        public void setVirtualHost(String virtualHost) { this.virtualHost = virtualHost; }

        public String getManagementUri() { return managementUri; }
        public void setManagementUri(String managementUri) { this.managementUri = managementUri; }

        public Boolean getTlsEnabled() { return tlsEnabled; }
        public void setTlsEnabled(Boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }

        public Integer getTlsPort() { return tlsPort; }
        public void setTlsPort(Integer tlsPort) { this.tlsPort = tlsPort; }

        public Boolean getServiceGatewayEnabled() { return serviceGatewayEnabled; }
        public void setServiceGatewayEnabled(Boolean serviceGatewayEnabled) { this.serviceGatewayEnabled = serviceGatewayEnabled; }

        public Integer getServiceGatewayAccessPort() { return serviceGatewayAccessPort; }
        public void setServiceGatewayAccessPort(Integer serviceGatewayAccessPort) { this.serviceGatewayAccessPort = serviceGatewayAccessPort; }

        public Boolean getUserProvided() { return userProvided; }
        public void setUserProvided(Boolean userProvided) { this.userProvided = userProvided; }
    }
}

