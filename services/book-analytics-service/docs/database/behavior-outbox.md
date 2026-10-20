# Behavior outbox sink

A migration `V17__behavior_outbox_sink.sql` creates
`analytics.behavior_event`, keyed by the source `event_id`. The table stores
the event type, JSON payload, occurrence time, and receipt time. The unique key
makes retries from `behavior-service` idempotent; the behavior outbox is marked
published only after the sink returns a successful response.
