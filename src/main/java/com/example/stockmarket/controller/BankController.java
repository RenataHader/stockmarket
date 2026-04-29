package com.example.stockmarket.controller;

import com.example.stockmarket.model.StockEntry;
import com.example.stockmarket.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class BankController {

    private final StockService stockService;

    public BankController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/stocks")
    public ResponseEntity<?> getBankStocks() {
        List<StockEntry> stocks = stockService.getBankStocks();
        return ResponseEntity.ok(Map.of("stocks", stocks));
    }

    @PostMapping("/stocks")
    public ResponseEntity<?> setBankStocks(@RequestBody Map<String, List<StockEntry>> body) {
        List<StockEntry> stocks = body.get("stocks");
        if (stocks == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing 'stocks' field"));
        }
        stockService.setBankStocks(stocks);
        return ResponseEntity.ok().build();
    }
}

