create extension if not exists pg_trgm;

-- ============================================================
-- recipes
-- ============================================================
create table recipes (
    id                bigint generated always as identity primary key,
    source            varchar(20)  not null default 'mfds',
    source_id         varchar(64)  not null,
    title             varchar(255) not null,
    category          varchar(32),
    cook_method       varchar(32),
    servings          smallint     not null default 1,
    image_url         varchar(512),
    image_thumb_url   varchar(512),
    origin_image_url  varchar(512),
    source_url        varchar(512),
    license           varchar(20)  not null default 'kogl',
    is_published      boolean      not null default false,
    view_count        integer      not null default 0,
    like_count        integer      not null default 0,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    constraint uq_recipes_source unique (source, source_id)
);

create index idx_recipes_pub_category on recipes (is_published, category);
create index idx_recipes_popular      on recipes (is_published, view_count desc);

-- Korean partial-match search: lets `title LIKE '%김치%'` use an index instead of a seq scan.
create index idx_recipes_title_trgm on recipes using gin (title gin_trgm_ops);

-- ============================================================
-- recipe_steps
-- ============================================================
create table recipe_steps (
    id        bigint generated always as identity primary key,
    recipe_id bigint   not null,
    seq       smallint not null,
    content   text     not null,
    image_url varchar(512),
    constraint uq_recipe_steps_recipe_seq unique (recipe_id, seq),
    constraint fk_steps_recipe foreign key (recipe_id)
        references recipes (id) on delete cascade
);

-- ============================================================
-- ingredients
-- ============================================================
create table ingredients (
    id       integer generated always as identity primary key,
    name     varchar(64) not null,
    norm_key varchar(64) not null,
    category varchar(32),
    constraint uq_ingredients_norm_key unique (norm_key)
);

create index idx_ingredients_category  on ingredients (category);
create index idx_ingredients_name_trgm on ingredients using gin (name gin_trgm_ops);

-- ============================================================
-- recipe_ingredients
-- ============================================================
create table recipe_ingredients (
    id            bigint generated always as identity primary key,
    recipe_id     bigint         not null,
    seq           smallint       not null,
    ingredient_id integer,
    group_name    varchar(64),
    amount        numeric(10, 2),
    unit          varchar(16),
    raw_text      varchar(255)   not null,
    constraint uq_recipe_ingredients_recipe_seq unique (recipe_id, seq),
    constraint fk_ri_recipe foreign key (recipe_id)
        references recipes (id) on delete cascade,
    -- Ingredient dictionary keeps getting merged/cleaned up post-launch (e.g. 쪽파/실파 merge);
    -- cascading here would silently wipe recipe_ingredients rows. raw_text still renders fine.
    constraint fk_ri_ingredient foreign key (ingredient_id)
        references ingredients (id) on delete set null
);

create index idx_ri_ingredient on recipe_ingredients (ingredient_id);

-- ============================================================
-- recipe_nutrition
-- ============================================================
create table recipe_nutrition (
    recipe_id        bigint      not null,
    basis            varchar(16) not null default 'PER_SERVING' check (basis in ('PER_SERVING', 'PER_100G')),
    serving_weight_g numeric(8, 2),
    calories         numeric(8, 2),
    carbs_g          numeric(8, 2),
    protein_g        numeric(8, 2),
    fat_g            numeric(8, 2),
    sodium_mg        numeric(8, 2),
    constraint pk_recipe_nutrition primary key (recipe_id),
    constraint fk_nutrition_recipe foreign key (recipe_id)
        references recipes (id) on delete cascade
);

-- ============================================================
-- recipe_tags
-- ============================================================
create table recipe_tags (
    id        bigint generated always as identity primary key,
    recipe_id bigint      not null,
    tag       varchar(32) not null,
    constraint uq_recipe_tags_recipe_tag unique (recipe_id, tag),
    constraint fk_tags_recipe foreign key (recipe_id)
        references recipes (id) on delete cascade
);

create index idx_recipe_tags_tag on recipe_tags (tag);

-- ============================================================
-- recipe_raw
-- ============================================================
-- Kept separate from `recipes` so a plain `select *` on the master table doesn't drag the raw
-- API payload along; only the ingestion/re-parsing batch reads this table.
create table recipe_raw (
    recipe_id  bigint      not null,
    payload    jsonb       not null,
    fetched_at timestamptz not null default now(),
    constraint pk_recipe_raw primary key (recipe_id),
    constraint fk_raw_recipe foreign key (recipe_id)
        references recipes (id) on delete cascade
);
