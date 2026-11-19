-- Preserve historical aggregates whose source cannot identify their population.
UPDATE reporting.reader_day SET population = 'UNKNOWN' WHERE population = 'UNCLASSIFIED';
UPDATE reporting.feed_daily SET population = 'UNKNOWN' WHERE population = 'UNCLASSIFIED';
UPDATE reporting.recommendation_daily SET population = 'UNKNOWN' WHERE population = 'UNCLASSIFIED';
UPDATE reporting.recommendation_position_funnel_daily
  SET population = 'UNKNOWN' WHERE population = 'UNCLASSIFIED';
UPDATE reporting.recommendation_profile_quality_daily
  SET population = 'UNKNOWN' WHERE population = 'UNCLASSIFIED';
