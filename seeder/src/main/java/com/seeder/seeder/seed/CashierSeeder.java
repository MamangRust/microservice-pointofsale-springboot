package com.seeder.seeder.seed;

import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.springframework.stereotype.Component;

/**
 * Seeder untuk domain cashier (cashier-db). Idempotent via ON CONFLICT (cashier_id).
 */
@Component
public class CashierSeeder implements Seeder {

    @Override
    public String domain() {
        return "cashier";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void seed(SeedContext ctx) {
        var jdbc = ctx.jdbc("cashier");
        jdbc.update("""
            INSERT INTO cashiers (merchant_id, user_id, name) VALUES
            (1, 2, 'Kasir Utama'),
            (2, 3, 'Kasir Warung')
            ON CONFLICT (cashier_id) DO NOTHING
            """);
        ctx.log().info("Seeded cashiers (idempotent)");
    }
}