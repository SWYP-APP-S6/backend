-- O-003(상점 정보 등록) 와이어프레임이 요구하는데 스키마에 없던 세 항목을 받는다:
-- 가게 종류(다중선택), 영업 요일, 우편번호.

-- 우편번호는 nullable 이다 — O-003-01 의 예외 처리가 "주소 검색 실패 → 직접 입력 허용"이라
-- 우편번호 없이도 등록이 성립해야 한다. 우편번호 서비스는 5자리를 주지만 자릿수는 서비스가
-- 검증하고 컬럼은 여유를 둔다.
alter table stores
    add column postal_code varchar(10);

comment on column stores.postal_code is '주소 검색이 준 우편번호. 주소를 직접 입력한 경우 비어 있다';

-- 가게 종류. 화면이 최대 3개까지 고르게 하지만, 그 상한은 product_ingredients(태그 5개)와
-- 마찬가지로 서비스가 강제하는 입력 규칙이지 스키마 불변식이 아니다 — 정책이 바뀌면
-- 마이그레이션 없이 움직여야 한다.
create table store_categories (
    store_id bigint      not null,
    category varchar(20) not null
        check (category in ('VEGETABLE', 'FRUIT', 'MEAT', 'SEAFOOD',
                            'DAIRY_EGG', 'BAKERY', 'PREPARED_FOOD', 'ETC')),
    constraint pk_store_categories primary key (store_id, category),
    constraint fk_store_categories_store foreign key (store_id) references stores (id)
        on delete cascade
);

-- 영업 요일. java.time.DayOfWeek 이름을 그대로 쓴다(엔티티가 @Enumerated(STRING)).
create table store_business_days (
    store_id    bigint      not null,
    day_of_week varchar(10) not null
        check (day_of_week in ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
                               'FRIDAY', 'SATURDAY', 'SUNDAY')),
    constraint pk_store_business_days primary key (store_id, day_of_week),
    constraint fk_store_business_days_store foreign key (store_id) references stores (id)
        on delete cascade
);

-- 기존 행 백필. 둘 다 이번에 신설되는 항목이라 기존 가게에는 값이 없고, 등록 API 는 앞으로
-- 두 항목을 필수로 받으므로 기존 행만 빈 채로 남는다.
--
--   영업 요일 = 7일 전체. 지금까지 요일 개념 자체가 없어 모든 가게가 사실상 매일 영업이었다.
--   비워 두면 "오늘 영업하는 가게" 필터가 붙는 순간 기존 가게가 통째로 사라진다.
--
--   가게 종류 = ETC. 무엇을 파는 집인지 알 방법이 없으므로 추측하지 않고 '기타'로 둔다.
--   (수정 API 가 아직 없어 점주가 스스로 고칠 수단은 없다 — 붙으면 그때 정정된다.)
insert into store_business_days (store_id, day_of_week)
select s.id, d.day_of_week
from stores s
cross join (values ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'),
                   ('FRIDAY'), ('SATURDAY'), ('SUNDAY')) as d(day_of_week);

insert into store_categories (store_id, category)
select id, 'ETC'
from stores;
