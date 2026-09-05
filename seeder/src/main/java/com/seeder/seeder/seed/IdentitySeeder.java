package com.seeder.seeder.seed;

import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.springframework.stereotype.Component;

/**
 * Seeder untuk domain identity (user-db). Idempotent via ON CONFLICT (username).
 * Password di-hash BCrypt agar compatible dengan auth-service.
 */
@Component
public class IdentitySeeder implements Seeder {

    @Override
    public String domain() {
        return "identity";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void seed(SeedContext ctx) {
        var jdbc = ctx.jdbc("identity");
        String adminPw = ctx.passwordUtil().hashPassword("admin123");
        String cashierPw = ctx.passwordUtil().hashPassword("cashier123");
        String staffPw = ctx.passwordUtil().hashPassword("staff123");

        jdbc.update("""
            INSERT INTO users (username, password, email, role) VALUES
            ('admin', ?, 'admin@pos.local', 'ADMIN'),
            ('cashier', ?, 'cashier@pos.local', 'CASHIER'),
            ('staff', ?, 'staff@pos.local', 'STAFF')
            ON CONFLICT (username) DO NOTHING
            """, adminPw, cashierPw, staffPw);
        ctx.log().info("Seeded identity users (idempotent)");
    }
}