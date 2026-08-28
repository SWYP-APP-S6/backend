-- 개발용 더미 가게. **로컬 전용 — 운영에 넣지 말 것.**
-- **dev_seed_users.sql 을 먼저 넣어야 한다** — 점주를 닉네임으로 찾고, 그 스크립트가 dev 가게를
-- 함께 지우기 때문이다.
--
-- 입점 승인 화면을 확인하려면 PENDING 이 있어야 하므로 상태를 섞어 둔다.
--
--   psql "postgresql://swyp:swyp@localhost:5432/swyp" -v ON_ERROR_STOP=1 -f \
--     src/main/resources/db/data/dev_seed_stores.sql
delete from stores where owner_user_id in (select id from users where nickname like 'dev\_%');

insert into stores (owner_user_id, name, address, address_detail, phone, latitude, longitude,
                    business_open_time, business_close_time, status,
                    business_registration_number, application_note, created_at, updated_at)
select u.id, v.name, v.address, v.address_detail, v.phone, v.latitude, v.longitude,
       v.open_time, v.close_time, v.status, v.brn, v.note, now() - v.age, now()
from (values
  ('dev_청과왕',   '역삼 청과',     '서울특별시 강남구 역삼로 123',   '1층',   '0212341234',
   37.500600, 127.036500, time '09:00', time '21:00', 'APPROVED', '123-45-67890',
   '역삼동에서 15년째 청과를 하고 있습니다. 매일 새벽 가락시장에서 직접 떼어 오는데, 그날 못 판 물건은 다음 날이면 상품성이 떨어져 버리는 일이 많습니다.
주로 사과, 배, 감귤 같은 제철 과일과 대파, 양파, 감자 등 기본 채소를 취급합니다. 폐기 직전 물건을 반값에라도 넘길 수 있으면 손해를 줄일 수 있을 것 같아 신청합니다.
평일에는 오후 7시 이후, 주말에는 오후 5시 이후에 마감 물량이 나옵니다.', interval '25 days'),
  ('dev_정육점',   '성수 정육점',   '서울특별시 성동구 아차산로 45',  null,    '0223452345',
   37.544600, 127.055900, time '10:00', time '20:00', 'PENDING', '234-56-78901',
   '성수동 골목에서 정육점을 운영합니다. 국내산 돼지고기와 소고기를 주로 다루고, 수제 떡갈비와 양념육도 매장에서 직접 만듭니다.
당일 소분해 둔 고기는 다음 날 판매하지 않는 것이 원칙이라 마감 무렵 남는 물량이 늘 생깁니다. 이걸 폐기하는 대신 저녁에 필요한 분들께 할인가로 드리고 싶습니다.
평일 저녁 8시 마감 기준으로 하루 5~10팩 정도 나올 것으로 예상합니다.', interval '3 days'),
  ('dev_반찬가게', '천호 반찬드림', '서울특별시 강동구 천호대로 77',  '지하1층', '0234563456',
   37.538600, 127.123700, time '08:30', time '19:30', 'PENDING', '345-67-89012',
   '천호역 근처 지하상가에서 반찬가게를 합니다. 매일 아침 조리해서 당일에만 판매하는 원칙으로 운영 중입니다.
나물류, 조림, 전, 김치류를 20여 가지 만들고 있고, 저녁 7시가 지나면 남은 반찬은 전부 폐기하고 있습니다. 양이 적지 않아 늘 아깝다고 생각했습니다.
1인 가구가 많은 동네라 소량으로 저렴하게 가져가실 분들이 있을 것 같습니다.', interval '1 day')
) as v(nickname, name, address, address_detail, phone, latitude, longitude,
       open_time, close_time, status, brn, note, age)
join users u on u.nickname = v.nickname;
