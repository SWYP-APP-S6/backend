-- ============================================================
-- notifications.push_state (푸시 아웃박스)
-- ============================================================
-- 알림 행을 만드는 쪽(HoldService·OwnerHoldService·HoldExpirer)은 전부 트랜잭션 안이고, 그중
-- 일부는 products 에 FOR UPDATE 를 쥐고 있다. 거기서 FCM 을 직접 호출하면 외부 HTTP 왕복(최대
-- spring.http.clients.read-timeout) 동안 상품 행이 잠긴 채로 남아 같은 상품의 찜이 전부 밀린다.
-- 그래서 발송을 이 컬럼으로 미룬다 -- 행은 커밋 뒤에만 보이므로 롤백된 트랜잭션의 푸시가 나갈 수
-- 없고, 재시작해도 PENDING 이 남아 유실되지 않는다.
--
-- SENT    = 기기 하나 이상이 받았다
-- SKIPPED = 보낼 이유가 없었다(등록된 기기 없음 / 너무 오래된 알림 / FCM 미설정)
-- FAILED  = 보내려 했지만 포기했다(FCM 이 거부 / 재시도 횟수 소진)
alter table notifications
    add column push_state    varchar(20) not null default 'PENDING'
        check (push_state in ('PENDING', 'SENT', 'SKIPPED', 'FAILED')),
    add column push_attempts integer     not null default 0 check (push_attempts >= 0),
    add column pushed_at     timestamptz;

-- 이 마이그레이션 이전의 알림은 푸시 대상이 아니다. PENDING 으로 두면 배포 직후 배치가 과거
-- 알림을 전부 한 번씩 쏘려 든다(오래된 것은 어차피 건너뛰지만, 굳이 훑을 이유가 없다).
update notifications
set push_state = 'SKIPPED'
where push_state = 'PENDING';

-- 배치의 유일한 질의 모양: PENDING 을 오래된 순으로 batch-size 만큼. 부분 인덱스라 SENT 가
-- 쌓여도 인덱스는 "아직 안 보낸 것"의 크기로만 남는다.
create index idx_notifications_push_pending on notifications (id) where push_state = 'PENDING';
