package com.vmware.cfspringsuper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for validating MySQL transactions
 */
@Slf4j
@Service
public class MysqlValidationService {

    @Autowired(required = false)
    @Qualifier("mysqlDataSource")
    @Nullable
    private DataSource mysqlDataSource;

    public Map<String, Object> validateTransaction(String operation, Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("service", "MySQL");
        result.put("operation", operation);

        if (mysqlDataSource == null) {
            result.put("status", "error");
            result.put("message", "MySQL service not configured");
            return result;
        }

        try (Connection conn = mysqlDataSource.getConnection()) {
            // Create test table if it doesn't exist
            createTestTableIfNotExists(conn);

            switch (operation.toLowerCase()) {
                case "read":
                    result.putAll(performRead(conn, data));
                    break;
                case "write":
                    result.putAll(performWrite(conn, data));
                    break;
                case "update":
                    result.putAll(performUpdate(conn, data));
                    break;
                case "delete":
                    result.putAll(performDelete(conn, data));
                    break;
                case "listall":
                    result.putAll(performListAll(conn));
                    break;
                case "listtables":
                    result.putAll(performListTables(conn));
                    break;
                default:
                    result.put("status", "error");
                    result.put("message", "Unsupported operation: " + operation);
            }

            result.put("status", "success");
        } catch (Exception e) {
            log.error("MySQL validation error: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    private void createTestTableIfNotExists(Connection conn) throws Exception {
        String createTable = "CREATE TABLE IF NOT EXISTS validation_test (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "key_name VARCHAR(255) NOT NULL, " +
                "value VARCHAR(255), " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createTable);
        }
    }

    private Map<String, Object> performRead(Connection conn, Map<String, Object> data) throws Exception {
        String key = (String) data.getOrDefault("key", "test");
        String sql = "SELECT * FROM validation_test WHERE key_name = ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            try (ResultSet rs = pstmt.executeQuery()) {
                Map<String, Object> result = new HashMap<>();
                if (rs.next()) {
                    result.put("found", true);
                    result.put("id", rs.getInt("id"));
                    result.put("key", rs.getString("key_name"));
                    result.put("value", rs.getString("value"));
                    result.put("created_at", rs.getTimestamp("created_at").toString());
                } else {
                    result.put("found", false);
                }
                return result;
            }
        }
    }

    private Map<String, Object> performWrite(Connection conn, Map<String, Object> data) throws Exception {
        String key = (String) data.getOrDefault("key", "test_" + System.currentTimeMillis());
        String value = (String) data.getOrDefault("value", "test_value");
        String sql = "INSERT INTO validation_test (key_name, value) VALUES (?, ?)";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            int rowsAffected = pstmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            result.put("rowsAffected", rowsAffected);
            
            if (rowsAffected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        result.put("generatedId", rs.getInt(1));
                    }
                }
            }
            return result;
        }
    }

    private Map<String, Object> performUpdate(Connection conn, Map<String, Object> data) throws Exception {
        String key = (String) data.getOrDefault("key", "test");
        String newValue = (String) data.getOrDefault("value", "updated_value");
        String sql = "UPDATE validation_test SET value = ? WHERE key_name = ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newValue);
            pstmt.setString(2, key);
            int rowsAffected = pstmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            result.put("rowsAffected", rowsAffected);
            return result;
        }
    }

    private Map<String, Object> performDelete(Connection conn, Map<String, Object> data) throws Exception {
        String key = (String) data.getOrDefault("key", "test");
        String sql = "DELETE FROM validation_test WHERE key_name = ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            int rowsAffected = pstmt.executeUpdate();
            
            Map<String, Object> result = new HashMap<>();
            result.put("rowsAffected", rowsAffected);
            return result;
        }
    }

    private Map<String, Object> performListAll(Connection conn) throws Exception {
        String sql = "SELECT id, key_name, value, created_at, updated_at FROM validation_test ORDER BY id";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("key_name", rs.getString("key_name"));
                row.put("value", rs.getString("value"));
                row.put("created_at", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : null);
                row.put("updated_at", rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toString() : null);
                rows.add(row);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("rows", rows);
            result.put("count", rows.size());
            return result;
        }
    }

    private Map<String, Object> performListTables(Connection conn) throws Exception {
        String sql = "SELECT TABLE_NAME, TABLE_TYPE, TABLE_ROWS, DATA_LENGTH " +
                     "FROM INFORMATION_SCHEMA.TABLES " +
                     "WHERE TABLE_SCHEMA = DATABASE() " +
                     "ORDER BY TABLE_NAME";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            List<Map<String, Object>> tables = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> table = new HashMap<>();
                table.put("name", rs.getString("TABLE_NAME"));
                table.put("type", rs.getString("TABLE_TYPE"));
                table.put("rows", rs.getLong("TABLE_ROWS"));
                table.put("data_length", rs.getLong("DATA_LENGTH"));
                tables.add(table);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("tables", tables);
            result.put("count", tables.size());
            return result;
        }
    }
}

