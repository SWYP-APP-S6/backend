-- ============================================================
-- recipes.cook_time_minutes
-- ============================================================
-- C-040-02 정렬 기준("조리시간 순 상위 3건")이자 C-040-03 레시피 카드 표시 항목인데 V0002에
-- 빠져 있었다. `cook_method`(굽기/끓이기)는 조리방법이라 대체할 수 없다.
--
-- nullable로 둔다: 미결 8(레시피 DB 직접구축 vs 외부 API)이 아직 안 풀려 데이터 출처가
-- 확정되지 않았고, 출처에 따라 전 행을 채우지 못할 수 있다. 조회 측은 값이 있다고 가정하지 말고
-- `order by cook_time_minutes nulls last`로 정렬한다.
--
-- 순수 추가 nullable 컬럼이므로 expand→contract 없이 1단계로 안전하다.
alter table recipes
    add column cook_time_minutes smallint check (cook_time_minutes > 0);
