-- ============================================================
-- holds.expiry_reminded_at
-- ============================================================
-- "찜 시간이 곧 끝나요"(N-02)는 만료 전에 한 번만 나가야 한다. 배치가 hold.expiry-reminder-lead
-- 안에 드는 HOLDING 을 훑는데, 스캔 주기가 lead 보다 짧으므로 표시를 남기지 않으면 같은 찜이
-- 매 주기마다 다시 알림을 받는다.
alter table holds
    add column expiry_reminded_at timestamptz;

-- 배치의 질의 모양: 아직 안 알린 HOLDING 중 곧 만료되는 것. 부분 인덱스라 이미 알린 찜과
-- 끝난 찜은 인덱스에서 빠진다.
create index idx_holds_expiry_reminder on holds (expires_at)
    where status = 'HOLDING' and expiry_reminded_at is null;
