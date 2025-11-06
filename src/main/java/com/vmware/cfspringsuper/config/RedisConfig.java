package com.vmware.cfspringsuper.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

/**
 * Configuration for Redis/Valkey connection
 */
@Slf4j
@Configuration
public class RedisConfig {

    @Autowired
    private Map<String, List<VcapServicesConfig.ServiceCredentials>> serviceCredentials;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // Try to find Redis or Valkey service
        // Check for valkey first, then redis
        List<VcapServicesConfig.ServiceCredentials> redisServices = serviceCredentials.get("valkey");
        if (redisServices == null || redisServices.isEmpty()) {
            redisServices = serviceCredentials.get("redis");
        }
        
        if (redisServices == null || redisServices.isEmpty()) {
            log.warn("No Redis/Valkey service found in VCAP_SERVICES");
            return null;
        }

        VcapServicesConfig.ServiceCredentials creds = redisServices.get(0);
        log.info("Configuring Redis/Valkey connection for service: {}", creds.getServiceName());

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        
        // Extract and resolve hostname
        String host = creds.getHost();
        if (host == null || host.isEmpty()) {
            log.error("Redis host is null or empty");
            return null;
        }
        
        log.info("Original Redis host from VCAP_SERVICES: {}", host);
        
        // Clean up hostname - remove <unresolved> marker and everything after /
        // Handle format: hostname/<unresolved>:port or hostname/<unresolved>
        String cleanHost = host;
        if (cleanHost.contains("/")) {
            cleanHost = cleanHost.substring(0, cleanHost.indexOf("/"));
            log.info("Extracted hostname (removed part after /): {}", cleanHost);
        }
        if (cleanHost.contains("<unresolved>")) {
            cleanHost = cleanHost.replace("<unresolved>", "").trim();
            log.info("Removed <unresolved> marker from hostname: {}", cleanHost);
        }
        // Remove any trailing whitespace or special characters
        cleanHost = cleanHost.trim();
        
        if (cleanHost.isEmpty()) {
            log.error("Hostname is empty after cleaning: {}", host);
            return null;
        }
        
        log.info("Cleaned Redis hostname: {}", cleanHost);
        
        // Resolve hostname to IP address
        String resolvedHost = resolveHostname(cleanHost);
        if (resolvedHost == null) {
            log.warn("Failed to resolve Redis hostname to IP: {}. Will use hostname directly - DNS resolution may work at connection time.", cleanHost);
            // Use the cleaned hostname - Lettuce may be able to resolve it at connection time
            resolvedHost = cleanHost;
        } else {
            log.info("Successfully resolved Redis hostname {} to IP: {}", cleanHost, resolvedHost);
        }
        
        config.setHostName(resolvedHost);
        
        // For user-provided services: use service_gateway_access_port if available, otherwise use port
        // For standard services: use TLS port if enabled, otherwise use regular port
        int port = 6379;
        if (creds.getUserProvided() != null && creds.getUserProvided()) {
            // User-provided service: prefer service_gateway_access_port
            if (creds.getServiceGatewayAccessPort() != null) {
                port = creds.getServiceGatewayAccessPort();
                log.info("Using service gateway access port (user-provided): {}", port);
            } else if (creds.getPort() != null) {
                port = creds.getPort();
                log.info("Using port (user-provided): {}", port);
            }
        } else {
            // Standard service: prefer TLS port if enabled, otherwise regular port
            if (creds.getTlsEnabled() != null && creds.getTlsEnabled() && creds.getTlsPort() != null) {
                port = creds.getTlsPort();
                log.info("Using TLS port: {}", port);
            } else if (creds.getPort() != null) {
                port = creds.getPort();
                log.info("Using regular port: {}", port);
            }
        }
        config.setPort(port);
        
        if (creds.getPassword() != null && !creds.getPassword().isEmpty()) {
            config.setPassword(creds.getPassword());
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        
        // Configure connection settings
        factory.setValidateConnection(true);
        
        // Note: TLS configuration would need additional setup for Lettuce
        // For now, we'll use the TLS port but full TLS setup may require additional configuration
        
        return factory;
    }

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisTemplate<String, Object> redisTemplate(@Autowired(required = false) @Nullable RedisConnectionFactory connectionFactory) {
        if (connectionFactory == null) {
            log.warn("RedisConnectionFactory is null - RedisTemplate will not be created");
            return null;
        }
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }
    
    /**
     * Resolve hostname to IP address
     * @param hostname The hostname to resolve
     * @return The IP address as a string, or null if resolution fails
     */
    private String resolveHostname(String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            return null;
        }
        
        // Check if it's already an IP address
        if (isIpAddress(hostname)) {
            return hostname;
        }
        
        try {
            InetAddress address = InetAddress.getByName(hostname);
            String ip = address.getHostAddress();
            log.debug("Resolved hostname {} to IP {}", hostname, ip);
            return ip;
        } catch (UnknownHostException e) {
            log.warn("Failed to resolve hostname {}: {}", hostname, e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if a string is an IP address
     * @param str The string to check
     * @return true if the string appears to be an IP address
     */
    private boolean isIpAddress(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // Simple check: contains dots and is numeric segments
        return str.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
    }
}

