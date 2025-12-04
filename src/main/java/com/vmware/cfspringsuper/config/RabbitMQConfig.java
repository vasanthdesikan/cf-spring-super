package com.vmware.cfspringsuper.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Configuration for RabbitMQ connection
 * This configuration is only active when RabbitMQ services are available
 */
@Slf4j
@Configuration
@Conditional(RabbitMQAvailableCondition.class)
public class RabbitMQConfig {

    @Autowired
    private Map<String, List<VcapServicesConfig.ServiceCredentials>> serviceCredentials;

    @Bean
    public ConnectionFactory rabbitConnectionFactory() {
        List<VcapServicesConfig.ServiceCredentials> rabbitServices = serviceCredentials.get("rabbitmq");
        
        if (rabbitServices == null || rabbitServices.isEmpty()) {
            log.warn("No RabbitMQ service found in VCAP_SERVICES");
            return null;
        }

        VcapServicesConfig.ServiceCredentials creds = rabbitServices.get(0);
        log.info("Configuring RabbitMQ connection for service: {}", creds.getServiceName());

        CachingConnectionFactory factory = new CachingConnectionFactory();

        if (creds.getUri() != null && !creds.getUri().isEmpty()) {
            try {
                URI uri = new URI(creds.getUri());
                String scheme = uri.getScheme();
                boolean useSsl = "amqps".equals(scheme) || "https".equals(scheme);
                String host = uri.getHost();
                int port = uri.getPort() != -1 ? uri.getPort() : (creds.getPort() != null ? creds.getPort() : 5672);
                String userInfo = uri.getUserInfo();
                
                // If host is null from URI, fall back to individual properties
                if (host == null || host.isEmpty()) {
                    throw new IllegalArgumentException("Host is null or empty in URI");
                }
                
                factory.setHost(host);
                factory.setPort(port);
                
                // Enable SSL if URI scheme is amqps://
                if (useSsl) {
                    try {
                        factory.getRabbitConnectionFactory().useSslProtocol();
                        log.debug("SSL enabled for RabbitMQ connection (amqps://)");
                    } catch (Exception sslEx) {
                        log.warn("Failed to enable SSL for RabbitMQ: {}", sslEx.getMessage());
                    }
                }
                
                if (userInfo != null && userInfo.contains(":")) {
                    // Split only on first colon to handle passwords with colons
                    String[] userPass = userInfo.split(":", 2);
                    factory.setUsername(userPass[0]);
                    factory.setPassword(userPass.length > 1 ? userPass[1] : "");
                } else if (creds.getUsername() != null) {
                    // Fallback to individual properties if userInfo is missing
                    factory.setUsername(creds.getUsername());
                    factory.setPassword(creds.getPassword() != null ? creds.getPassword() : "");
                }
                
                if (creds.getVirtualHost() != null && !creds.getVirtualHost().isEmpty()) {
                    factory.setVirtualHost(creds.getVirtualHost());
                } else if (uri.getPath() != null && !uri.getPath().isEmpty() && uri.getPath().length() > 1) {
                    factory.setVirtualHost(uri.getPath().substring(1));
                } else {
                    factory.setVirtualHost("/");
                }
                
                log.info("RabbitMQ configured from URI - Host: {}, Port: {}, SSL: {}, User: {}, VHost: {}", 
                        host, port, useSsl, factory.getUsername(), factory.getVirtualHost());
            } catch (Exception e) {
                log.error("Error parsing RabbitMQ URI: {}, falling back to individual properties", e.getMessage(), e);
                factory.setHost(creds.getHost());
                factory.setPort(creds.getPort() != null ? creds.getPort() : 5672);
                factory.setUsername(creds.getUsername());
                factory.setPassword(creds.getPassword());
                if (creds.getVirtualHost() != null && !creds.getVirtualHost().isEmpty()) {
                    factory.setVirtualHost(creds.getVirtualHost());
                } else {
                    factory.setVirtualHost("/");
                }
            }
        } else {
            factory.setHost(creds.getHost());
            factory.setPort(creds.getPort() != null ? creds.getPort() : 5672);
            factory.setUsername(creds.getUsername());
            factory.setPassword(creds.getPassword());
            if (creds.getVirtualHost() != null && !creds.getVirtualHost().isEmpty()) {
                factory.setVirtualHost(creds.getVirtualHost());
            } else {
                factory.setVirtualHost("/");
            }
        }

        return factory;
    }

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public RabbitTemplate rabbitTemplate(@Autowired(required = false) @Nullable ConnectionFactory connectionFactory) {
        if (connectionFactory == null) {
            log.warn("ConnectionFactory is null - RabbitTemplate will not be created");
            return null;
        }
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }
}

