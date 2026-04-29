package com.example.stockmarket.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StockEntry(
        @JsonProperty("name") String name,
        @JsonProperty("quantity") long quantity
) {}

