package com.common.seed;

/**
 * Seeder SPI contract — mirrors Quarkus com.sanedge.common.seed.Seeder.
 * Each domain registers one Seeder with a stable order() for cross-domain sequencing.
 */
public interface Seeder {

    /** Domain identifier, e.g. "identity", "merchant", "category". */
    String domain();

    /** Execution order: identity(10) → merchant/cashier(20) → category/product(30) → order(40) → transaction(50). */
    default int order() {
        return 100;
    }

    /**
     * Seed the domain. Must be idempotent — safe to re-run.
     *
     * @param ctx data sources + shared helpers
     */
    void seed(SeedContext ctx) throws Exception;
}