package com.example.stockmarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LogEntry(
        @JsonProperty("type") String type,
        @JsonProperty("wallet_id") String walletId,
        @JsonProperty("stock_name") String stockName
) {}