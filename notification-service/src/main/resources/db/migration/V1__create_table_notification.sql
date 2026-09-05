create table notifications (
    id uuid primary key default gen_random_uuid (),
    user_id uuid,
    recipient varchar(255) not null,
    title varchar(255) not null,
    message text not null,
    type varchar(50) not null,
    status varchar(50) not null,
    created_at timestamp not null default current_timestamp
);
