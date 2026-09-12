-- 런칭 전에 넣어 두는 **샘플** 데이터. 실존 인물·가게가 아니고 전화번호도 유효하지 않다.
-- 운영에 필요한 실제 데이터(관리자·레시피)는 init_data_1.sql 이다.
--
--   psql "postgresql://swyp:swyp@localhost:5432/swyp" -v ON_ERROR_STOP=1 \
--     -f src/main/resources/db/data/test_data_1.sql
--
-- 재실행 = 교체다. 이 파일이 만든 행만 지우고 다시 넣으므로, 동료들이 관리자 페이지에서
-- 만든 데이터는 남는다. 표시는 users.oauth_provider = 'seed' 하나로 한다.
--
-- **픽업 시각이 now() 기준 상대값**이라 언제 넣어도 판매중이다. 시간이 지나 목록이 비면
-- 이 파일을 다시 돌리면 된다 -- 마감이 지난 채로 굳는 시드가 "필터가 고장 났나" 하는
-- 오해를 만든다.
--
-- ============================================================
-- 위치 -- 기준점에서 거리 구간이 갈리게 배치했다
-- ============================================================
-- 기준점은 역삼역 부근(37.506900, 127.036500)이고, admin-web 탐색 테스트의 첫 프리셋과 같다.
-- 경도는 전부 같게 두고 위도만 움직여 거리가 한눈에 계산되게 했다(위도 1도 ≈ 111km).
--
--   수경야채      37.506900      0 m  기준점
--   신사 반찬     37.509000    234 m  PENDING -- 승인 전이라 탐색에 안 뜬다
--   역삼 정육     37.513200    701 m  북
--   역삼 청과     37.500600    701 m  남
--   도곡 반찬     37.520400  1,503 m  기본 반경(1km) 밖, 2km 안
--   한강 수산     37.533900  3,006 m  2km 밖, 5km 안
--   성수 베이커리 37.560800  6,000 m  최대 반경(5km) 밖 -- 어떤 값으로도 안 잡힌다
--
-- 그래서 radiusMeters 를 500 → 1000 → 2000 → 5000 으로 올리면 매장이
-- 1 → 3 → 4 → 5 곳으로 늘고, 6km 짜리는 끝까지 안 나온다.
--
-- 영업 요일도 가게마다 다르다. 오늘 쉬는 가게는 목록·지도에서 빠지고 찜도 거절된다
-- (STORE_CLOSED_TODAY) -- 요일을 바꿔 가며 눌러 볼 수 있게 월·화·일 휴무를 섞어 두었다.

begin;

-- ------------------------------------------------------------
-- 기존 샘플 제거. FK 에 cascade 가 없는 쪽부터 차례로 지운다.
-- ------------------------------------------------------------
create temp table seed_users on commit drop as
select id from users where oauth_provider = 'seed';

create temp table seed_stores on commit drop as
select id from stores where owner_user_id in (select id from seed_users);

-- 샘플 가게에 찜한 실제 테스트 계정의 찜도 함께 지운다 -- 상품을 지우려면 먼저 사라져야 한다.
create temp table seed_holds on commit drop as
select id from holds
where store_id in (select id from seed_stores)
   or user_id in (select id from seed_users);

delete from hold_items where hold_id in (select id from seed_holds);
delete from holds where id in (select id from seed_holds);
delete from hold_cancel_credits where user_id in (select id from seed_users);
delete from notifications where user_id in (select id from seed_users);
delete from products where store_id in (select id from seed_stores);
delete from stores where id in (select id from seed_stores);
delete from user_locations where user_id in (select id from seed_users);
delete from users where id in (select id from seed_users);

-- ------------------------------------------------------------
-- 유저 -- 점주 7명(가게 하나씩) + 소비자 5명
-- ------------------------------------------------------------
insert into users (role, oauth_provider, oauth_provider_id, nickname, phone,
                   marketing_opt_in, terms_agreed_at, created_at, updated_at)
