package com.vmware.cfspringsuper.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

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
        List<VcapServicesConfig.ServiceCredentials> redisServices = serviceCredentials.get("redis");
        if (redisServices == null || redisServices.isEmpty()) {
            redisServices = serviceCredentials.get("valkey");
        }
        
        if (redisServices == null || redisServices.isEmpty()) {
            log.warn("No Redis/Valkey service found in VCAP_SERVICES");
            return null;
        }

        VcapServicesConfig.ServiceCredentials creds = redisServices.get(0);
        log.info("Configuring Redis/Valkey connection for service: {}", creds.getServiceName());

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(creds.getHost());
        config.setPort(creds.getPort() != null ? creds.getPort() : 6379);
        
        if (creds.getPassword() != null && !creds.getPassword().isEmpty()) {
            config.setPassword(creds.getPassword());
        }

        return new LettuceConnectionFactory(config);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        if (connectionFactory == null) {
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

