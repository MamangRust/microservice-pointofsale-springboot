CREATE TABLE merchants (
    merchant_id BIGSERIAL PRIMARY KEY, user_id BIGINT,
    merchant_no VARCHAR(36) NOT NULL UNIQUE, api_key VARCHAR(36) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL, description TEXT, address TEXT,
    contact_email VARCHAR(255), contact_phone VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT now(), updated_at TIMESTAMP DEFAULT now(), deleted_at TIMESTAMP
);
CREATE TABLE merchant_documents (
    document_id BIGSERIAL PRIMARY KEY, merchant_id BIGINT NOT NULL,
    document_type VARCHAR(50) NOT NULL, document_url TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING', note TEXT,
    created_at TIMESTAMP DEFAULT now(), updated_at TIMESTAMP DEFAULT now(), deleted_at TIMESTAMP
);
CREATE INDEX idx_merchant_documents_merchant ON merchant_documents(merchant_id);