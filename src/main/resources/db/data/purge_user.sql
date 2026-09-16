-- 유저 한 명과 그 유저에 딸린 행을 전부 지운다. 회원 탈퇴 기능이 생기기 전까지 쓰는 임시 도구다.
--
-- 기본은 dry-run 이다 -- 지울 행 수만 보여 주고 롤백한다. 확인한 뒤 commit=1 을 붙여 다시 돌린다.
--
--   docker exec -i backend-postgres-1 psql -U swyp -d swyp -v ON_ERROR_STOP=1 -v uid=123 < purge_user.sql
--   docker exec -i backend-postgres-1 psql -U swyp -d swyp -v ON_ERROR_STOP=1 -v uid=123 -v commit=1 < purge_user.sql
--
-- 점주라면 그 가게 · 상품 · 가게에 걸린 찜(다른 소비자의 찜 포함)까지 함께 지워진다.
-- store_categories / store_business_days / product_ingredients / user_locations 는 on delete cascade 다.

\if :{?uid}
\else
	\echo 'usage: -v uid=<users.id> [-v commit=1]'
	\quit
\endif

begin;

select set_config('purge.uid', :'uid', true);
select id, role, nickname, oauth_provider, created_at from users where id = :uid;

do $$
begin
	if not exists (select 1 from users where id = current_setting('purge.uid')::bigint) then
		raise exception 'users.id = % 가 없다', current_setting('purge.uid');
	end if;
end $$;

create temp table purge_stores on commit drop as
	select id from stores where owner_user_id = :uid;
create temp table purge_products on commit drop as
	select id from products where store_id in (select id from purge_stores);
create temp table purge_holds on commit drop as
	select id from holds
	where user_id = :uid
	   or store_id in (select id from purge_stores)
	   or product_id in (select id from purge_products);

select
	(select count(*) from purge_stores)   as stores,
	(select count(*) from purge_products) as products,
	(select count(*) from purge_holds)    as holds;

delete from hold_cancel_credit_events
 where user_id = :uid or hold_id in (select id from purge_holds);
delete from domain_events
 where user_id = :uid
    or store_id   in (select id from purge_stores)
    or product_id in (select id from purge_products)
    or hold_id    in (select id from purge_holds);
delete from holds    where id in (select id from purge_holds);
delete from products where id in (select id from purge_products);
delete from stores   where id in (select id from purge_stores);

delete from notifications         where user_id = :uid;
delete from user_device_tokens    where user_id = :uid;
delete from recipe_feedback       where user_id = :uid;
delete from user_terms_agreements where user_id = :uid;
delete from hold_cancel_credits   where user_id = :uid;
delete from users                 where id = :uid;

\if :{?commit}
	commit;
	\echo '==> committed'
\else
	rollback;
	\echo '==> dry-run: rolled back. 확인했으면 -v commit=1 을 붙여 다시 실행한다.'
\endif
