-- 상품 상세의 추천 레시피와 레시피 상세가 `난이도 하` · `20분 소요` 배지를 보여주는데, 둘 다
-- 채울 값이 없었다. 식약처 원본(COOKRCP01)에 두 항목이 아예 없어서 재수집으로는 나오지 않는다.
--
-- 그래서 추정해 넣는다. 추정값을 사실처럼 두면 나중에 "이 난이도 틀렸다"는 말이 나왔을 때
-- 어디서 온 값인지 알 수 없으므로, 출처를 함께 적는다 -- 다시 돌릴 대상도 이 컬럼으로 고른다.
alter table recipes
    add column difficulty        varchar(10)
        check (difficulty in ('EASY', 'NORMAL', 'HARD')),
    add column difficulty_source varchar(10)
        check (difficulty_source in ('AI', 'HUMAN')),
    add column cook_time_source  varchar(10)
        check (cook_time_source in ('AI', 'HUMAN'));

-- 값과 출처는 함께 있거나 함께 없다. 한쪽만 있는 행은 "누가 넣었는지 모르는 값"이라 아무도
-- 손대지 못하게 된다.
alter table recipes
    add constraint chk_recipes_difficulty_source
        check ((difficulty is null) = (difficulty_source is null)),
    add constraint chk_recipes_cook_time_source
        check ((cook_time_minutes is null) = (cook_time_source is null));

comment on column recipes.difficulty is '조리 난이도. 원본에 없어 추정한 값이다';
comment on column recipes.difficulty_source is 'AI 가 추정했는지 사람이 넣었는지';
comment on column recipes.cook_time_source is 'AI 가 추정했는지 사람이 넣었는지';
