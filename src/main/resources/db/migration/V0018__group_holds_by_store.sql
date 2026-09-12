-- 찜의 단위를 "상품 1건"에서 "한 가게에서의 한 번의 픽업"으로 바꾼다.
-- 같은 가게의 상품은 한 찜 안에 담기고(hold_items), 사용자는 진행 중인 찜을 하나만 가진다.
-- 그래서 "한 번에 한 가게에서만 찜"이 애플리케이션 조건이 아니라 유니크 인덱스로 강제된다.

create table hold_items
(
    id         bigint generated always as identity primary key,
    hold_id    bigint      not null,
    product_id bigint      not null,
    qty        integer     not null check (qty >= 1),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_hold_items_hold foreign key (hold_id) references holds (id),
    constraint fk_hold_items_product foreign key (product_id) references products (id),
    constraint uq_hold_items_hold_product unique (hold_id, product_id)
);

create index idx_hold_items_product on hold_items (product_id);

alter table holds
    add column store_id bigint;

insert into hold_items (hold_id, product_id, qty, created_at, updated_at)
select id, product_id, qty, created_at, updated_at
from holds;

update holds h
set store_id = p.store_id
from products p
where p.id = h.product_id;

-- 새 유니크 인덱스는 사용자당 진행 중 찜을 하나로 제한한다. 그 전에 만들어진 여러 건은
-- 가장 먼저 만든 것만 남기고 만료 처리하되, 잡고 있던 재고를 먼저 돌려준다 --
-- 상태만 바꾸면 available_qty 가 영영 모자란 채로 남는다.
update products p
set available_qty = p.available_qty + surplus.qty,
    held_qty      = greatest(p.held_qty - surplus.qty, 0),
    updated_at    = now()
from (select product_id, sum(qty) as qty
      from holds
      where status = 'HOLDING'
        and id not in (select min(id) from holds where status = 'HOLDING' group by user_id)
      group by product_id) surplus
where p.id = surplus.product_id;

update holds
set status     = 'EXPIRED',
    updated_at = now()
where status = 'HOLDING'
  and id not in (select min(id) from holds where status = 'HOLDING' group by user_id);

alter table holds
    alter column store_id set not null;

alter table holds
    add constraint fk_holds_store foreign key (store_id) references stores (id);

-- 사용자당 진행 중 찜 1건. 상품별로 걸려 있던 예전 제약을 대체한다(컬럼을 지우기 전에 먼저 내린다 --
-- product_id 를 drop 하면 이 인덱스도 함께 사라져 이름으로 지울 수 없게 된다).
drop index uq_holds_user_product_holding;

create unique index uq_holds_user_holding on holds (user_id) where status = 'HOLDING';

-- product_id 와 qty 는 not null 이라 남겨둘 수 없다: 새 코드의 insert 가 값을 주지 못한다.
-- 두 컬럼에 걸린 인덱스·제약(idx_holds_product_status, fk_holds_product, holds_qty_check)도 함께 사라진다.
alter table holds
    drop column product_id,
    drop column qty;

create index idx_holds_store_status on holds (store_id, status);
