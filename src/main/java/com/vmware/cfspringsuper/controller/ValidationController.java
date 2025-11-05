package com.vmware.cfspringsuper.controller;

import com.vmware.cfspringsuper.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Main controller for the validation application
 */
@Controller
@RequiredArgsConstructor
public class ValidationController {

    private final MysqlValidationService mysqlService;
    private final PostgresqlValidationService postgresqlService;
    private final RedisValidationService redisService;
    private final RabbitMQValidationService rabbitMQService;

    @GetMapping("/")
    public String index(Model model) {
        return "index";
    }

    @GetMapping("/api/mysql/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateMysql(
            @RequestParam String operation,
            @RequestParam(required = false) Map<String, Object> data) {
        if (data == null) {
            data = new HashMap<>();
        }
        Map<String, Object> result = mysqlService.validateTransaction(operation, data);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/mysql/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateMysqlPost(
            @RequestParam String operation,
            @RequestBody(required = false) Map<String, Object> data) {
        if (data == null) {
            data = new HashMap<>();
        }
        Map<String, Object> result = mysqlService.validateTransaction(operation, data);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/postgresql/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validatePostgresql(
            @RequestParam String operation,
            @RequestParam(required = false) Map<String, Object> data) {
        if (data == null) {
            data = new HashMap<>();
        }
        Map<String, Object> result = postgresqlService.validateTransaction(operation, data);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/postgresql/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validatePostgresqlPost(
            @RequestParam String operation,
            @RequestBody(required = false) Map<String, Object> data) {
        if (data == null) {
            data = new HashMap<>();
        }
        Map<String, Object> result = postgresqlService.validateTransaction(operation, data);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/redis/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateRedis(
            @RequestParam String operation,
            @RequestParam(required = false) Map<String, Object> data) {
        if (data == null) {
            data = new HashMap<>();
        }
        Map<String, Object> result = redisService.validateTransaction(operation, data);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/redis/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateRedisPost(
            @RequestParam String operation,
            @RequestBody(required = false) Map<String, Object> data) {
        if (data == null) {
            data = new HashMap<>();
        }
        Map<String, Object> result = redisService.validateTransaction(operation, data);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/rabbitmq/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateRabbitMQ(
            @RequestParam String operation,
            @RequestParam(required = false) Map<String, Object> data) {
        if (data == null) {
            data = new HashMap<>();
        }
        Map<String, Object> result = rabbitMQService.validateTransaction(operation, data);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/rabbitmq/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateRabbitMQPost(
            @RequestParam String operation,
            @RequestBody(required = false) Map<String, Object> data) {
        if (data == null) {
            data = new HashMap<>();
        }
        Map<String, Object> result = rabbitMQService.validateTransaction(operation, data);
        return ResponseEntity.ok(result);
    }
}

