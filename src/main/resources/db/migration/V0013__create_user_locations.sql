-- 사용자가 직접 고른 기본 동네. **위치 이력이 아니다** — 탐색은 조회 시점의 GPS 좌표로 하고
-- 그 좌표는 저장하지 않는다(NFR-SEC-002). 이 테이블은 위치 권한을 거부했을 때 탐색 기준으로
-- 쓸 fallback 하나만 담는다(FR-001-01 예외 흐름).
--
-- 유저당 한 곳이라 user_id 를 그대로 PK 로 둔다. 집/직장처럼 여러 곳이 필요해지면 그때
-- 대리키를 추가하고 (user_id, label) 유니크로 넓힌다.
create table user_locations (
    user_id     bigint        primary key,
    -- 행정동/법정동 코드. 지금은 채우지 않는다 — 지도는 네이버로 정해졌는데 NCP Geocoding 응답에
    -- 코드가 없다(addressElements 의 code 가 빈 값으로 온다). 지역 판정은 아래 좌표로 하고,
    -- 코드 체계가 실제로 필요해지면 어느 쪽(HCODE/BCODE)을 쓸지 정한 뒤 채운다.
    region_code varchar(10),
    region_name varchar(100)  not null,
    latitude    numeric(9, 6) not null,
    longitude   numeric(9, 6) not null,
    created_at  timestamptz   not null,
    updated_at  timestamptz   not null,
    -- 유저가 지워지면 위치도 함께 사라져야 한다(개인정보).
    constraint fk_user_locations_user foreign key (user_id) references users (id) on delete cascade
);
