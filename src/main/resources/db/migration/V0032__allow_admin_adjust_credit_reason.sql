-- 문의 대응 중 취소권을 손으로 되돌려 줄 길이 없었다. 관리자 보정도 같은 원장에 남겨야
-- "왜 늘었나"를 나중에 되짚을 수 있다. 위의 네 사유는 앱이 스스로 만드는 것이고, 이것만 사람이 만든다.
alter table hold_cancel_credit_events
    drop constraint hold_cancel_credit_events_reason_check;
alter table hold_cancel_credit_events
    add constraint hold_cancel_credit_events_reason_check
        check (reason in ('CANCEL', 'NO_SHOW', 'REFILL', 'GIVE_BACK', 'ADMIN_ADJUST'));
