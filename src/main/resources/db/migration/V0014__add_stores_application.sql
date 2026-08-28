-- 입점 신청서. stores.status = PENDING 을 만드는 흐름(점주의 입점 신청)이 아직 없어서
-- 심사할 근거가 비어 있었다 — 관리자가 무엇을 보고 승인/반려하는지가 없었다.
--
-- 기존 행이 이미 있으므로 nullable 로 추가한다. 점주 신청 화면이 붙어 모든 가게가 신청서를
-- 거쳐 들어오게 되면 그때 not null 로 좁힌다(expand -> contract).
alter table stores
    add column business_registration_number varchar(20),
    add column application_note             text;

comment on column stores.business_registration_number is '사업자등록번호. 승인 심사의 근거';
comment on column stores.application_note is '입점 신청서 본문(취급 품목·입점 사유 등 자유 서술)';
