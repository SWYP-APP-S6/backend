-- ============================================================
-- products 판매 마감 배치 인덱스
-- ============================================================
-- ProductCloseService 가 1분마다 pickup_end_at 이 지난 상품을 CLOSED 로 바꾼다. 인덱스가 없으면
-- 한 바퀴마다 products 전체를 훑는데, 닫힌 상품은 지우지 않고 히스토리로 남기므로 그 비용이 매일
-- 늘어난다. 부분 인덱스라 CLOSED 가 쌓여도 인덱스는 "아직 안 닫힌 것"의 크기로만 남는다 --
-- 등록 시 pickup_end_at 은 24시간 안으로 잡히므로 대략 하루치 등록량이 상한이다.
create index idx_products_open_pickup_end on products (pickup_end_at) where status <> 'CLOSED';
