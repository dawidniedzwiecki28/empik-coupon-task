CREATE TABLE coupon_redemptions (
    id          UUID         NOT NULL,
    coupon_id   UUID         NOT NULL,
    user_id     VARCHAR(255) NOT NULL,
    redeemed_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_coupon_redemptions PRIMARY KEY (id),
    CONSTRAINT fk_redemptions_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    -- Enforces one redemption per user per coupon at the DB level (safe under concurrency).
    CONSTRAINT uq_redemption_coupon_user UNIQUE (coupon_id, user_id)
);
