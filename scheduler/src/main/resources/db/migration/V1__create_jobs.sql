CREATE SCHEMA IF NOT EXISTS scheduler;

CREATE TABLE scheduler.jobs (
        id                    UUID PRIMARY KEY NOT NULL,
        name                  VARCHAR(255) NOT NULL,
        type                  VARCHAR(20) NOT NULL CHECK (type IN ('ONE_TIME', 'RECURRING')),
        cron_expression       VARCHAR(255),
        next_fire_time        TIMESTAMPTZ,
        status                VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
        target_url            TEXT NOT NULL,
        http_method           VARCHAR(10) NOT NULL CHECK (http_method IN ('GET', 'POST', 'PUT', 'DELETE', 'PATCH')),
        payload               TEXT,
        max_attempts          INT NOT NULL DEFAULT 3,
        attempt_count         INT NOT NULL DEFAULT 0,
        retry_backoff_seconds INT NOT NULL DEFAULT 30,
        owner_type            VARCHAR(20) NOT NULL CHECK (owner_type IN ('USER', 'SERVICE')),
        owner_id              BIGINT NOT NULL,
        version               INT NOT NULL DEFAULT 0,
        created_at            TIMESTAMPTZ NOT NULL,
        updated_at            TIMESTAMPTZ NOT NULL,

        CHECK (
            (type = 'ONE_TIME' AND cron_expression IS NULL) OR
            (type = 'RECURRING' AND cron_expression IS NOT NULL)
            )
);

CREATE INDEX idx_jobs_status_next_fire
    ON scheduler.jobs (status, next_fire_time);

CREATE TABLE scheduler.job_runs (
        id                  UUID PRIMARY KEY NOT NULL,
        job_id              UUID NOT NULL REFERENCES scheduler.jobs(id) ON DELETE CASCADE,
        attempt_number      INT NOT NULL,
        started_at          TIMESTAMPTZ NOT NULL,
        ended_at            TIMESTAMPTZ,
        status              VARCHAR(20) NOT NULL CHECK (status IN ('SUCCESS', 'FAILURE')),
        response_status_code INT,
        error_message       TEXT,
        duration_ms         BIGINT
);
