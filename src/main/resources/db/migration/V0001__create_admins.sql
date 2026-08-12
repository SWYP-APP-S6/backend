create table admins (
    id         bigint generated always as identity primary key,
    email      varchar(255) not null,
    name       varchar(255) not null,
    type       varchar(20)  not null check (type in ('SUPER', 'MANAGER', 'DEVELOPER')),
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    deleted_at timestamptz
);

-- Soft-deleted admins keep their row, so a plain unique(email) would block re-registering a
-- previously deleted address. Scope uniqueness to live rows only.
create unique index uq_admins_email_active on admins (email) where deleted_at is null;
