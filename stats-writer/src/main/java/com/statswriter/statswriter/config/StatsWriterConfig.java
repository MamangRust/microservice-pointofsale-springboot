package com.statswriter.statswriter.config;

import com.common.clickhouse.ClickHouseConfig;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;

@Configuration
@Import(ClickHouseConfig.class)
public class StatsWriterConfig {
}