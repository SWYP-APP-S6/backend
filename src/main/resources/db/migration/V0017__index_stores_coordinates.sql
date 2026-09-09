-- 지도 탭이 뷰포트(bbox)로 매장을 찾는다. 그 전까지 stores 를 좌표로 읽는 쿼리가 없어서
-- 인덱스도 없었고, 지금은 지도를 팬할 때마다 seq scan 이 된다.
--
-- 부분 인덱스인 이유: 조회는 승인된 가게만 본다. 심사 대기·반려 행은 색인에서 빠져
-- 인덱스가 작게 유지되고, 승인 상태가 바뀌는 빈도도 낮다.
create index idx_stores_approved_coordinates on stores (latitude, longitude)
    where status = 'APPROVED';
