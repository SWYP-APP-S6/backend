create table admins (
    id         bigint generated always as identity primary key,
    email      varchar(255) not null,
    name       varchar(255) not null,
    type       varchar(20)  not null check (type in ('SUPER', 'MANAGER', 'DEVELOPER')),
    password   varchar(255) not null,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    deleted_at timestamptz
);

-- Soft-deleted admins keep their row, so a plain unique(email) would block re-registering a
-- previously deleted address. Scope uniqueness to live rows only.
create unique index uq_admins_email_active on admins (email) where deleted_at is null;

-- Dev seed: a first SUPER admin so the system can be bootstrapped (creating admins is itself an
-- admin-gated action). Password is 'swyp-admin-1234' (BCrypt hash below). DEV ONLY — rotate or
-- remove this admin before production.
insert into admins (email, name, type, password, created_at, updated_at)
values ('admin@swyp.com', 'Super Admin', 'SUPER',
        '$2a$10$d/Uurstls9aEwNeY19bwL.2gNOA1MAPPse7.pHjw.P8sRvQyltPPm', now(), now());
