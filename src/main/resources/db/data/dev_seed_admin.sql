-- 개발용 SUPER 관리자. **로컬 전용 — 운영에 절대 넣지 말 것.**
--
-- V0001 이 심던 계정을 V0012 가 제거했다. 비밀번호가 공개 저장소에 적혀 있어 스키마의 일부로
-- 둘 수 없기 때문이다. 로컬에서 백오피스를 만져야 할 때만 이 파일을 직접 넣는다.
--
--   psql "postgresql://swyp:swyp@localhost:5432/swyp" -v ON_ERROR_STOP=1 -f \
--     src/main/resources/db/data/dev_seed_admin.sql
--
-- 계정: admin@swyp.com / swyp-admin-1234
insert into admins (email, name, type, password, created_at, updated_at)
values ('admin@swyp.com', 'Super Admin', 'SUPER',
        '$2a$10$d/Uurstls9aEwNeY19bwL.2gNOA1MAPPse7.pHjw.P8sRvQyltPPm', now(), now())
on conflict do nothing;
