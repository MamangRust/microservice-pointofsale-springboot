package com.common.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.clickhouse.jdbc.ClickHouseDataSource;

import javax.sql.DataSource;

/**
 * Direct unit test of {@link ClickHouseConfig#clickhouseDataSource()}.
 *
 * The ClickHouseDataSource constructor only parses the URL and stores
 * properties — no connection is opened until getConnection() is called —
 * so constructing the bean is network-free and safe to assert here.
 * getConnection() itself would need a live server and is NOT tested.
 */
class ClickHouseConfigTest {

    private ClickHouseConfig config;

    @BeforeEach
    void setUp() {
        config = new ClickHouseConfig();
        ReflectionTestUtils.setField(config, "url", "jdbc:clickhouse://localhost:8123/default");
        ReflectionTestUtils.setField(config, "username", "default");
        ReflectionTestUtils.setField(config, "password", "");
    }

    @Test
    void clickhouseDataSource_returnsClickHouseDataSourceWithoutNetwork() {
        DataSource dataSource = config.clickhouseDataSource();

        assertThat(dataSource).isInstanceOf(ClickHouseDataSource.class);
    }

    @Test
    void clickhouseDataSource_wrapsSqlExceptionInRuntime() {
        ReflectionTestUtils.setField(config, "url", "jdbc:clickhouse://");

        assertThatThrownBy(config::clickhouseDataSource)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create ClickHouse datasource");
    }
}
