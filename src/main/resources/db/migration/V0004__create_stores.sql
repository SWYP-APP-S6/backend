create table stores (
    id                   bigint generated always as identity primary key,
    owner_user_id        bigint        not null,
    name                 varchar(100)  not null,
    address              varchar(255)  not null,
    address_detail       varchar(255),
    phone                varchar(20)   not null,
    latitude             numeric(9, 6) not null,
    longitude            numeric(9, 6) not null,
    business_open_time   time          not null,
    business_close_time  time          not null,
    status               varchar(20)   not null default 'PENDING'
                             check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    created_at           timestamptz   not null,
    updated_at           timestamptz   not null,
    constraint uq_stores_owner unique (owner_user_id),
    constraint fk_stores_owner foreign key (owner_user_id) references users (id)
);
