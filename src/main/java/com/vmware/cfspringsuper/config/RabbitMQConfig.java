package com.vmware.cfspringsuper.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import org.springframework.lang.Nullable;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Configuration for RabbitMQ connection
 * Only processes when RabbitMQ services are available
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
            log.debug("No RabbitMQ service found in VCAP_SERVICES - ConnectionFactory will not be created");
            return null;
        }

        VcapServicesConfig.ServiceCredentials creds = rabbitServices.get(0);
        log.info("Configuring RabbitMQ connection for service: {}", creds.getServiceName());

        CachingConnectionFactory factory = new CachingConnectionFactory();

        if (creds.getUri() != null && !creds.getUri().isEmpty()) {
            try {
                URI uri = new URI(creds.getUri());
                factory.setHost(uri.getHost());
                factory.setPort(uri.getPort());
                factory.setUsername(uri.getUserInfo().split(":")[0]);
                factory.setPassword(uri.getUserInfo().split(":")[1]);
                if (creds.getVirtualHost() != null && !creds.getVirtualHost().isEmpty()) {
                    factory.setVirtualHost(creds.getVirtualHost());
                } else if (uri.getPath() != null && !uri.getPath().isEmpty()) {
                    factory.setVirtualHost(uri.getPath().substring(1));
                }
            } catch (Exception e) {
                log.error("Error parsing RabbitMQ URI: {}", e.getMessage(), e);
                factory.setHost(creds.getHost());
                factory.setPort(creds.getPort() != null ? creds.getPort() : 5672);
                factory.setUsername(creds.getUsername());
                factory.setPassword(creds.getPassword());
                if (creds.getVirtualHost() != null) {
                    factory.setVirtualHost(creds.getVirtualHost());
                }
            }
        } else {
            factory.setHost(creds.getHost());
            factory.setPort(creds.getPort() != null ? creds.getPort() : 5672);
            factory.setUsername(creds.getUsername());
            factory.setPassword(creds.getPassword());
            if (creds.getVirtualHost() != null) {
                factory.setVirtualHost(creds.getVirtualHost());
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

