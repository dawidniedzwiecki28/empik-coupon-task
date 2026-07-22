CREATE TABLE coupon_redemptions (
    id          UUID         NOT NULL,
    coupon_id   UUID         NOT NULL,
    -- Opaque, client-supplied identifier. We do not own user identity, so it is
    -- persisted as an attribute of the redemption rather than a managed entity.
    user_id     VARCHAR(255) NOT NULL,
    redeemed_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_coupon_redemptions PRIMARY KEY (id),
    CONSTRAINT fk_redemptions_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    -- Enforces "one redemption per user per coupon" at the database level, so the
    -- rule holds under concurrency and across instances without application locking.
    -- Its backing index also serves coupon_id-prefixed lookups.
    CONSTRAINT uq_redemption_coupon_user UNIQUE (coupon_id, user_id)
);
