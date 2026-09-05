create table products (
    id uuid primary key default gen_random_uuid (),
    name varchar(255) not null,
    description varchar(255) not null,
    price decimal(10, 2) not null,
    quantity integer not null
);