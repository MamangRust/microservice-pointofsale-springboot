package com.seeder.seeder.seed;

import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.springframework.stereotype.Component;

/**
 * Seeder untuk domain role (role-db). Idempotent via ON CONFLICT (role_name).
 */
@Component
public class RoleSeeder implements Seeder {

    @Override
    public String domain() {
        return "role";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void seed(SeedContext ctx) {
        var jdbc = ctx.jdbc("role");
        jdbc.update("""
            INSERT INTO roles (role_name) VALUES ('ROLE_ADMIN'), ('ROLE_STAFF'), ('ROLE_USER')
            ON CONFLICT (role_name) DO NOTHING
            """);
        ctx.log().info("Seeded roles (idempotent)");
    }
}