values
  ('OWNER',    'seed', 'seed-o1', '수경야채',      '0212340001', false, now() - interval '40 days', now() - interval '40 days', now()),
  ('OWNER',    'seed', 'seed-o2', '역삼정육',      '0212340002', true,  now() - interval '35 days', now() - interval '35 days', now()),
  ('OWNER',    'seed', 'seed-o3', '역삼청과',      '0212340003', false, now() - interval '32 days', now() - interval '32 days', now()),
  ('OWNER',    'seed', 'seed-o4', '도곡반찬',      '0212340004', true,  now() - interval '21 days', now() - interval '21 days', now()),
  ('OWNER',    'seed', 'seed-o5', '한강수산',      '0212340005', false, now() - interval '14 days', now() - interval '14 days', now()),
  ('OWNER',    'seed', 'seed-o6', '성수베이커리',  '0212340006', true,  now() - interval '9 days',  now() - interval '9 days',  now()),
  ('OWNER',    'seed', 'seed-o7', '신사반찬',      '0212340007', false, now() - interval '2 days',  now() - interval '2 days',  now()),
  ('CONSUMER', 'seed', 'seed-c1', '한소영',        '01000000001', true,  now() - interval '30 days', now() - interval '30 days', now()),
  ('CONSUMER', 'seed', 'seed-c2', '박지훈',        '01000000002', false, now() - interval '24 days', now() - interval '24 days', now()),
  ('CONSUMER', 'seed', 'seed-c3', '이서연',        null,          true,  now() - interval '18 days', now() - interval '18 days', now()),
  ('CONSUMER', 'seed', 'seed-c4', '최민준',        '01000000004', false, now() - interval '11 days', now() - interval '11 days', now()),
  ('CONSUMER', 'seed', 'seed-c5', '정예린',        '01000000005', true,  now() - interval '3 days',  now() - interval '3 days',  now());

-- 기본 동네. 위치 권한을 거부한 사용자의 탐색 기준이다.
-- region_code 는 비워 둔다(V0013 주석 -- Geocoding 응답에 행정동 코드가 없다).
insert into user_locations (user_id, region_name, latitude, longitude, created_at, updated_at)
select u.id, v.region_name, v.latitude, v.longitude, now(), now()
from (values
  ('seed-c1', '서울특별시 강남구 역삼동', 37.500600, 127.036500),
  ('seed-c2', '서울특별시 강남구 도곡동', 37.520400, 127.036500),
  ('seed-c4', '서울특별시 성동구 성수동', 37.544600, 127.055900)
) as v(oauth_id, region_name, latitude, longitude)
join users u on u.oauth_provider = 'seed' and u.oauth_provider_id = v.oauth_id;

-- ------------------------------------------------------------
-- 가게
-- ------------------------------------------------------------
insert into stores (owner_user_id, name, postal_code, address, address_detail, phone,
                    latitude, longitude, business_open_time, business_close_time, status,
                    business_registration_number, application_note, created_at, updated_at)
select u.id, v.name, v.postal_code, v.address, v.address_detail, v.phone,
       v.latitude, v.longitude, v.open_time, v.close_time, v.status,
       v.brn, v.note, now() - v.age, now()
