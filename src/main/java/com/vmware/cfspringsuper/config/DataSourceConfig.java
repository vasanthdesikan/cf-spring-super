package com.vmware.cfspringsuper.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * Configuration for data sources (MySQL and PostgreSQL)
 * Handles multiple service instances and selects the first available
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    @Autowired
    private Map<String, List<VcapServicesConfig.ServiceCredentials>> serviceCredentials;

    @Bean(name = "mysqlDataSource")
    @Primary
    public DataSource mysqlDataSource() {
        List<VcapServicesConfig.ServiceCredentials> mysqlServices = serviceCredentials.get("mysql");
        
        if (mysqlServices == null || mysqlServices.isEmpty()) {
            log.warn("No MySQL service found in VCAP_SERVICES");
            return null;
        }

        VcapServicesConfig.ServiceCredentials creds = mysqlServices.get(0);
        log.info("Configuring MySQL datasource for service: {}", creds.getServiceName());

        return DataSourceBuilder.create()
                .url(buildJdbcUrl("mysql", creds))
                .username(creds.getUsername())
                .password(creds.getPassword())
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .build();
    }

    @Bean(name = "postgresDataSource")
    public DataSource postgresDataSource() {
        List<VcapServicesConfig.ServiceCredentials> postgresServices = serviceCredentials.get("postgresql");
        
        if (postgresServices == null || postgresServices.isEmpty()) {
            log.warn("No PostgreSQL service found in VCAP_SERVICES");
            return null;
        }

        VcapServicesConfig.ServiceCredentials creds = postgresServices.get(0);
        log.info("Configuring PostgreSQL datasource for service: {}", creds.getServiceName());

        return DataSourceBuilder.create()
                .url(buildJdbcUrl("postgresql", creds))
                .username(creds.getUsername())
                .password(creds.getPassword())
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    private String buildJdbcUrl(String dbType, VcapServicesConfig.ServiceCredentials creds) {
        if (creds.getUri() != null && !creds.getUri().isEmpty()) {
            return creds.getUri();
        }

        String host = creds.getHost();
        Integer port = creds.getPort() != null ? creds.getPort() : (dbType.equals("mysql") ? 3306 : 5432);
        String database = creds.getDatabase();

        if (dbType.equals("mysql")) {
            return String.format("jdbc:mysql://%s:%d/%s?useSSL=true&requireSSL=false", host, port, database);
        } else {
            return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
        }
    }
}

