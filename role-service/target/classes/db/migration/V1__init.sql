-- Role domain — pos_identity
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    deleted_at TIMESTAMP
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- Seed base roles (idempotent)
INSERT INTO roles (role_name) VALUES ('ROLE_ADMIN'), ('ROLE_STAFF'), ('ROLE_USER')
ON CONFLICT (role_name) DO NOTHING;