from (values
  ('seed-o1', '수경야채', '06236', '서울특별시 강남구 테헤란로 152', '1층', '0212340001',
   37.506900, 127.036500, time '09:00', time '21:00', 'APPROVED', '111-11-11111',
   '역삼동에서 수경재배 채소를 팝니다. 당일 수확분만 취급해서 저녁이면 늘 물량이 남습니다.', interval '40 days'),
  ('seed-o2', '역삼 정육', '06232', '서울특별시 강남구 역삼로 180', null, '0212340002',
   37.513200, 127.036500, time '10:00', time '20:00', 'APPROVED', '222-22-22222',
   '국내산 돼지고기와 소고기를 다룹니다. 당일 소분한 고기는 다음 날 팔지 않는 것이 원칙입니다.', interval '35 days'),
  ('seed-o3', '역삼 청과', '06224', '서울특별시 강남구 역삼로 92', '지하1층', '0212340003',
   37.500600, 127.036500, time '08:00', time '21:00', 'APPROVED', '333-33-33333',
   '새벽 가락시장에서 직접 떼어 옵니다. 그날 못 판 과일은 다음 날이면 상품성이 떨어집니다.', interval '32 days'),
  ('seed-o4', '도곡 반찬', '06253', '서울특별시 강남구 도곡로 233', null, '0212340004',
   37.520400, 127.036500, time '08:30', time '19:30', 'APPROVED', '444-44-44444',
   '매일 아침 조리해 당일에만 판매합니다. 저녁 7시가 지나면 남은 반찬을 전부 폐기해 왔습니다.', interval '21 days'),
  ('seed-o5', '한강 수산', '04788', '서울특별시 성동구 왕십리로 315', '2층', '0212340005',
   37.533900, 127.036500, time '07:00', time '20:00', 'APPROVED', '555-55-55555',
   '노량진에서 새벽에 받아 옵니다. 손질해 둔 생선은 당일을 넘기지 않습니다.', interval '14 days'),
  ('seed-o6', '성수 베이커리', '04778', '서울특별시 성동구 아차산로 100', null, '0212340006',
   37.560800, 127.036500, time '07:30', time '22:00', 'APPROVED', '666-66-66666',
   '매장에서 직접 굽습니다. 마감 두 시간 전부터 남은 빵이 나옵니다.', interval '9 days'),
  ('seed-o7', '신사 반찬', '06022', '서울특별시 강남구 강남대로 618', '1층', '0212340007',
   37.509000, 127.036500, time '09:00', time '20:00', 'PENDING', '777-77-77777',
   '이번 주에 문을 열었습니다. 반찬 20여 가지를 매일 만들고 저녁에 남는 양이 적지 않습니다.', interval '2 days')
) as v(oauth_id, name, postal_code, address, address_detail, phone,
       latitude, longitude, open_time, close_time, status, brn, note, age)
join users u on u.oauth_provider = 'seed' and u.oauth_provider_id = v.oauth_id;

-- 가게 종류(화면은 최대 3개까지 고르게 한다)
insert into store_categories (store_id, category)
select s.id, v.category
from (values
  ('seed-o1', 'VEGETABLE'),
  ('seed-o2', 'MEAT'), ('seed-o2', 'DAIRY_EGG'),
  ('seed-o3', 'FRUIT'), ('seed-o3', 'VEGETABLE'),
  ('seed-o4', 'PREPARED_FOOD'),
  ('seed-o5', 'SEAFOOD'),
  ('seed-o6', 'BAKERY'), ('seed-o6', 'DAIRY_EGG'),
  ('seed-o7', 'PREPARED_FOOD'), ('seed-o7', 'ETC')
) as v(oauth_id, category)
join users u on u.oauth_provider = 'seed' and u.oauth_provider_id = v.oauth_id
join stores s on s.owner_user_id = u.id;

-- 영업 요일. 비워 두면 "오늘 영업하는 가게" 필터가 그 가게를 통째로 지운다.
-- 정육(일요일) · 반찬(월요일) · 베이커리(화요일) 휴무로 섞어 둔다.
insert into store_business_days (store_id, day_of_week)
select s.id, d.day_of_week
from (values
  ('seed-o1', null), ('seed-o2', 'SUNDAY'), ('seed-o3', null), ('seed-o4', 'MONDAY'),
  ('seed-o5', null), ('seed-o6', 'TUESDAY'), ('seed-o7', null)
) as v(oauth_id, closed_on)
join users u on u.oauth_provider = 'seed' and u.oauth_provider_id = v.oauth_id
join stores s on s.owner_user_id = u.id
cross join (values ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'),
                   ('FRIDAY'), ('SATURDAY'), ('SUNDAY')) as d(day_of_week)
where v.closed_on is null or d.day_of_week <> v.closed_on;

