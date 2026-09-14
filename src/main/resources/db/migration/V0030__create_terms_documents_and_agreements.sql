-- ============================================================
-- terms_documents / user_terms_agreements
-- ============================================================
-- 가입 동의는 지금까지 users.terms_agreed_at 한 칸이었다. "언제 동의했나"는 남지만 "어느 문서의
-- 어느 판에 동의했나"는 남지 않는다. 약관이 개정되면 기존 회원이 동의한 것은 이전 판이므로, 문서를
-- 판(version) 단위 행으로 두고 동의는 그 행을 가리킨다.
--
-- 누군가 동의한 판의 본문은 고치지 않는다 — 동의한 내용이 사후에 바뀌기 때문이다. 개정은 version 을
-- 올린 새 행으로 넣고, 앱에는 (role, type) 별 가장 높은 version 이 보인다. 본문은 스키마가 아니라
-- 운영 데이터라 db/data/terms_data_1.sql 로 넣는다.
create table terms_documents (
    id             bigint generated always as identity primary key,
    role           varchar(20)  not null check (role in ('CONSUMER', 'OWNER')),
    type           varchar(30)  not null check (type in (
                       'SERVICE', 'PRIVACY_COLLECTION', 'LOCATION', 'THIRD_PARTY', 'MARKETING', 'PRIVACY_POLICY')),
    version        integer      not null check (version >= 1),
    title          varchar(100) not null,
    -- NOTICE = 동의받지 않고 열람만 시키는 문서(개인정보처리방침).
    requirement    varchar(20)  not null check (requirement in ('REQUIRED', 'OPTIONAL', 'NOTICE')),
    content        text         not null,
    -- 시행일은 날짜라 벽시계 date 로 둔다. 원문에 아직 정해지지 않아 비어 있을 수 있다.
    effective_date date,
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    constraint uq_terms_documents_role_type_version unique (role, type, version)
);

-- 동의는 가입 순간 그 역할의 현재 판에 대해 남긴다. users.terms_agreed_at 은 지우지 않는다 —
-- 이 테이블 이전에 가입한 회원의 유일한 기록이고, 그들이 어느 판을 봤는지는 알 수 없어 소급해 만들지 않는다.
create table user_terms_agreements (
    id                bigint      generated always as identity primary key,
    user_id           bigint      not null,
    terms_document_id bigint      not null,
    agreed_at         timestamptz not null,
    created_at        timestamptz not null,
    updated_at        timestamptz not null,
    constraint fk_uta_user foreign key (user_id) references users (id),
    constraint fk_uta_terms_document foreign key (terms_document_id) references terms_documents (id),
    constraint uq_user_terms_agreements_user_document unique (user_id, terms_document_id)
);
