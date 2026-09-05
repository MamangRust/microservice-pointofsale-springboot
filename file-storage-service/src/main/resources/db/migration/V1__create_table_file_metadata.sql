create table file_metadata (
    id uuid primary key default gen_random_uuid (),
    file_name varchar(255) not null,
    file_type varchar(100) not null,
    file_size bigint not null,
    file_path varchar(500) not null,
    uploaded_by uuid,
    uploaded_at timestamp not null default current_timestamp
);
