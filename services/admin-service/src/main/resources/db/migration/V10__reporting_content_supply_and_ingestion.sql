CREATE TABLE IF NOT EXISTS reporting.content_supply_snapshot (
    collected_at TIMESTAMPTZ NOT NULL,
    active_books BIGINT NOT NULL,
    available_asset_versions BIGINT NOT NULL,
    analyzed_asset_versions BIGINT NOT NULL,
    eligible_excerpts BIGINT NOT NULL,
    ranked_excerpts BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (collected_at)
);

CREATE INDEX IF NOT EXISTS reporting_content_supply_time_idx
  ON reporting.content_supply_snapshot(collected_at DESC);

CREATE TABLE IF NOT EXISTS reporting.ingestion_daily (
    day DATE NOT NULL,
    job_status VARCHAR(24) NOT NULL,
    job_count BIGINT NOT NULL,
    discovered_items BIGINT NOT NULL,
    processed_items BIGINT NOT NULL,
    succeeded_items BIGINT NOT NULL,
    failed_items BIGINT NOT NULL,
    item_status VARCHAR(24),
    item_count BIGINT NOT NULL,
    calculation_version VARCHAR(120) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL,
    source_watermark TIMESTAMPTZ,
    PRIMARY KEY (day, job_status, item_status)
);

CREATE INDEX IF NOT EXISTS reporting_ingestion_daily_day_idx
  ON reporting.ingestion_daily(day DESC, job_status, item_status);
