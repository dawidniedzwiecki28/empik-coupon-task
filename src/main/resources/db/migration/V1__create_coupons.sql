CREATE TABLE coupons (
    id           UUID         NOT NULL,
    -- Stored normalized (trim + upper-case) so UNIQUE enforces case-insensitive uniqueness.
    code         VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    max_uses     INTEGER      NOT NULL,
    current_uses INTEGER      NOT NULL DEFAULT 0,
    country      CHAR(2)      NOT NULL,
    CONSTRAINT pk_coupons PRIMARY KEY (id),
    CONSTRAINT uq_coupons_code UNIQUE (code),
    -- Enforce the normalized (trimmed + upper-case) form the app guarantees, so a direct
    -- write cannot bypass case-insensitive uniqueness.
    CONSTRAINT ck_coupons_code_normalized CHECK (code = upper(btrim(code))),
    CONSTRAINT ck_coupons_country_alpha2 CHECK (country ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_coupons_max_uses_positive CHECK (max_uses > 0),
    CONSTRAINT ck_coupons_current_uses_non_negative CHECK (current_uses >= 0),
    CONSTRAINT ck_coupons_uses_within_limit CHECK (current_uses <= max_uses)
);
