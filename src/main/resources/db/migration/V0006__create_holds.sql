create table holds (
    id             bigint generated always as identity primary key,
    user_id        bigint       not null,
    product_id     bigint       not null,
    -- BR-017 caps one hold at `user_hold_limit` (MVP 3), but that is a server setting the
    -- service re-validates, not a schema constant. The DB only guards the invariant.
    qty            integer      not null check (qty >= 1),
    status         varchar(20)  not null
                       check (status in ('HOLDING', 'COMPLETED', 'CANCELED', 'EXPIRED')),
    expires_at     timestamptz  not null,
    completed_at   timestamptz,
    canceled_at    timestamptz,
    -- O-030-03 `전부 취소할게요`: the owner cancels every hold on a product and the reason is
    -- pushed to the consumer as N-05.
    canceled_by    varchar(20)  check (canceled_by in ('USER', 'OWNER')),
    cancel_reason  varchar(200),
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    constraint fk_holds_user    foreign key (user_id) references users (id),
    constraint fk_holds_product foreign key (product_id) references products (id)
);

-- C-020-06: a user cannot hold the same product twice at once — the button turns into
-- `찜 확인하기` instead. Enforced here so a concurrent double request can't slip through.
create unique index uq_holds_user_product_holding on holds (user_id, product_id)
    where status = 'HOLDING';

-- Expiry batch scans for HOLDING rows past their expires_at (1분 주기, §5 만료 배치).
create index idx_holds_status_expires on holds (status, expires_at);
-- Active-hold aggregation for a product: active_hold_qty (BR-015·016 오버셀 판정), O-040 counts.
create index idx_holds_product_status on holds (product_id, status);
-- C-030 찜 내역 (진행중/지난 내역 조회).
create index idx_holds_user_status    on holds (user_id, status);
