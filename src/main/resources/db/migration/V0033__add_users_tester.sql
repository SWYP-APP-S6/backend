-- 팀원이 운영에서 테스트하는 계정을 표시한다. 테스터가 만든 가게·상품·찜은 따로 표시하지 않는다 --
-- 가게는 주인을, 상품은 가게를 따라가면 알 수 있으므로 같은 사실을 테이블마다 다시 적지 않는다.
-- 탐색 화면은 보는 사람과 가게 주인의 tester 가 같은 것만 보여준다.
alter table users
    add column tester boolean not null default false;

-- /dev/test-token 이 만든 계정은 처음부터 테스트용이다.
update users
set tester = true
where oauth_provider = 'dev';
