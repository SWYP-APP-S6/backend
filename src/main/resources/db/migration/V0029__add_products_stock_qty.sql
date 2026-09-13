-- 점주가 적는 "매장에 남은 수량"은 찜된 것까지 포함한 실제 총 수량이다. available_qty 는 거기서
-- 찜을 뺀 나머지라 0 에서 잘리고, 그래서 "찜이 재고보다 몇 개 많은지"가 사라진다 --
-- 그 숫자가 O-030 의 「재고가 N개 부족해요」이자 점주 홈의 「취소 안내가 필요한 찜」이다.
--
-- available_qty 는 그대로 둔다. 소비자 조회가 전부 그 컬럼을 본다.
alter table products
    add column stock_qty integer;

update products
set stock_qty = available_qty + held_qty;

alter table products
    alter column stock_qty set not null,
    add constraint products_stock_qty_check check (stock_qty >= 0);
