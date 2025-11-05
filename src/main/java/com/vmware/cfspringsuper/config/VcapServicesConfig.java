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
                String serviceName = entry.getKey();
                JsonNode services = entry.getValue();

                if (services.isArray()) {
                    List<ServiceCredentials> credsList = new ArrayList<>();
                    for (JsonNode service : services) {
                        ServiceCredentials creds = parseServiceCredentials(service);
                        credsList.add(creds);
                    }
                    credentialsMap.put(serviceName, credsList);
                }
            }
        } catch (Exception e) {
            log.error("Error parsing VCAP_SERVICES: {}", e.getMessage(), e);
        }

        return credentialsMap;
    }

    private ServiceCredentials parseServiceCredentials(JsonNode service) {
        ServiceCredentials creds = new ServiceCredentials();
        
        // Handle standard service bindings
        if (service.has("credentials")) {
            JsonNode credentials = service.get("credentials");
            creds.setHost(extractString(credentials, "host", "hostname"));
            creds.setPort(extractInt(credentials, "port"));
            creds.setDatabase(extractString(credentials, "database", "name"));
            creds.setUsername(extractString(credentials, "username", "user"));
            creds.setPassword(extractString(credentials, "password"));
            creds.setUri(extractString(credentials, "uri"));
            
            // For RabbitMQ
            creds.setVirtualHost(extractString(credentials, "vhost", "virtual_host"));
            creds.setManagementUri(extractString(credentials, "management_uri"));
            
            // For Redis/Valkey
            if (credentials.has("tls")) {
                creds.setTlsEnabled(credentials.get("tls").asBoolean(false));
            }
        }
        
        // Handle User Provided Services (CUPS) - credentials are in the same structure
        if (service.has("name")) {
            creds.setServiceName(service.get("name").asText());
        }
        if (service.has("label")) {
            creds.setLabel(service.get("label").asText());
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
        private String virtualHost;
        private String managementUri;
        private Boolean tlsEnabled = false;

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

        public String getVirtualHost() { return virtualHost; }
        public void setVirtualHost(String virtualHost) { this.virtualHost = virtualHost; }

        public String getManagementUri() { return managementUri; }
        public void setManagementUri(String managementUri) { this.managementUri = managementUri; }

        public Boolean getTlsEnabled() { return tlsEnabled; }
        public void setTlsEnabled(Boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }
    }
}

