-- ============================================================
-- user_device_tokens
-- ============================================================
-- `notifications`가 "인앱 알림함에 뭘 보여줄지"라면, 이 테이블은 실제 FCM 푸시를 쏘기 위한
-- 수신처다. 토큰 하나는 한 시점에 한 기기를 가리키므로(재설치·재로그인 시 소유자가 바뀔 수 있음)
-- fcm_token을 유니크로 두고 upsert로 소유자를 갱신한다. last_used_at은 만료 토큰 정리용.
create table user_device_tokens (
    id            bigint       generated always as identity primary key,
    user_id       bigint       not null,
    platform      varchar(10)  not null check (platform in ('ANDROID', 'IOS')),
    fcm_token     varchar(255) not null,
    last_used_at  timestamptz  not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    constraint uq_user_device_tokens_token unique (fcm_token),
    constraint fk_udt_user foreign key (user_id) references users (id)
);

create index idx_udt_user on user_device_tokens (user_id);