-- ------------------------------------------------------------
-- 상품
-- ------------------------------------------------------------
-- 픽업 시각은 지금 기준 상대값이다. 할인율은 엔티티(Product.discountRateOf)와 같은 식으로
-- 계산해 넣는다 -- 컬럼이 not null 이라 비워 둘 수 없고, 손으로 적으면 가격과 어긋난다.
--
-- 상태를 섞어 둔다: 마감 임박(40분) · 품절(SOLD_OUT) · 판매 종료(CLOSED) · 재고 2개 이하.
insert into products (store_id, name, category, initial_qty, available_qty, held_qty,
                      original_price, sale_price, discount_rate,
                      pickup_start_at, pickup_end_at, photo_url, status, created_at, updated_at)
select s.id, v.name, v.category, v.initial_qty, v.available_qty, 0,
       v.original_price, v.sale_price,
       round((v.original_price - v.sale_price) * 100.0 / v.original_price)::smallint,
       local_now.ts + v.starts_in, local_now.ts + v.ends_in,
       'https://picsum.photos/seed/' || v.photo || '/400/300', v.status, now(), now()
from (values
  -- 수경야채 (0 m)
  ('seed-o1', '상추 300g',        'VEGETABLE', 5, 5,  4000,  2000, interval '-1 hour', interval '4 hours',  'lettuce',   'ON_SALE'),
  ('seed-o1', '양파 1.5kg',       'VEGETABLE', 4, 4,  8000,  4000, interval '-1 hour', interval '4 hours',  'onion',     'ON_SALE'),
  ('seed-o1', '감자 1kg',         'VEGETABLE', 5, 5,  6000,  3000, interval '-1 hour', interval '4 hours',  'potato',    'ON_SALE'),
  ('seed-o1', '애호박 2개',       'VEGETABLE', 2, 2,  3000,  1500, interval '-2 hours', interval '40 minutes', 'zucchini', 'ON_SALE'),
  ('seed-o1', '오이 5입',         'VEGETABLE', 3, 0,  5000,  2500, interval '-1 hour', interval '4 hours',  'cucumber',  'SOLD_OUT'),
  -- 역삼 정육 (701 m, 일요일 휴무)
  ('seed-o2', '삼겹살 500g',      'MEAT',      2, 2, 18000, 12000, interval '-1 hour', interval '3 hours',  'pork',      'ON_SALE'),
  ('seed-o2', '닭다리살 800g',    'MEAT',      3, 3, 14000,  8400, interval '-1 hour', interval '3 hours',  'chicken',   'ON_SALE'),
  ('seed-o2', '계란 한판',        'ETC',       7, 7,  9000,  5400, interval '-1 hour', interval '6 hours',  'egg',       'ON_SALE'),
  ('seed-o2', '수제 떡갈비 6쪽',  'SIDE_DISH', 4, 4, 12000,  6000, interval '-1 hour', interval '2 hours',  'patty',     'ON_SALE'),
  -- 역삼 청과 (701 m)
  ('seed-o3', '복숭아 4입',       'FRUIT',     3, 3, 10000,  4000, interval '-2 hours', interval '5 hours', 'peach',     'ON_SALE'),
  ('seed-o3', '방울토마토 500g',  'FRUIT',     4, 4,  8000,  4400, interval '-2 hours', interval '5 hours', 'tomato',    'ON_SALE'),
  ('seed-o3', '대파 1단',         'VEGETABLE', 2, 2,  5000,  3500, interval '-2 hours', interval '5 hours', 'leek',      'ON_SALE'),
  ('seed-o3', '알배기 배추 2통',  'VEGETABLE', 4, 4,  6000,  3000, interval '-2 hours', interval '5 hours', 'cabbage',   'ON_SALE'),
  ('seed-o3', '고구마 1.5kg',     'VEGETABLE', 6, 6, 12000,  7200, interval '-2 hours', interval '5 hours', 'sweetpotato','ON_SALE'),
  -- 도곡 반찬 (1.5 km, 월요일 휴무)
  ('seed-o4', '모둠 나물 3종',    'SIDE_DISH', 5, 5,  9000,  4500, interval '-3 hours', interval '2 hours', 'namul',     'ON_SALE'),
  ('seed-o4', '두부 조림 2인분',  'SIDE_DISH', 3, 3,  6000,  3000, interval '-3 hours', interval '2 hours', 'tofu',      'ON_SALE'),
  ('seed-o4', '간장 불고기 500g', 'MEAT',      2, 2, 15000,  9000, interval '-3 hours', interval '2 hours', 'bulgogi',   'ON_SALE'),
  -- 한강 수산 (3 km)
  ('seed-o5', '손질 새우 300g',   'SEAFOOD',   3, 3, 16000,  9600, interval '-1 hour', interval '4 hours',  'shrimp',    'ON_SALE'),
  ('seed-o5', '고등어 2손',       'SEAFOOD',   4, 4, 12000,  7200, interval '-1 hour', interval '4 hours',  'mackerel',  'ON_SALE'),
  ('seed-o5', '오징어 2마리',     'SEAFOOD',   2, 2, 10000,  5000, interval '-2 hours', interval '40 minutes', 'squid',  'ON_SALE'),
  -- 성수 베이커리 (6 km, 화요일 휴무)
  ('seed-o6', '식빵 1봉',         'ETC',       6, 6,  5000,  2500, interval '-1 hour', interval '3 hours',  'bread',     'ON_SALE'),
  ('seed-o6', '크루아상 4개',     'ETC',       3, 3, 12000,  6000, interval '-1 hour', interval '3 hours',  'croissant', 'ON_SALE'),
  ('seed-o6', '우유 1L',          'ETC',       5, 5,  3000,  1800, interval '-6 hours', interval '-1 hour', 'milk',     'CLOSED'),
  -- 신사 반찬 (234 m, 승인 대기 -- 탐색에 뜨지 않는다)
  ('seed-o7', '모둠전 4종',       'SIDE_DISH', 3, 3, 14000,  7000, interval '-1 hour', interval '3 hours',  'jeon',      'ON_SALE'),
  ('seed-o7', '잡채 500g',        'SIDE_DISH', 2, 2, 10000,  5000, interval '-1 hour', interval '3 hours',  'japchae',   'ON_SALE')
) as v(oauth_id, name, category, initial_qty, available_qty, original_price, sale_price,
       starts_in, ends_in, photo, status)
