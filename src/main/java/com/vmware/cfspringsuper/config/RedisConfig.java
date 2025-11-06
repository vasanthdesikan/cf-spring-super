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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration for Redis/Valkey connection
 * Implements best practices from Lettuce Redis Java client documentation
 * Handles both standard services and user-provided services (CUPS)
 */
@Slf4j
@Configuration
public class RedisConfig {

    @Autowired
    private Map<String, List<VcapServicesConfig.ServiceCredentials>> serviceCredentials;

    static {
        // Disable DNS cache for dynamic Redis endpoints (e.g., Redis Enterprise Active-Active)
        // This is critical when server nodes or proxies fail and IP addresses change
        java.security.Security.setProperty("networkaddress.cache.ttl", "0");
        java.security.Security.setProperty("networkaddress.cache.negative.ttl", "0");
        log.info("DNS cache disabled for Redis connections");
    }

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
        boolean isUserProvided = creds.getUserProvided() != null && creds.getUserProvided();
        
        log.info("Configuring Redis/Valkey connection for service: {} (User-provided: {})", 
                creds.getServiceName(), isUserProvided);

        try {
            // Extract and clean hostname
            String host = extractAndCleanHostname(creds.getHost());
            if (host == null || host.isEmpty()) {
                log.error("Redis hostname is null or empty after extraction");
                return null;
            }

            // Resolve hostname to IP if possible (helps with connection issues)
            String connectionHost = resolveHostname(host);
            log.info("Using host for connection: {} (original: {})", connectionHost, host);

            // Configure Redis connection
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName(connectionHost);
            
            // Determine port based on service type
            int port = determinePort(creds, isUserProvided);
            config.setPort(port);
            
            // Set password if available
            if (creds.getPassword() != null && !creds.getPassword().isEmpty()) {
                config.setPassword(creds.getPassword());
                log.debug("Password configured for Redis connection");
            }

            // Build Lettuce client configuration with production-ready settings
            LettuceClientConfiguration clientConfig = buildLettuceClientConfiguration(creds, isUserProvided);

            // Create connection factory
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
            
            // Enable connection validation
            factory.setValidateConnection(true);
            
            // Initialize connection to verify it works
            factory.afterPropertiesSet();
            
            log.info("Redis/Valkey connection factory configured successfully for {}:{}", connectionHost, port);
            return factory;
            
        } catch (Exception e) {
            log.error("Failed to create Redis connection factory: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extract and clean hostname from VCAP_SERVICES
     * Handles formats like: hostname/<unresolved>:port or hostname/<unresolved>
     */
    private String extractAndCleanHostname(String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        
        log.debug("Original Redis host from VCAP_SERVICES: {}", host);
        
        // Remove everything after / (handles <unresolved> markers)
        String cleanHost = host;
        if (cleanHost.contains("/")) {
            cleanHost = cleanHost.substring(0, cleanHost.indexOf("/"));
            log.debug("Extracted hostname (removed part after /): {}", cleanHost);
        }
        
        // Remove <unresolved> marker if present
        if (cleanHost.contains("<unresolved>")) {
            cleanHost = cleanHost.replace("<unresolved>", "").trim();
            log.debug("Removed <unresolved> marker: {}", cleanHost);
        }
        
        // Clean up any trailing whitespace
        cleanHost = cleanHost.trim();
        
        if (cleanHost.isEmpty()) {
            log.error("Hostname is empty after cleaning: {}", host);
            return null;
        }
        
        log.info("Cleaned Redis hostname: {}", cleanHost);
        return cleanHost;
    }

    /**
     * Resolve hostname to IP address
     * Falls back to hostname if resolution fails (DNS may work at connection time)
     */
    private String resolveHostname(String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            return null;
        }
        
        // If already an IP address, return as-is
        if (isIpAddress(hostname)) {
            log.debug("Hostname is already an IP address: {}", hostname);
            return hostname;
        }
        
        try {
            InetAddress address = InetAddress.getByName(hostname);
            String ip = address.getHostAddress();
            log.info("Resolved hostname {} to IP: {}", hostname, ip);
            return ip;
        } catch (UnknownHostException e) {
            log.warn("Failed to resolve hostname {} to IP: {}. Will use hostname directly - DNS resolution may work at connection time.", 
                    hostname, e.getMessage());
            // Return hostname - Lettuce may be able to resolve it at connection time
            return hostname;
        }
    }

    /**
     * Check if a string is an IP address
     */
    private boolean isIpAddress(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return str.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
    }

    /**
     * Determine the port to use based on service type and configuration
     */
    private int determinePort(VcapServicesConfig.ServiceCredentials creds, boolean isUserProvided) {
        int port = 6379; // Default Redis port
        
        if (isUserProvided) {
            // User-provided service: prefer service_gateway_access_port
            if (creds.getServiceGatewayAccessPort() != null) {
                port = creds.getServiceGatewayAccessPort();
                log.info("Using service gateway access port (user-provided): {}", port);
            } else if (creds.getPort() != null) {
                port = creds.getPort();
                log.info("Using port from credentials (user-provided): {}", port);
            }
        } else {
            // Standard service: prefer TLS port if enabled, otherwise regular port
            if (creds.getTlsEnabled() != null && creds.getTlsEnabled() && creds.getTlsPort() != null) {
                port = creds.getTlsPort();
                log.info("Using TLS port (standard service): {}", port);
            } else if (creds.getPort() != null) {
                port = creds.getPort();
                log.info("Using regular port (standard service): {}", port);
            }
        }
        
        return port;
    }

    /**
     * Build Lettuce client configuration with production-ready settings
     * Based on best practices from Lettuce documentation
     */
    private LettuceClientConfiguration buildLettuceClientConfiguration(
            VcapServicesConfig.ServiceCredentials creds, boolean isUserProvided) {
        
        // Configure TCP User Timeout
        // Useful for scenarios where the server stops responding without acknowledging the last request
        // Rule of thumb: TCP_USER_TIMEOUT = TCP_KEEP_IDLE + TCP_KEEPINTVL * TCP_KEEPCNT
        // In this case: 20 = 5 + 5 * 3
        SocketOptions.TcpUserTimeoutOptions tcpUserTimeout = SocketOptions.TcpUserTimeoutOptions.builder()
                .tcpUserTimeout(Duration.ofSeconds(20))
                .enable()
                .build();

        // Configure TCP Keep-Alive
        // Good for detecting dead connections where there is no traffic between client and server
        SocketOptions.KeepAliveOptions keepAliveOptions = SocketOptions.KeepAliveOptions.builder()
                .interval(Duration.ofSeconds(5))   // TCP_KEEPINTVL: interval between probes
                .idle(Duration.ofSeconds(5))       // TCP_KEEPIDLE: time before first probe
                .count(3)                         // TCP_KEEPCNT: number of probes
                .enable()
                .build();

        // Build SocketOptions
        SocketOptions socketOptions = SocketOptions.builder()
                .tcpUserTimeout(tcpUserTimeout)
                .keepAlive(keepAliveOptions)
                .build();

        // Configure timeout options
        // Global command timeout - 30 seconds (adjust based on your needs)
        TimeoutOptions timeoutOptions = TimeoutOptions.builder()
                .fixedTimeout(Duration.ofSeconds(30))
                .build();

        // Build ClientOptions with production settings
        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .timeoutOptions(timeoutOptions)
                .autoReconnect(true)  // Enable auto-reconnect for resilience
                .build();

        // Build Lettuce client configuration
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = 
                LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofSeconds(30));  // Command execution timeout

        // Note: TLS configuration would be added here if needed
        // For now, we use the TLS port but full TLS setup requires additional configuration
        // See: https://redis.io/docs/latest/develop/clients/lettuce/advanced-usage/#ssl-connections
        
        return builder.build();
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
        
        // Enable transaction support
        template.setEnableTransactionSupport(false);
        
        // Initialize the template
        template.afterPropertiesSet();
        
        log.info("RedisTemplate configured successfully");
        return template;
    }
}
