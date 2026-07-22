CREATE TABLE coupon_redemptions (
    coupon_id   UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    redeemed_at TIMESTAMPTZ NOT NULL,
    -- Natural key: one redemption per user per coupon. Enforces the rule under concurrency
    -- and doubles as the coupon_id-prefixed lookup index — no surrogate id needed.
    CONSTRAINT pk_coupon_redemptions PRIMARY KEY (coupon_id, user_id),
    CONSTRAINT fk_redemptions_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id)
);
