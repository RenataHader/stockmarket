package com.example.stockmarket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.stockmarket.model.LogEntry;
import com.example.stockmarket.model.StockEntry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StockService {

    private static final String BANK_PREFIX  = "bank:";
    private static final String WALLET_PREFIX = "wallet:";
    private static final String STOCKS_SET_SUFFIX = ":__stocks__";
    private static final String LOG_KEY = "log";
    private static final String BANK_STOCKS_SET = "bank:__stocks__";

    private final RedisTemplate<String, String> redis;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String BUY_SCRIPT = """
            local bankKey   = KEYS[1]
            local walletKey = KEYS[2]
            local bankSet   = KEYS[3]
            local walletSet = KEYS[4]
            local stockName = ARGV[1]
            
            local bankQty = tonumber(redis.call('GET', bankKey) or '0')
            if bankQty == nil or bankQty <= 0 then
              return redis.error_reply('NO_STOCK_IN_BANK')
            end
            redis.call('DECRBY', bankKey, 1)
            redis.call('SADD', bankSet, stockName)
            redis.call('INCR', walletKey)
            redis.call('SADD', walletSet, stockName)
            return 'OK'
            """;

    private static final String SELL_SCRIPT = """
            local walletKey = KEYS[1]
            local bankKey   = KEYS[2]
            local bankSet   = KEYS[3]
            local walletSet = KEYS[4]
            local stockName = ARGV[1]
            
            local walletQty = tonumber(redis.call('GET', walletKey) or '0')
            if walletQty == nil or walletQty <= 0 then
              return redis.error_reply('NO_STOCK_IN_WALLET')
            end
            redis.call('DECRBY', walletKey, 1)
            redis.call('INCR', bankKey)
            redis.call('SADD', bankSet, stockName)
            redis.call('SADD', walletSet, stockName)
            return 'OK'
            """;

    private final DefaultRedisScript<String> buyScript;
    private final DefaultRedisScript<String> sellScript;

    public StockService(@Qualifier("redisTemplate") RedisTemplate<String, String> redis) {
        this.redis = redis;

        buyScript = new DefaultRedisScript<>();
        buyScript.setScriptText(BUY_SCRIPT);
        buyScript.setResultType(String.class);

        sellScript = new DefaultRedisScript<>();
        sellScript.setScriptText(SELL_SCRIPT);
        sellScript.setResultType(String.class);
    }

    public List<StockEntry> getBankStocks() {
        Set<String> names = redis.opsForSet().members(BANK_STOCKS_SET);
        if (names == null || names.isEmpty()) return List.of();
        return names.stream()
                .map(name -> new StockEntry(name, getBankQuantity(name)))
                .sorted(Comparator.comparing(StockEntry::name))
                .collect(Collectors.toList());
    }

    public void setBankStocks(List<StockEntry> stocks) {
        Set<String> oldNames = redis.opsForSet().members(BANK_STOCKS_SET);

        if (oldNames != null) {
            for (String oldName : oldNames) {
                redis.delete(BANK_PREFIX + oldName);
            }
        }

        redis.delete(BANK_STOCKS_SET);

        for (StockEntry entry : stocks) {
            String key = BANK_PREFIX + entry.name();
            redis.opsForValue().set(key, String.valueOf(entry.quantity()));
            redis.opsForSet().add(BANK_STOCKS_SET, entry.name());
        }
    }

    private long getBankQuantity(String stockName) {
        String val = redis.opsForValue().get(BANK_PREFIX + stockName);
        return val == null ? 0L : Long.parseLong(val);
    }

    public boolean stockExists(String stockName) {
        Boolean member = redis.opsForSet().isMember(BANK_STOCKS_SET, stockName);
        return Boolean.TRUE.equals(member);
    }

    public enum TradeResult { OK, STOCK_NOT_FOUND, NO_STOCK_IN_BANK, NO_STOCK_IN_WALLET }

    public TradeResult buy(String walletId, String stockName) {
        if (!stockExists(stockName)) return TradeResult.STOCK_NOT_FOUND;

        String bankKey    = BANK_PREFIX + stockName;
        String walletKey  = WALLET_PREFIX + walletId + ":" + stockName;
        String walletSet  = WALLET_PREFIX + walletId + STOCKS_SET_SUFFIX;

        try {
            redis.execute(buyScript,
                    List.of(bankKey, walletKey, BANK_STOCKS_SET, walletSet),
                    stockName);
        } catch (Exception e) {
            if (containsCauseMessage(e, "NO_STOCK_IN_BANK")) {
                return TradeResult.NO_STOCK_IN_BANK;
            }
            throw e;
        }

        appendLog(new LogEntry("buy", walletId, stockName));
        return TradeResult.OK;
    }

    public TradeResult sell(String walletId, String stockName) {
        if (!stockExists(stockName)) return TradeResult.STOCK_NOT_FOUND;

        String walletKey = WALLET_PREFIX + walletId + ":" + stockName;
        String bankKey   = BANK_PREFIX + stockName;
        String walletSet = WALLET_PREFIX + walletId + STOCKS_SET_SUFFIX;

        try {
            redis.execute(sellScript,
                    List.of(walletKey, bankKey, BANK_STOCKS_SET, walletSet),
                    stockName);
        } catch (Exception e) {
            if (containsCauseMessage(e, "NO_STOCK_IN_WALLET")) {
                return TradeResult.NO_STOCK_IN_WALLET;
            }
            throw e;
        }

        appendLog(new LogEntry("sell", walletId, stockName));
        return TradeResult.OK;
    }

    public List<StockEntry> getWalletStocks(String walletId) {
        String walletSet = WALLET_PREFIX + walletId + STOCKS_SET_SUFFIX;
        Set<String> names = redis.opsForSet().members(walletSet);
        if (names == null || names.isEmpty()) return List.of();
        return names.stream()
                .map(name -> {
                    String key = WALLET_PREFIX + walletId + ":" + name;
                    String val = redis.opsForValue().get(key);
                    long qty = val == null ? 0L : Long.parseLong(val);
                    return new StockEntry(name, qty);
                })
                .filter(e -> e.quantity() > 0)
                .sorted(Comparator.comparing(StockEntry::name))
                .collect(Collectors.toList());
    }

    public Optional<Long> getWalletStockQuantity(String walletId, String stockName) {
        if (!stockExists(stockName)) return Optional.empty();
        String key = WALLET_PREFIX + walletId + ":" + stockName;
        String val = redis.opsForValue().get(key);
        return Optional.of(val == null ? 0L : Long.parseLong(val));
    }

    public List<LogEntry> getLog() {
        List<String> raw = redis.opsForList().range(LOG_KEY, 0, -1);
        if (raw == null) return List.of();
        return raw.stream().map(json -> {
            try {
                return mapper.readValue(json, LogEntry.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    private void appendLog(LogEntry entry) {
        try {
            redis.opsForList().rightPush(LOG_KEY, mapper.writeValueAsString(entry));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean containsCauseMessage(Throwable e, String text) {
        while (e != null) {
            if (e.getMessage() != null && e.getMessage().contains(text)) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }
}