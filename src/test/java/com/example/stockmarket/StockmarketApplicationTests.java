package com.example.stockmarket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StockmarketApplicationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    @Qualifier("redisTemplate")
    private RedisTemplate<String, String> redis;

    @BeforeEach
    void cleanRedis() {
        Set<String> keys = redis.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void buyAndSellHappyPath() throws Exception {
        mvc.perform(post("/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stocks\":[{\"name\":\"Stock1\",\"quantity\":5}]}"))
                .andExpect(status().isOk());

        mvc.perform(post("/wallets/w1/stocks/Stock1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"buy\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks[0].name").value("Stock1"))
                .andExpect(jsonPath("$.stocks[0].quantity").value(4));

        mvc.perform(get("/wallets/w1/stocks/Stock1"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        mvc.perform(post("/wallets/w1/stocks/Stock1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"sell\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks[0].quantity").value(5));
    }

    @Test
    void buyNonExistentStockReturns404() throws Exception {
        mvc.perform(post("/wallets/w1/stocks/UNKNOWN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"buy\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void buyWhenBankEmptyReturns400() throws Exception {
        mvc.perform(post("/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stocks\":[{\"name\":\"Stock2\",\"quantity\":0}]}"))
                .andExpect(status().isOk());

        mvc.perform(post("/wallets/w1/stocks/Stock2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"buy\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sellWhenWalletEmptyReturns400() throws Exception {
        mvc.perform(post("/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stocks\":[{\"name\":\"Stock3\",\"quantity\":10}]}"))
                .andExpect(status().isOk());

        mvc.perform(post("/wallets/w1/stocks/Stock3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"sell\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logOnlyContainsSuccessfulOperations() throws Exception {
        mvc.perform(post("/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stocks\":[{\"name\":\"Stock4\",\"quantity\":1}]}"))
                .andExpect(status().isOk());

        mvc.perform(post("/wallets/w2/stocks/Stock4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"buy\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/wallets/w2/stocks/Stock4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"buy\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.log.length()").value(1))
                .andExpect(jsonPath("$.log[0].type").value("buy"))
                .andExpect(jsonPath("$.log[0].wallet_id").value("w2"))
                .andExpect(jsonPath("$.log[0].stock_name").value("Stock4"));
    }

    @Test
    void getWalletReturnsAllStocks() throws Exception {
        mvc.perform(post("/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stocks\":[{\"name\":\"Stock5\",\"quantity\":5},{\"name\":\"Stock6\",\"quantity\":5}]}"))
                .andExpect(status().isOk());

        mvc.perform(post("/wallets/myWallet/stocks/Stock5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"buy\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/wallets/myWallet/stocks/Stock6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"buy\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/wallets/myWallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("myWallet"))
                .andExpect(jsonPath("$.stocks.length()").value(2));
    }
}