create table notifications (
    id         bigint generated always as identity primary key,
    user_id    bigint       not null,
    type       varchar(30)  not null,
    title      varchar(100) not null,
    body       varchar(255) not null,
    deep_link  varchar(255),
    read_at    timestamptz,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    constraint fk_notifications_user foreign key (user_id) references users (id)
);

create index idx_notifications_user_unread on notifications (user_id, read_at);
