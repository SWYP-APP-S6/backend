-- 찜이 최초 등록 수량의 60% 에 닿으면 점주에게 "지금 판매 가능한 수량이 N개가 맞나요?" 를 묻는다
-- (reconfirm_sent_at). 거기서 "네, 맞아요" 를 고른 시각이 여기 남고, 그 뒤로는 픽업 마감까지
-- 수량을 고칠 수 없다 -- 손님이 이미 그 수량을 믿고 오는 중이기 때문이다.
--
-- reconfirm_answered_at 과 따로 둔다. "아니요" 도 응답이라 그 컬럼만으로는 잠글지 말지를
-- 가릴 수 없다.
alter table products
    add column stock_confirmed_at timestamptz;
