package com.statsreader.statsreader.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Mirror Quarkus StatsQueryService — query ClickHouse with Redis cache-aside.
 * Cache key: apigw:stats:{type}:{sha1(sql)}
 */
@Service
public class StatsQueryService {

    private static final Logger log = LoggerFactory.getLogger(StatsQueryService.class);
    private static final int CACHE_TTL_SECONDS = 300;

    private final JdbcTemplate clickhouseJdbc;
    private final StringRedisTemplate redisTemplate;

    public StatsQueryService(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbc,
                              StringRedisTemplate redisTemplate) {
        this.clickhouseJdbc = clickhouseJdbc;
        this.redisTemplate = redisTemplate;
    }

    public List<Map<String, Object>> query(String cacheKey, String sql, Object... args) {
        String cached = redisTemplate.opsForValue().get("apigw:stats:" + cacheKey);
        if (cached != null) {
            log.debug("Cache hit: {}", cacheKey);
            return List.of(Map.of("data", cached)); // simplified — real impl uses JSON deserialization
        }
        log.debug("Cache miss: {}", cacheKey);
        List<Map<String, Object>> result = clickhouseJdbc.queryForList(sql, args);
        if (!result.isEmpty()) {
            redisTemplate.opsForValue().set("apigw:stats:" + cacheKey, result.toString(),
                CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        }
        return result;
    }

    // === Cashier stats ===

    public List<Map<String, Object>> cashierMonthlyTotalSales(int year, int month) {
        return query("cashier:monthly-total:" + year + "-" + month,
            "SELECT cashier_id, sum(total_amount) as total FROM pos_stats.order_daily " +
            "WHERE toYear(occurred_at) = ? AND toMonth(occurred_at) = ? GROUP BY cashier_id",
            year, month);
    }

    public List<Map<String, Object>> cashierYearlyTotalSales(int year) {
        return query("cashier:yearly-total:" + year,
            "SELECT cashier_id, sum(total_amount) as total FROM pos_stats.order_daily " +
            "WHERE toYear(occurred_at) = ? GROUP BY cashier_id", year);
    }

    public List<Map<String, Object>> cashierMonthlySales(int year) {
        return query("cashier:monthly:" + year,
            "SELECT toMonth(occurred_at) as month, sum(total_amount) as total FROM pos_stats.order_daily " +
            "WHERE toYear(occurred_at) = ? GROUP BY month ORDER BY month", year);
    }

    public List<Map<String, Object>> cashierYearlySales() {
        return query("cashier:yearly",
            "SELECT toYear(occurred_at) as year, sum(total_amount) as total FROM pos_stats.order_daily " +
            "GROUP BY year ORDER BY year");
    }

    // === Category stats ===

    public List<Map<String, Object>> categoryMonthlyTotalPrices(int year, int month) {
        return query("category:monthly-total:" + year + "-" + month,
            "SELECT count() as total, sum(total_amount) as revenue FROM pos_stats.order_daily " +
            "WHERE toYear(occurred_at) = ? AND toMonth(occurred_at) = ?", year, month);
    }

    // === Order stats ===

    public List<Map<String, Object>> orderMonthlyTotalRevenue(int year, int month) {
        return query("order:monthly-revenue:" + year + "-" + month,
            "SELECT sum(total_amount) as revenue FROM pos_stats.order_daily " +
            "WHERE toYear(occurred_at) = ? AND toMonth(occurred_at) = ?", year, month);
    }

    public List<Map<String, Object>> orderYearlyTotalRevenue(int year) {
        return query("order:yearly-revenue:" + year,
            "SELECT sum(total_amount) as revenue FROM pos_stats.order_daily " +
            "WHERE toYear(occurred_at) = ?", year);
    }

    // === Transaction stats ===

    public List<Map<String, Object>> transactionAmountMonthly(int year) {
        return query("txn:amount-monthly:" + year,
            "SELECT toMonth(occurred_at) as month, sum(amount) as total FROM pos_stats.transaction_daily " +
            "WHERE toYear(occurred_at) = ? GROUP BY month ORDER BY month", year);
    }

    public List<Map<String, Object>> transactionMethodMonthly(int year) {
        return query("txn:method-monthly:" + year,
            "SELECT toMonth(occurred_at) as month, payment_method, sum(amount) as total " +
            "FROM pos_stats.transaction_daily WHERE toYear(occurred_at) = ? " +
            "GROUP BY month, payment_method ORDER BY month", year);
    }

    public List<Map<String, Object>> transactionStatusMonthly(int year, int month, String status) {
        return query("txn:status-" + status + "-" + year + "-" + month,
            "SELECT count() as count, sum(amount) as total FROM pos_stats.transaction_daily " +
            "WHERE toYear(occurred_at) = ? AND toMonth(occurred_at) = ? AND status = ?",
            year, month, status);
    }
}