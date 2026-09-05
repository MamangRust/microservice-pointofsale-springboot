package com.common.clickhouse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.clickhouse.jdbc.ClickHouseDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

/**
 * ClickHouse datasource configuration — used by stats-writer, stats-reader, and stats-backfill.
 * Mirrors Quarkus ClickHouseClient pattern.
 */
@Configuration
@ConditionalOnProperty(name = "clickhouse.enabled", havingValue = "true", matchIfMissing = false)
public class ClickHouseConfig {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseConfig.class);

    @Value("${clickhouse.url:jdbc:clickhouse://localhost:8123/pos_stats}")
    private String url;

    @Value("${clickhouse.username:default}")
    private String username;

    @Value("${clickhouse.password:}")
    private String password;

    @Bean
    public DataSource clickhouseDataSource() {
        try {
            var props = new Properties();
            props.setProperty("user", username);
            if (!password.isEmpty()) {
                props.setProperty("password", password);
            }
            props.setProperty("compress", "1");
            props.setProperty("max_buffer_size", "1000000");

            var ds = new ClickHouseDataSource(url, props);
            log.info("ClickHouse datasource configured: {}", url);
            return ds;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create ClickHouse datasource", e);
        }
    }

    @Bean
    public JdbcTemplate clickhouseJdbcTemplate(DataSource clickhouseDataSource) {
        return new JdbcTemplate(clickhouseDataSource);
    }
}