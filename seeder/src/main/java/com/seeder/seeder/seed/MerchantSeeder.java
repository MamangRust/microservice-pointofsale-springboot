package com.seeder.seeder.seed;

import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.springframework.stereotype.Component;

/**
 * Seeder untuk domain merchant (merchant-db). Idempotent via ON CONFLICT (merchant_no).
 */
@Component
public class MerchantSeeder implements Seeder {

    @Override
    public String domain() {
        return "merchant";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void seed(SeedContext ctx) {
        var jdbc = ctx.jdbc("merchant");
        jdbc.update("""
            INSERT INTO merchants (merchant_no, api_key, name, description, address, contact_email, contact_phone, status) VALUES
            ('10000000-0000-0000-0000-000000000001', 'demo-api-key-001', 'Demo Merchant', 'Merchant untuk demo POS', 'Jl. Merdeka 1, Jakarta', 'merchant@pos.local', '081234567890', 'SUCCESS'),
            ('10000000-0000-0000-0000-000000000002', 'demo-api-key-002', 'Warung Kopi Nusantara', 'Warung kopi modern', 'Jl. Sudirman 12, Bandung', 'kopi@pos.local', '081298765432', 'SUCCESS')
            ON CONFLICT (merchant_no) DO NOTHING
            """);
        ctx.log().info("Seeded merchants (idempotent)");
    }
}