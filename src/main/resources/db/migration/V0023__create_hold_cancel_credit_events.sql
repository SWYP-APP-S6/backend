-- 취소권이 왜 줄었는지 아무 데도 남지 않았다. hold_cancel_credits 는 현재 잔액만 들고 있고,
-- 노쇼 차감만 holds.no_show_charged_at 으로 흔적이 남았을 뿐 사용자 취소는 기록이 없었다.
--
-- 그래서 "담고 취소했는데 2개가 줄었다"는 제보가 들어왔을 때 확인할 방법이 없었다. 실제로는
-- 지난 찜이 만료돼 밀려 있던 노쇼가 함께 정산된 것인데, 그 사실을 앱도 백오피스도 보여주지
-- 못한다 -- 차감이 방금 한 행동과 무관한 순간에 조용히 일어나기 때문이다.
create table hold_cancel_credit_events
(
    id         bigint      generated always as identity primary key,
    user_id    bigint      not null,
    -- 충전에는 해당 찜이 없다.
    hold_id    bigint,
    reason     varchar(20) not null
        check (reason in ('CANCEL', 'NO_SHOW', 'REFILL', 'GIVE_BACK')),
    -- 실제로 움직인 양이다. 잔액이 0인데 노쇼가 정산되면 깎이는 것이 없으므로 행 자체를 남기지
    -- 않는다 -- 0 을 적어 두면 "차감됐다"고 읽힌다.
    delta      smallint    not null check (delta <> 0),
    created_at timestamptz not null,
    constraint fk_hcce_user foreign key (user_id) references users (id),
    constraint fk_hcce_hold foreign key (hold_id) references holds (id)
);

-- 마이 화면과 문의 대응이 "이 사람의 최근 변동"을 읽는다.
create index idx_hcce_user on hold_cancel_credit_events (user_id, created_at desc);
-- 찜 내역이 "이 찜이 취소권을 썼나"를 찜 단위로 읽는다.
create index idx_hcce_hold on hold_cancel_credit_events (hold_id) where hold_id is not null;

-- 이미 차감된 노쇼는 기록이 남아 있으므로 그대로 옮긴다. 사용자 취소는 옮기지 않는다 --
-- "취소 시각 - 생성 시각 > 오조작 유예" 로 되짚을 수는 있지만 그건 추정이고, 추정을 사실처럼
-- 적어 두면 나중에 유예 값이 바뀔 때 과거 기록의 뜻이 함께 흔들린다.
insert into hold_cancel_credit_events (user_id, hold_id, reason, delta, created_at)
select user_id, id, 'NO_SHOW', -1, no_show_charged_at
from holds
where no_show_charged_at is not null;
