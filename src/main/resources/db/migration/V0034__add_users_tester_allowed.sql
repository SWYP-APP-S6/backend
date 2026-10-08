-- 관리자가 테스트를 허가한 계정. 테스트 모드(tester)는 허가받은 사람이 앱 마이페이지에서 직접 켜고 끈다 --
-- 팀원이 실제 가게와 테스트 가게를 오가며 확인할 수 있게, 허가와 현재 모드를 나눠 둔다.
alter table users
    add column tester_allowed boolean not null default false;

-- 이미 테스트 모드인 계정(/dev/test-token 계정 등)은 허가도 받은 것으로 본다.
update users
set tester_allowed = true
where tester;

alter table users
    add constraint users_tester_requires_allowance_check check (not tester or tester_allowed);