join users u on u.oauth_provider = 'seed' and u.oauth_provider_id = v.oauth_id
join stores s on s.owner_user_id = u.id
cross join (select (now() at time zone 'Asia/Seoul') as ts) local_now;

-- 재료 태그. 레시피 추천(FR-004-01)이 이 id 로 조인한다 -- 자유 텍스트를 맞춰 보지 않는다.
-- init_data_1.sql 을 아직 안 넣었으면 사전이 비어 있어 아무 행도 안 생긴다(오류 아님).
insert into product_ingredients (product_id, ingredient_id)
select p.id, i.id
from (values
  ('상추 300g', '상추'), ('양파 1.5kg', '양파'), ('감자 1kg', '감자'),
  ('애호박 2개', '애호박'), ('오이 5입', '오이'),
  ('삼겹살 500g', '돼지고기'), ('닭다리살 800g', '닭고기'), ('계란 한판', '계란'),
  ('복숭아 4입', '복숭아'), ('방울토마토 500g', '토마토'), ('대파 1단', '대파'),
  ('알배기 배추 2통', '배추'), ('고구마 1.5kg', '고구마'),
  ('모둠 나물 3종', '시금치'), ('두부 조림 2인분', '두부'), ('간장 불고기 500g', '돼지고기'),
  ('손질 새우 300g', '새우')
) as v(product_name, ingredient_name)
join products p on p.name = v.product_name
join stores s on s.id = p.store_id
join users u on u.id = s.owner_user_id and u.oauth_provider = 'seed'
join ingredients i on i.norm_key = v.ingredient_name
on conflict do nothing;

commit;
