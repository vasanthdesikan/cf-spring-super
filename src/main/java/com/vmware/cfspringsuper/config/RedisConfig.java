package com.vmware.cfspringsuper.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration for Redis/Valkey connection using Lettuce
 * Handles both standard and user-provided services
 * Uses TLS with insecure option (trust all certificates)
 */
@Slf4j
@Configuration
public class RedisConfig {

    @Autowired
    private Map<String, List<VcapServicesConfig.ServiceCredentials>> serviceCredentials;

    /**
     * Check if Redis/Valkey service is available
     */
    private boolean isRedisAvailable() {
        List<VcapServicesConfig.ServiceCredentials> redisServices = serviceCredentials.get("valkey");
        if (redisServices == null || redisServices.isEmpty()) {
            redisServices = serviceCredentials.get("redis");
        }
        return redisServices != null && !redisServices.isEmpty();
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // Check if Redis is available first
        if (!isRedisAvailable()) {
            log.warn("No Redis/Valkey service found in VCAP_SERVICES - RedisConnectionFactory will not be created");
            return null;
        }
        
        // Find Redis or Valkey service
        List<VcapServicesConfig.ServiceCredentials> redisServices = serviceCredentials.get("valkey");
        if (redisServices == null || redisServices.isEmpty()) {
            redisServices = serviceCredentials.get("redis");
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

        // Determine port and whether to use TLS
        PortInfo portInfo = determinePort(creds, isUserProvided);
        int port = portInfo.port;
        boolean useTls = portInfo.useTls;
        
        log.info("Using Redis port: {} (TLS: {})", port, useTls);

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

        // Build Lettuce client configuration with TLS (insecure)
        LettuceClientConfiguration clientConfig = buildLettuceClientConfiguration(useTls);

        // Create connection factory
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
        factory.setValidateConnection(true);
        factory.afterPropertiesSet();
        
        log.info("Redis/Valkey connection factory configured for {}:{} (TLS: {})", host, port, useTls);
        return factory;
    }

    /**
     * Build Lettuce client configuration with TLS (insecure - trust all certificates)
     */
    private LettuceClientConfiguration buildLettuceClientConfiguration(boolean useTls) {
        // Configure socket options
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Configure timeout options
        TimeoutOptions timeoutOptions = TimeoutOptions.builder()
                .fixedTimeout(Duration.ofSeconds(30))
                .build();

        // Build client options
        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .timeoutOptions(timeoutOptions)
                .autoReconnect(true)
                .build();

        // Build Lettuce client configuration
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = 
                LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofSeconds(30));

        // Configure TLS with insecure option (trust all certificates)
        if (useTls) {
            // Enable SSL and disable peer verification (insecure - trust all certificates)
            builder.useSsl()
                .disablePeerVerification()
                .and();
            
            log.info("TLS enabled with insecure option (peer verification disabled)");
        }
        
        return builder.build();
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
     * Port information including whether TLS should be used
     */
    private static class PortInfo {
        int port;
        boolean useTls;
        
        PortInfo(int port, boolean useTls) {
            this.port = port;
            this.useTls = useTls;
        }
    }
    
    /**
     * Determine port - prefer non-TLS first, then TLS
     * For user-provided services: try service_gateway_access_port or port (non-TLS), then TLS
     * For standard services: try port (non-TLS), then tls_port
     * Returns port and whether TLS should be enabled
     */
    private PortInfo determinePort(VcapServicesConfig.ServiceCredentials creds, boolean isUserProvided) {
        int defaultPort = 6379;
        boolean defaultUseTls = false;
        
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
                log.debug("User-provided service: falling back to TLS port: {} (TLS required)", creds.getTlsPort());
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
                log.debug("Standard service: falling back to TLS port: {} (TLS required)", creds.getTlsPort());
                return new PortInfo(creds.getTlsPort(), true);
            }
            // Check if TLS is explicitly enabled (even if we have a regular port)
            if (creds.getTlsEnabled() != null && creds.getTlsEnabled() && creds.getPort() != null) {
                log.debug("Standard service: TLS explicitly enabled, using port: {} (TLS required)", creds.getPort());
                return new PortInfo(creds.getPort(), true);
            }
        }
        
        log.warn("No port found in credentials, using default: {} (non-TLS)", defaultPort);
        return new PortInfo(defaultPort, defaultUseTls);
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
