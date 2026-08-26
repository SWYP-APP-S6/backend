-- ============================================================
-- domain_events
-- ============================================================
-- Backend-side source of truth for business KPIs (§9 지표 산식) that client-side analytics
-- (GA/Firebase) can't cover reliably: events with no persisted row of their own (hold_fail —
-- a rejected concurrent hold never creates a `holds` row) or that need a before/after diff
-- `products`/`holds` don't retain (stock_adjust). Dimension FKs are nullable because each
-- event_type only populates the ones relevant to it; event-specific attributes go in payload
-- rather than a wide sparse column set (same reasoning as V0002 recipe_raw.payload).
create table domain_events (
    id          bigint generated always as identity primary key,
    event_type  varchar(40) not null,
    user_id     bigint,
    store_id    bigint,
    product_id  bigint,
    hold_id     bigint,
    recipe_id   bigint,
    payload     jsonb       not null default '{}'::jsonb,
    created_at  timestamptz not null,
    updated_at  timestamptz not null,
    constraint fk_de_user    foreign key (user_id)    references users (id),
    constraint fk_de_store   foreign key (store_id)   references stores (id),
    constraint fk_de_product foreign key (product_id) references products (id),
    constraint fk_de_hold    foreign key (hold_id)    references holds (id),
    constraint fk_de_recipe  foreign key (recipe_id)  references recipes (id)
);

-- Main query shape: count/list events of one type over a date range.
create index idx_de_type_created on domain_events (event_type, created_at);
create index idx_de_product      on domain_events (product_id);
create index idx_de_user         on domain_events (user_id);
