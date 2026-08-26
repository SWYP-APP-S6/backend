create table products (
    id                     bigint generated always as identity primary key,
    store_id               bigint       not null,
    name                   varchar(30)  not null,
    category               varchar(20)  not null
                               check (category in ('VEGETABLE', 'FRUIT', 'MEAT', 'SEAFOOD', 'SIDE_DISH', 'ETC')),
    initial_qty            integer      not null check (initial_qty >= 1),
    available_qty          integer      not null check (available_qty >= 0),
    held_qty               integer      not null default 0 check (held_qty >= 0),
    original_price         integer      not null check (original_price > 0),
    sale_price             integer      not null check (sale_price > 0),
    discount_rate          smallint     not null,
    pickup_start_at        timestamp    not null,
    pickup_end_at          timestamp    not null,
    photo_url              varchar(512) not null,
    -- v2.1 §2.1: MVP has no 판매중지 UI. An owner who wants no more holds sets available_qty = 0
    -- and the system flips to SOLD_OUT, so there is no SUSPENDED state to enter.
    status                 varchar(20)  not null default 'ON_SALE'
                               check (status in ('ON_SALE', 'SOLD_OUT', 'CLOSED')),
    -- Nullable timestamps rather than a boolean + timestamp pair, so "sent but no sent_at" is
    -- unrepresentable. null sent_at = 60% 재확인 미발송, null answered_at = 미응답 (O-050-07
    -- `나중에` / back), which is exactly what O-010-03's 재확인 배너 queries on.
    reconfirm_sent_at      timestamptz,
    reconfirm_answered_at  timestamptz,
    created_at             timestamptz  not null,
    updated_at             timestamptz  not null,
    constraint fk_products_store foreign key (store_id) references stores (id),
    constraint chk_products_price_order   check (sale_price < original_price),
    constraint chk_products_pickup_window check (pickup_start_at < pickup_end_at)
);

-- available_qty has no upper bound relative to initial_qty on purpose: O-030-02 lets the owner
-- raise the sellable count freely, and BR-015 allows the oversell case available_qty < active_hold_qty.

create index idx_products_store            on products (store_id);
create index idx_products_status_available on products (status, available_qty);

-- ============================================================
-- product_ingredients
-- ============================================================
-- Links a product to the same normalized ingredient dictionary the recipe schema uses
-- (V0002 `ingredients`), so recipe matching (FR-004-01) can join on ingredient_id instead of
-- fuzzy-matching free-text tags. O-020-06 caps a product at 5 tags; that is an input rule the
-- service enforces, not a schema invariant.
create table product_ingredients (
    product_id    bigint  not null,
    ingredient_id integer not null,
    constraint pk_product_ingredients primary key (product_id, ingredient_id),
    constraint fk_pi_product foreign key (product_id) references products (id)
        on delete cascade,
    -- Composite PK means ingredient_id can't be nulled on dictionary cleanup like
    -- recipe_ingredients does; losing a tag on merge is harmless, so cascade instead.
    constraint fk_pi_ingredient foreign key (ingredient_id) references ingredients (id)
        on delete cascade
);
