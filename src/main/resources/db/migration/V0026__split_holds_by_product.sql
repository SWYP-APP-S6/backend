-- 찜의 단위를 "한 가게에서의 한 번의 픽업"에서 다시 "상품 1건"으로 되돌린다. 되돌리기는
-- 아니다 -- V0006 은 찜마다 카운트다운이 따로 돌았지만, 여기서는 한 손님이 같은 가게에서
-- 이어서 찜한 건들이 **처음 찜의 만료 시각을 그대로 물려받는다**. 화면에서 묶여 보이는 것은
-- 그대로 두고, 취소만 상품 단위로 가능해진다.
--
-- 점주가 재고 부족으로 취소할 때 "복숭아만 취소하고 애호박은 살린다"가 되어야 하는데,
-- hold_items 에는 상태가 없어 찜 전체를 취소할 수밖에 없었다.

-- 손님당 진행 중 찜 1건 제약을 먼저 내린다. 아래에서 항목을 행으로 펼치는 순간 위반한다.
drop index uq_holds_user_holding;

alter table holds
    add column product_id bigint,
    add column qty        integer,
    -- 한 손님이 같은 가게에서 이어서 찜한 건들을 묶는 키. 첫 찜의 id 를 그대로 쓴다 --
    -- 만료 시각으로 묶으면 우연히 같은 시각에 걸린 다른 방문까지 한 묶음이 된다.
    add column group_id   bigint;

-- 각 찜의 첫 항목은 원래 행에 그대로 채운다. 그래야 hold_cancel_credit_events.hold_id 가
-- 가리키던 찜이 살아남아 취소권 기록이 끊기지 않는다.
update holds h
set product_id = first_item.product_id,
    qty        = first_item.qty,
    group_id   = h.id
from (select distinct on (hold_id) hold_id, product_id, qty
      from hold_items
      order by hold_id, id) first_item
where first_item.hold_id = h.id;

-- 나머지 항목은 새 찜 행으로 펼친다. 상태·시각은 부모 찜의 것을 그대로 물려받는다 --
-- 만료 시각을 공유한다는 정책이 데이터에서도 그대로 성립한다.
insert into holds (user_id, store_id, product_id, qty, group_id, status, expires_at, completed_at,
                   canceled_at, canceled_by, cancel_reason, no_show_charged_at, expiry_reminded_at,
                   created_at, updated_at)
select h.user_id, h.store_id, i.product_id, i.qty, h.id, h.status, h.expires_at, h.completed_at,
       h.canceled_at, h.canceled_by, h.cancel_reason, h.no_show_charged_at, h.expiry_reminded_at,
       i.created_at, i.updated_at
from hold_items i
         join holds h on h.id = i.hold_id
where i.id <> (select min(x.id) from hold_items x where x.hold_id = i.hold_id);

-- 항목이 하나도 없는 찜은 앱을 거쳐서는 생길 수 없다(찜 생성과 항목 추가가 한 트랜잭션이다).
-- 그래도 남아 있다면 상품을 가리키지 못해 not null 을 세울 수 없으므로 흔적까지 함께 지운다.
delete from hold_cancel_credit_events where hold_id in (select id from holds where product_id is null);
delete from holds where product_id is null;

alter table holds
    alter column product_id set not null,
    alter column qty set not null,
    alter column group_id set not null,
    add constraint holds_qty_check check (qty >= 1),
    add constraint fk_holds_product foreign key (product_id) references products (id);

-- 같은 상품을 두 번 찜할 수는 없다. 다른 상품이라면 같은 가게 안에서 얼마든지 이어 찜한다.
create unique index uq_holds_user_product_holding on holds (user_id, product_id)
    where status = 'HOLDING';

-- 상품 단위 조회(재고 부족분 계산, 상품별 활성 찜)가 이 인덱스를 탄다.
create index idx_holds_product_status on holds (product_id, status);

-- 점주 화면은 손님 한 명의 묶음을 한 줄로 보여주고, 픽업 완료도 묶음 단위로 처리한다.
create index idx_holds_group on holds (group_id);

-- 묶음 키는 찜 id 와 별개의 시퀀스에서 뽑는다. 첫 찜의 id 를 쓰려면 insert 가 끝난 뒤에야
-- 값을 알 수 있어 not null 로 넣을 수 없다. 옮겨 온 값들과 겹치지 않게 현재 최대 id 뒤에서
-- 시작한다.
create sequence holds_group_id_seq;
select setval('holds_group_id_seq', (select coalesce(max(id), 1) from holds));

drop table hold_items;
