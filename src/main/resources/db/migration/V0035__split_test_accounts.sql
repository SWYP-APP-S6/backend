-- 테스트 모드를 같은 카카오 계정의 별도 행(tester = true)으로 나눈다. 찜·알림·취소권·기기 토큰이 모두
-- user_id 에 붙어 있으므로 행이 갈리면 데이터도 통째로 갈린다. 행의 tester 는 만들 때 정해지고 바뀌지 않는다 --
-- 가게·상품은 주인 행의 tester 를 따라가므로, 그 값이 바뀌면 테스트 가게가 실사용자에게 드러난다.
alter table users
    drop constraint users_tester_requires_allowance_check;

-- 허가와 "로그인하면 테스트 계정으로 들어간다"(test_mode)는 실제 행에만 둔다.
update users
set tester_allowed = false
where tester;

alter table users
    add column test_mode boolean not null default false;

alter table users
    add constraint users_test_account_has_no_permission_check check (not (tester and tester_allowed)),
    add constraint users_test_mode_requires_allowance_check check (not test_mode or tester_allowed);

-- 한 카카오 계정·역할에 실제 행과 테스트 행이 하나씩 있을 수 있다.
drop index uq_users_oauth_identity;
create unique index uq_users_oauth_identity on users (oauth_provider, oauth_provider_id, role, tester)
    where oauth_provider_id is not null;

create function forbid_users_tester_change() returns trigger
    language plpgsql as
$$
begin
    raise exception 'users.tester is fixed when the row is created (user %)', old.id;
end;
$$;

create trigger users_tester_immutable
    before update of tester
    on users
    for each row
    when (old.tester is distinct from new.tester)
execute function forbid_users_tester_change();
