-- 홈 목록의 카테고리 칩이 여덟이다(전체·채소·과일·육류·수산·유제품·베이커리·반찬·기타).
-- products.category 는 여섯만 받고 있어 유제품·베이커리를 고를 수 없었다.
--
-- 가게 종류(store_categories, V0016)는 이미 여덟을 받는다. 다만 '반찬'을 가게는 PREPARED_FOOD,
-- 상품은 SIDE_DISH 로 부른다 -- 같은 말이 두 이름으로 남아 있는 건 맞지만, 한쪽을 바꾸면 이미
-- 쌓인 행을 손봐야 하고 얻는 것이 이름뿐이라 그대로 둔다.
alter table products
    drop constraint products_category_check;

alter table products
    add constraint products_category_check
        check (category in ('VEGETABLE', 'FRUIT', 'MEAT', 'SEAFOOD',
                            'DAIRY_EGG', 'BAKERY', 'SIDE_DISH', 'ETC'));
