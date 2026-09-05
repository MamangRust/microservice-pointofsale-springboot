package com.seeder.seeder.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Registers one DataSource per domain database so seeders can write to each
 * service's own PostgreSQL instance.
 */
@Configuration
public class SeederDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(SeederDataSourceConfig.class);

    @Value("${seed.db.identity-url:jdbc:postgresql://user-db:5432/user_service}")
    private String identityUrl;

    @Value("${seed.db.merchant-url:jdbc:postgresql://merchant-db:5432/merchant_service}")
    private String merchantUrl;

    @Value("${seed.db.cashier-url:jdbc:postgresql://cashier-db:5432/cashier_service}")
    private String cashierUrl;

    @Value("${seed.db.category-url:jdbc:postgresql://category-db:5432/category_service}")
    private String categoryUrl;

    @Value("${seed.db.product-url:jdbc:postgresql://product-db:5432/product_service}")
    private String productUrl;

    @Value("${seed.db.order-url:jdbc:postgresql://order-db:5432/order_service}")
    private String orderUrl;

    @Value("${seed.db.order-item-url:jdbc:postgresql://order-item-db:5432/order_item_service}")
    private String orderItemUrl;

    @Value("${seed.db.transaction-url:jdbc:postgresql://transaction-db:5432/transaction_service}")
    private String transactionUrl;

    @Value("${seed.db.username:postgres}")
    private String username;

    @Value("${seed.db.password:password}")
    private String password;

    @Bean
    public Map<String, DataSource> seedDataSources() {
        Map<String, DataSource> map = new HashMap<>();
        map.put("identity", dataSource(identityUrl));
        map.put("merchant", dataSource(merchantUrl));
        map.put("cashier", dataSource(cashierUrl));
        map.put("category", dataSource(categoryUrl));
        map.put("product", dataSource(productUrl));
        map.put("order", dataSource(orderUrl));
        map.put("order_item", dataSource(orderItemUrl));
        map.put("transaction", dataSource(transactionUrl));
        log.info("Registered {} seed data sources", map.size());
        return map;
    }

    private DataSource dataSource(String url) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }
}