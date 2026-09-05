create table users (
    id uuid primary key default gen_random_uuid (),
    username varchar(255) not null unique,
    password varchar(255) not null,
    email varchar(255) unique,
    role varchar(50) not null default 'USER'
);