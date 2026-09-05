package com.common.seed;

import org.slf4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Context passed to each Seeder — provides per-domain data sources + helpers.
 * Mirrors Quarkus SeedContext (reactive pool) adapted to Spring JDBC.
 */
public record SeedContext(Map<String, DataSource> dataSources, Logger log, PasswordUtil passwordUtil) {

    /** Returns a JdbcTemplate bound to the given domain's database. */
    public JdbcTemplate jdbc(String domain) {
        DataSource ds = dataSources.get(domain);
        if (ds == null) {
            throw new IllegalStateException("No data source registered for domain: " + domain);
        }
        return new JdbcTemplate(ds);
    }
}