package com.example.stockmarket.controller;

import com.example.stockmarket.model.StockEntry;
import com.example.stockmarket.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class WalletController {

    private final StockService stockService;

    public WalletController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/wallets/{wallet_id}/stocks/{stock_name}")
    public ResponseEntity<?> trade(
            @PathVariable("wallet_id") String walletId,
            @PathVariable("stock_name") String stockName,
            @RequestBody Map<String, String> body) {

        String type = body.get("type");
        if (type == null || (!type.equals("buy") && !type.equals("sell"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "type must be 'buy' or 'sell'"));
        }

        StockService.TradeResult result = type.equals("buy")
                ? stockService.buy(walletId, stockName)
                : stockService.sell(walletId, stockName);

        return switch (result) {
            case OK                  -> ResponseEntity.ok().build();
            case STOCK_NOT_FOUND     -> ResponseEntity.status(404).body(Map.of("error", "Stock not found"));
            case NO_STOCK_IN_BANK    -> ResponseEntity.badRequest().body(Map.of("error", "No stock available in bank"));
            case NO_STOCK_IN_WALLET  -> ResponseEntity.badRequest().body(Map.of("error", "No stock available in wallet"));
        };
    }

    @GetMapping("/wallets/{wallet_id}")
    public ResponseEntity<?> getWallet(@PathVariable("wallet_id") String walletId) {
        if (!stockService.walletExists(walletId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Wallet not found"));
        }
        List<StockEntry> stocks = stockService.getWalletStocks(walletId);
        return ResponseEntity.ok(Map.of("id", walletId, "stocks", stocks));
    }

    @GetMapping("/wallets/{wallet_id}/stocks/{stock_name}")
    public ResponseEntity<?> getWalletStock(
            @PathVariable("wallet_id") String walletId,
            @PathVariable("stock_name") String stockName) {

        Optional<Long> qty = stockService.getWalletStockQuantity(walletId, stockName);
        if (qty.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Stock not found"));
        }
        return ResponseEntity.ok(qty.get());
    }
}