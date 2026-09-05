package com.seeder.seeder.seed;

import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.springframework.stereotype.Component;

/**
 * Seeder untuk domain order (order-db). Idempotent via ON CONFLICT (id).
 * Schema order-service: id uuid, product_id uuid, user_id uuid, quantity, payment_status.
 */
@Component
public class OrderSeeder implements Seeder {

    @Override
    public String domain() {
        return "order";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public void seed(SeedContext ctx) {
        var jdbc = ctx.jdbc("order");
        // Ambil user id (uuid) pertama dari identity, lalu buat order demo.
        // Idempotent: skip kalau sudah ada order.
        Integer count = jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class);
        if (count != null && count > 0) {
            ctx.log().info("Orders already exist, skipping seed");
            return;
        }
        var users = jdbc.queryForList("SELECT id FROM users");
        if (users.isEmpty()) {
            ctx.log().warn("No users found, skipping order seed");
            return;
        }
        Object userId = users.get(0).get("id");
        var products = jdbc.queryForList("SELECT id FROM products LIMIT 1");
        if (products.isEmpty()) {
            ctx.log().warn("No products found, skipping order seed");
            return;
        }
        Object productId = products.get(0).get("id");
        jdbc.update("""
            INSERT INTO orders (product_id, user_id, quantity, payment_status)
            VALUES (?, ?, 2, 'SUCCESS')
            """, productId, userId);
        ctx.log().info("Seeded demo order");
    }
}