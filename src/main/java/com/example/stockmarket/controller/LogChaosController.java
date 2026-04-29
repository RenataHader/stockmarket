package com.example.stockmarket.controller;

import com.example.stockmarket.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class LogChaosController {

    private final StockService stockService;

    public LogChaosController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/log")
    public ResponseEntity<?> getLog() {
        return ResponseEntity.ok(Map.of("log", stockService.getLog()));
    }

    @PostMapping("/chaos")
    public void chaos() {
        System.exit(1);
    }
}
