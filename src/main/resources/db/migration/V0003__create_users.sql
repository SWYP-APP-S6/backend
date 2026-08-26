create table users (
    id                  bigint generated always as identity primary key,
    role                varchar(20)  not null check (role in ('CONSUMER', 'OWNER')),
    oauth_provider      varchar(20),
    oauth_provider_id   varchar(100),
    nickname            varchar(50)  not null,
    phone               varchar(20),
    marketing_opt_in    boolean      not null default false,
    terms_agreed_at     timestamptz  not null,
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null
);

-- Kakao login lands later (per team convention); provider/provider_id stay nullable until
-- then, so the unique identity check only applies once both are actually populated.
create unique index uq_users_oauth_identity on users (oauth_provider, oauth_provider_id)
    where oauth_provider_id is not null;
