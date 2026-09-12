-- 취소·노쇼를 "최근 24시간 몇 건"으로 세던 것을 사용자별 잔여 횟수(토큰 버킷)로 바꾼다.
-- 상한이 없으면 오래 쓴 계정이 취소권을 무한히 쌓게 되므로 최대치를 두고, 하루에 하나씩 충전한다.
create table hold_cancel_credits
(
    id          bigint generated always as identity primary key,
    user_id     bigint      not null,
    credits     integer     not null check (credits >= 0),
    refilled_at timestamptz not null,
    created_at  timestamptz not null,
    updated_at  timestamptz not null,
    constraint fk_hold_cancel_credits_user foreign key (user_id) references users (id),
    constraint uq_hold_cancel_credits_user unique (user_id)
);

-- 노쇼는 만료 즉시가 아니라 유예가 지난 뒤에 차감된다. 어떤 찜이 이미 차감됐는지 표시해 두면
-- 두 번 깎이지 않고, 점주가 늦게 수령 완료를 누를 때 돌려줄 대상도 이 컬럼으로 알 수 있다.
alter table holds
    add column no_show_charged_at timestamptz;

create index idx_holds_no_show_pending on holds (user_id, expires_at)
    where status = 'EXPIRED' and completed_at is null and no_show_charged_at is null;
