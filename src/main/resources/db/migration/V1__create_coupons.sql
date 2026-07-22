CREATE TABLE coupons (
    id           UUID         NOT NULL,
    -- Stored already normalized (trimmed + upper-case) so the UNIQUE constraint
    -- enforces case-insensitive uniqueness (WIOSNA == wiosna) at the database level.
    code         VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    max_uses     INTEGER      NOT NULL,
    current_uses INTEGER      NOT NULL DEFAULT 0,
    -- ISO 3166-1 alpha-2, upper-case.
    country      CHAR(2)      NOT NULL,
    CONSTRAINT pk_coupons PRIMARY KEY (id),
    CONSTRAINT uq_coupons_code UNIQUE (code),
    CONSTRAINT ck_coupons_max_uses_positive CHECK (max_uses > 0),
    CONSTRAINT ck_coupons_current_uses_non_negative CHECK (current_uses >= 0),
    CONSTRAINT ck_coupons_uses_within_limit CHECK (current_uses <= max_uses)
);
