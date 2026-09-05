package com.seeder.seeder.seed;

import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.springframework.stereotype.Component;

/**
 * Seeder untuk domain category (category-db). Idempotent via ON CONFLICT (slug_category).
 */
@Component
public class CategorySeeder implements Seeder {

    @Override
    public String domain() {
        return "category";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void seed(SeedContext ctx) {
        var jdbc = ctx.jdbc("category");
        jdbc.update("""
            INSERT INTO categories (name, description, slug_category) VALUES
            ('Minuman', 'Semua jenis minuman', 'minuman'),
            ('Makanan', 'Semua jenis makanan', 'makanan'),
            ('Snack', 'Camilan ringan', 'snack')
            ON CONFLICT (slug_category) DO NOTHING
            """);
        ctx.log().info("Seeded categories (idempotent)");
    }
}