create table if not exists orders (
    id uuid primary key default gen_random_uuid (),
    product_id uuid not null,
    user_id uuid not null,
    quantity int not null,
    payment_status VARCHAR(50) DEFAULT 'PENDING'
);