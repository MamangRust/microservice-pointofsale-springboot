package com.seeder.seeder.lifecycle;

import com.common.seed.PasswordUtil;
import com.common.seed.SeedContext;
import com.common.seed.Seeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One-shot orchestrator — mirrors Quarkus SeederLifecycle.
 * Discovers all Seeder beans, filters by SEED_DOMAINS env, sorts by order(),
 * runs each, then exits.
 */
@Component
public class SeederLifecycle implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeederLifecycle.class);

    private final List<Seeder> seeders;
    private final Map<String, DataSource> dataSources;
    private final PasswordUtil passwordUtil;

    @Value("${seed.domains:}")
    private String seedDomainsFilter;

    public SeederLifecycle(List<Seeder> seeders,
                           Map<String, DataSource> dataSources) {
        this.seeders = seeders;
        this.dataSources = dataSources;
        this.passwordUtil = new PasswordUtil();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Set<String> filter = seedDomainsFilter.isBlank()
            ? Set.of()
            : Arrays.stream(seedDomainsFilter.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        List<Seeder> toRun = seeders.stream()
            .filter(s -> filter.isEmpty() || filter.contains(s.domain()))
            .sorted(Comparator.comparingInt(Seeder::order))
            .toList();

        log.info("Seeder: {} seeders to run (filter={})", toRun.size(), seedDomainsFilter);

        SeedContext ctx = new SeedContext(dataSources, log, passwordUtil);

        for (Seeder seeder : toRun) {
            try {
                log.info("Seed: {} (order={})", seeder.domain(), seeder.order());
                seeder.seed(ctx);
                log.info("Seed complete: {}", seeder.domain());
            } catch (Exception e) {
                log.error("Seed failed: {} — {}", seeder.domain(), e.getMessage(), e);
                System.exit(1);
            }
        }

        log.info("All seeders completed successfully");
        System.exit(0);
    }
}