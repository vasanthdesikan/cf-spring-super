package com.vmware.cfspringsuper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    RedisAutoConfiguration.class,
    RabbitAutoConfiguration.class
}, excludeName = {
    "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
    "org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration",
    "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
    "org.springframework.boot.actuate.autoconfigure.jdbc.DataSourceHealthContributorAutoConfiguration"
})
public class CfSpringSuperApplication {

    public static void main(String[] args) {
        SpringApplication.run(CfSpringSuperApplication.class, args);
    }
}

