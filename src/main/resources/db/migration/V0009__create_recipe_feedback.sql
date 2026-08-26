-- ============================================================
-- recipe_feedback
-- ============================================================
-- C-041-02 "도움됐어요 / 아니요" — 레시피 만족도 지표(성공지표 80%)의 유일한 수집경로.
-- 레시피당 1회지만 응답을 바꿀 수 있으므로(user_id, recipe_id) 유니크 + UPSERT로 다룬다.
create table recipe_feedback (
    id         bigint generated always as identity primary key,
    user_id    bigint      not null,
    recipe_id  bigint      not null,
    helpful    boolean     not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_recipe_feedback_user_recipe unique (user_id, recipe_id),
    constraint fk_rf_user   foreign key (user_id)   references users (id),
    constraint fk_rf_recipe foreign key (recipe_id) references recipes (id)
);

-- 레시피별 만족도(helpful=true 비율) 집계용.
create index idx_recipe_feedback_recipe on recipe_feedback (recipe_id);
