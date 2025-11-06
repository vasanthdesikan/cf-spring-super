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
        
        // Resolve hostname to IP if needed
        String host = creds.getHost();
        if (host != null && host.contains("<unresolved>")) {
            log.error("Redis hostname contains <unresolved>: {}", host);
            return null;
        }
        config.setHostName(host);
        
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
        
        // Configure DNS resolution timeout
        factory.setValidateConnection(true);
        factory.setShutdownTimeout(java.time.Duration.ofSeconds(2));
        
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
}

