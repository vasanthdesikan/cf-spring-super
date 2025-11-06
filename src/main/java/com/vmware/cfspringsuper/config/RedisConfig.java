package com.vmware.cfspringsuper.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration for Redis/Valkey connection using Jedis
 * Simple and robust implementation that works for both standard and user-provided services
 * Prefers non-TLS connections first, then falls back to TLS if needed
 */
@Slf4j
@Configuration
public class RedisConfig {

    @Autowired
    private Map<String, List<VcapServicesConfig.ServiceCredentials>> serviceCredentials;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // Find Redis or Valkey service
        List<VcapServicesConfig.ServiceCredentials> redisServices = serviceCredentials.get("valkey");
        if (redisServices == null || redisServices.isEmpty()) {
            redisServices = serviceCredentials.get("redis");
        }
        
        if (redisServices == null || redisServices.isEmpty()) {
            log.warn("No Redis/Valkey service found in VCAP_SERVICES");
            return null;
        }

        VcapServicesConfig.ServiceCredentials creds = redisServices.get(0);
        boolean isUserProvided = creds.getUserProvided() != null && creds.getUserProvided();
        
        log.info("Configuring Redis/Valkey connection for service: {} (User-provided: {})", 
                creds.getServiceName(), isUserProvided);

        // Clean hostname - remove <unresolved> markers and everything after /
        String host = cleanHostname(creds.getHost());
        if (host == null || host.isEmpty()) {
            log.error("Redis host is null or empty after cleaning");
            return null;
        }
        log.info("Using Redis host: {}", host);

        // Determine port - prefer non-TLS first
        PortInfo portInfo = determinePort(creds, isUserProvided);
        int port = portInfo.port;
        boolean useSsl = portInfo.useSsl;
        
        log.info("Using Redis port: {} (SSL: {})", port, useSsl);

        // Create Redis configuration
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        
        // Set password if available
        if (creds.getPassword() != null && !creds.getPassword().isEmpty()) {
            config.setPassword(creds.getPassword());
            log.debug("Redis password configured");
        } else {
            log.debug("No Redis password provided - connecting without authentication");
        }

        // Build Jedis client configuration
        JedisClientConfiguration.JedisClientConfigurationBuilder clientConfigBuilder = 
                JedisClientConfiguration.builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(10));
        
        // Enable SSL if needed
        if (useSsl) {
            clientConfigBuilder.useSsl();
            log.info("SSL/TLS enabled for Redis connection");
        }
        
        JedisClientConfiguration clientConfig = clientConfigBuilder.build();
        
        // Create Jedis connection factory with config and client configuration
        JedisConnectionFactory factory = new JedisConnectionFactory(config, clientConfig);
        factory.afterPropertiesSet();
        
        log.info("Redis/Valkey connection factory configured for {}:{} (SSL: {})", host, port, useSsl);
        return factory;
    }

    /**
     * Clean hostname by removing <unresolved> markers and everything after /
     */
    private String cleanHostname(String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        
        // Remove everything after / (including <unresolved>)
        if (host.contains("/")) {
            host = host.substring(0, host.indexOf("/"));
        }
        
        // Remove <unresolved> marker if still present
        host = host.replace("<unresolved>", "").trim();
        
        return host.isEmpty() ? null : host;
    }

    /**
     * Port information including whether SSL should be used
     */
    private static class PortInfo {
        int port;
        boolean useSsl;
        
        PortInfo(int port, boolean useSsl) {
            this.port = port;
            this.useSsl = useSsl;
        }
    }
    
    /**
     * Determine port - prefer non-TLS first, then TLS
     * For user-provided services: try service_gateway_access_port or port (non-TLS), then TLS
     * For standard services: try port (non-TLS), then tls_port
     * Returns port and whether SSL should be enabled
     */
    private PortInfo determinePort(VcapServicesConfig.ServiceCredentials creds, boolean isUserProvided) {
        int defaultPort = 6379;
        boolean defaultUseSsl = false;
        
        if (isUserProvided) {
            // User-provided service: prefer non-TLS ports first
            if (creds.getServiceGatewayAccessPort() != null) {
                log.debug("User-provided service: using service_gateway_access_port: {} (non-TLS)", 
                        creds.getServiceGatewayAccessPort());
                return new PortInfo(creds.getServiceGatewayAccessPort(), false);
            }
            if (creds.getPort() != null) {
                log.debug("User-provided service: using port: {} (non-TLS)", creds.getPort());
                return new PortInfo(creds.getPort(), false);
            }
            // Fallback to TLS port if non-TLS not available
            if (creds.getTlsPort() != null) {
                log.debug("User-provided service: falling back to TLS port: {} (SSL required)", creds.getTlsPort());
                return new PortInfo(creds.getTlsPort(), true);
            }
        } else {
            // Standard service: prefer non-TLS port first
            if (creds.getPort() != null) {
                log.debug("Standard service: using non-TLS port: {}", creds.getPort());
                return new PortInfo(creds.getPort(), false);
            }
            // Fallback to TLS port if non-TLS not available
            if (creds.getTlsPort() != null) {
                log.debug("Standard service: falling back to TLS port: {} (SSL required)", creds.getTlsPort());
                return new PortInfo(creds.getTlsPort(), true);
            }
            // Check if TLS is explicitly enabled (even if we have a regular port)
            if (creds.getTlsEnabled() != null && creds.getTlsEnabled() && creds.getPort() != null) {
                log.debug("Standard service: TLS explicitly enabled, using port: {} (SSL required)", creds.getPort());
                return new PortInfo(creds.getPort(), true);
            }
        }
        
        log.warn("No port found in credentials, using default: {} (non-TLS)", defaultPort);
        return new PortInfo(defaultPort, defaultUseSsl);
    }

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisTemplate<String, Object> redisTemplate(
            @Autowired(required = false) @Nullable RedisConnectionFactory connectionFactory) {
        if (connectionFactory == null) {
            log.warn("RedisConnectionFactory is null - RedisTemplate will not be created");
            return null;
        }
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializers for keys and values
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        
        template.afterPropertiesSet();
        
        log.info("RedisTemplate configured successfully");
        return template;
    }
}
