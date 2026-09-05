package com.seeder.seeder.seed;

import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.springframework.stereotype.Component;

/**
 * Seeder untuk domain transaction (transaction-db). Idempotent via ON CONFLICT (idempotency_key).
 */
@Component
public class TransactionSeeder implements Seeder {

    @Override
    public String domain() {
        return "transaction";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public void seed(SeedContext ctx) {
        var jdbc = ctx.jdbc("transaction");
        jdbc.update("""
            INSERT INTO transactions (order_id, merchant_id, payment_method, amount, change_amount, status, idempotency_key) VALUES
            (1, 1, 'CASH', 36000, 4000, 'SUCCESS', 'seed-txn-001')
            ON CONFLICT (idempotency_key) WHERE deleted_at IS NULL DO NOTHING
            """);
        ctx.log().info("Seeded transactions (idempotent)");
    }
}