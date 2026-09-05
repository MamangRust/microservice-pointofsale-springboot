-- Cashier domain — pos_identity
CREATE TABLE cashiers (
    cashier_id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    deleted_at TIMESTAMP
);

-- Reference indexes for FK lookups to merchant-service and user-service
CREATE INDEX idx_cashiers_merchant_id ON cashiers (merchant_id);
CREATE INDEX idx_cashiers_user_id ON cashiers (user_id);
