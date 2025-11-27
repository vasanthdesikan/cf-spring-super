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
 * Creates beans only when services are available
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
            log.debug("No MySQL service found in VCAP_SERVICES - MySQL datasource will not be created");
            return null;
        }

        VcapServicesConfig.ServiceCredentials creds = mysqlServices.get(0);
        if (creds == null || creds.getServiceName() == null) {
            log.warn("MySQL service credentials are invalid - MySQL datasource will not be created");
            return null;
        }
        log.info("Configuring MySQL datasource for service: {}", creds.getServiceName());

        // Prefer jdbcUrl if available, otherwise build from components
        String jdbcUrl = creds.getJdbcUrl();
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            jdbcUrl = buildJdbcUrl("mysql", creds);
        }

        return DataSourceBuilder.create()
                .url(jdbcUrl)
                .username(creds.getUsername())
                .password(creds.getPassword())
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .build();
    }

    @Bean(name = "postgresDataSource")
    public DataSource postgresDataSource() {
        List<VcapServicesConfig.ServiceCredentials> postgresServices = serviceCredentials.get("postgresql");
        
        if (postgresServices == null || postgresServices.isEmpty()) {
            log.debug("No PostgreSQL service found in VCAP_SERVICES - PostgreSQL datasource will not be created");
            return null;
        }

        VcapServicesConfig.ServiceCredentials creds = postgresServices.get(0);
        if (creds == null || creds.getServiceName() == null) {
            log.warn("PostgreSQL service credentials are invalid - PostgreSQL datasource will not be created");
            return null;
        }
        log.info("Configuring PostgreSQL datasource for service: {}", creds.getServiceName());

        // Prefer jdbcUrl if available, otherwise build from components
        String jdbcUrl = creds.getJdbcUrl();
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            jdbcUrl = buildJdbcUrl("postgresql", creds);
        }

        return DataSourceBuilder.create()
                .url(jdbcUrl)
                .username(creds.getUsername())
                .password(creds.getPassword())
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    private String buildJdbcUrl(String dbType, VcapServicesConfig.ServiceCredentials creds) {
        // Try URI first (but convert to JDBC format if needed)
        if (creds.getUri() != null && !creds.getUri().isEmpty()) {
            String uri = creds.getUri();
            // Convert mysql:// or postgresql:// URI to jdbc: format
            if (uri.startsWith("mysql://")) {
                return "jdbc:" + uri;
            } else if (uri.startsWith("postgresql://")) {
                return "jdbc:" + uri;
            }
            return uri;
        }

        String host = creds.getHost();
        if (host == null || host.isEmpty()) {
            log.error("Cannot build JDBC URL: host is missing");
            return null;
        }

        Integer port = creds.getPort() != null ? creds.getPort() : (dbType.equals("mysql") ? 3306 : 5432);
        String database = creds.getDatabase() != null ? creds.getDatabase() : "";

        if (dbType.equals("mysql")) {
            return String.format("jdbc:mysql://%s:%d/%s?useSSL=true&requireSSL=false", host, port, database);
        } else {
            return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
        }
    }
}

