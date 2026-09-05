package com.seeder.seeder.seed;

import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.springframework.stereotype.Component;

/**
 * Seeder untuk domain product (product-db). Idempotent via ON CONFLICT (name).
 * Schema product-service: id uuid, name, description, price, quantity, image_id.
 */
@Component
public class ProductSeeder implements Seeder {

    @Override
    public String domain() {
        return "product";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void seed(SeedContext ctx) {
        var jdbc = ctx.jdbc("product");
        jdbc.update("""
            INSERT INTO products (name, description, price, quantity) VALUES
            ('Kopi Susu', 'Kopi susu gula aren', 18000.00, 50),
            ('Es Teh', 'Es teh manis segar', 5000.00, 100),
            ('Nasi Goreng', 'Nasi goreng spesial', 25000.00, 30),
            ('Pisang Goreng', 'Pisang goreng crispy', 10000.00, 40)
            ON CONFLICT (name) DO NOTHING
            """);
        ctx.log().info("Seeded products (idempotent)");
    }
}