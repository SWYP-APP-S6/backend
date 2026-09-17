-- 점주가 상품에 다는 식자재 태그는 ingredients 사전 중 사람이 고른 대표 행(is_tag)으로 한정한다.
-- 사전은 레시피 원문을 파싱한 결과라 같은 재료가 여러 이름으로 흩어져 있다(계란/달걀,
-- 돼지고기/돼지고기목살, '양념장: 간장'). 그런 행은 canonical_id 로 대표 태그를 가리켜, 레시피
-- 매칭이 대표 태그 기준으로 모이게 한다.
--
-- 값은 여기서 채우지 않는다. 사전 행 자체가 Flyway 밖 시드(mfds_cookrcp01.sql)로 들어오므로,
-- 새 환경에서는 이 마이그레이션이 빈 사전 위에서 돈다. 태그·별칭은 그 시드 뒤에 넣는
-- db/data/ingredient_tags.sql 이 채운다.
alter table ingredients
    add column is_tag       boolean not null default false,
    add column canonical_id integer;

-- 사전 정리로 대표 행이 지워져도 별칭 행과 그 행을 쓰는 recipe_ingredients 는 남아야 한다.
alter table ingredients
    add constraint fk_ingredients_canonical foreign key (canonical_id)
        references ingredients (id) on delete set null;

create index idx_ingredients_canonical on ingredients (canonical_id);
