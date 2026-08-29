-- 개발용 더미 유저. **로컬 전용 — 운영에 넣지 말 것.**
--
-- 앱이 아직 가입을 붙이지 않아 users 가 비어 있다. 관리자 화면(유저 목록)을 확인하려면
-- 볼 데이터가 있어야 하므로 가짜 유저를 넣는다. 실존 인물이 아니고 번호도 유효하지 않다.
--
--   psql "postgresql://swyp:swyp@localhost:5432/swyp" -v ON_ERROR_STOP=1 -f \
--     src/main/resources/db/data/dev_seed_users.sql
--
-- 지우려면: delete from users where nickname like 'dev_%';
--
-- 재실행해도 중복되지 않도록 먼저 지우고 넣는다. `on conflict do nothing` 에 기댈 수 없다 —
-- users 의 유니크 인덱스는 (oauth_provider, oauth_provider_id) 이고 `oauth_provider_id is
-- not null` 조건이 붙어 있어서, 카카오 미연동 더미는 아예 인덱스 대상이 아니다.
-- stores 가 users 를 참조하므로 가게부터 지운다(FK 에 cascade 가 없다).
-- user_locations 는 on delete cascade 로 함께 사라진다.
delete from stores where owner_user_id in (select id from users where nickname like 'dev\_%');
delete from users where nickname like 'dev\_%';

insert into users (role, oauth_provider, oauth_provider_id, nickname, phone,
                   marketing_opt_in, terms_agreed_at, created_at, updated_at)
values
  ('CONSUMER', 'kakao', 'dev-1001', 'dev_한소영', '01011112222', true,  now() - interval '20 days', now() - interval '20 days', now()),
  ('CONSUMER', 'kakao', 'dev-1002', 'dev_박지훈', '01022223333', false, now() - interval '18 days', now() - interval '18 days', now()),
  ('CONSUMER', 'kakao', 'dev-1003', 'dev_이서연', null,          true,  now() - interval '15 days', now() - interval '15 days', now()),
  ('CONSUMER', 'kakao', 'dev-1004', 'dev_최민준', '01044445555', false, now() - interval '12 days', now() - interval '12 days', now()),
  ('CONSUMER', 'kakao', 'dev-1005', 'dev_정예린', '01055556666', true,  now() - interval '9 days',  now() - interval '9 days',  now()),
  ('CONSUMER', 'kakao', 'dev-1006', 'dev_강도현', '01066667777', false, now() - interval '5 days',  now() - interval '5 days',  now()),
  ('CONSUMER', null,    null,       'dev_윤하늘', '01077778888', true,  now() - interval '2 days',  now() - interval '2 days',  now()),
  ('OWNER',    'kakao', 'dev-2001', 'dev_청과왕', '01088889999', false, now() - interval '25 days', now() - interval '25 days', now()),
  ('OWNER',    'kakao', 'dev-2002', 'dev_정육점', '01099990000', true,  now() - interval '17 days', now() - interval '17 days', now()),
  ('OWNER',    null,    null,       'dev_반찬가게', '01000001111', false, now() - interval '4 days',  now() - interval '4 days',  now());

-- 기본 동네. 실제 서비스에서는 위치 권한을 거부한 사용자의 탐색 기준으로 쓰인다.
-- (좌표는 서울 일부 행정동의 대략값이며 정확도를 보장하지 않는다.)
-- region_code 는 비워 둔다(V0013 주석 참고 — 네이버 Geocoding 이 코드를 주지 않는다).
insert into user_locations (user_id, region_name, latitude, longitude, created_at, updated_at)
select u.id, v.region_name, v.latitude, v.longitude, now(), now()
from (values
  ('dev_한소영',   '서울특별시 강남구 역삼동',     37.500600, 127.036500),
  ('dev_박지훈',   '서울특별시 강동구 천호동',     37.538600, 127.123700),
  ('dev_최민준',   '서울특별시 성동구 성수동',     37.544600, 127.055900),
  ('dev_정예린',   '서울특별시 영등포구 여의도동', 37.521600, 126.924200),
  ('dev_청과왕',   '서울특별시 강남구 역삼동',     37.500600, 127.036500),
  ('dev_반찬가게', '서울특별시 성동구 성수동',     37.544600, 127.055900)
) as v(nickname, region_name, latitude, longitude)
join users u on u.nickname = v.nickname;
