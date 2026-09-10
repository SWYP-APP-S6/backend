---
paths:
  - "src/main/java/**/*Function.java"
---

# function 작성 규칙

- 클래스: `@Component @RequiredArgsConstructor` (생성자 주입만, 필드 주입 금지).
- **자기 feature의 repository만 주입한다.** 다른 feature의 repository·function·service는 주입하지
  않는다 — function은 의존 그래프의 **잎**이어야 순환이 구조적으로 불가능해진다.
- **repository를 부르는 유일한 계층**이다. service·controller는 repository를 직접 보지 않는다.
- **"찾거나 예외"를 여기서 끝낸다**: `get{Entity}ById(id)` → 없으면
  `throw new BusinessException({Feature}ErrorCode.{X}_NOT_FOUND)`. 호출자가 `Optional` 을 다시
  풀게 하지 않는다. "없을 수도 있음"이 정상 흐름이면 `find...`로 `Optional`을 그대로 반환한다.
- 어느 쿼리를 쓸지 고르는 분기(`status == null ? findAll : findByStatus`)는 여기 둔다 — 데이터
  접근 관심사다.
- **의도 단위 파라미터를 받는다.** 호출자가 쿼리 준비물을 조립하게 하지 않는다 — bounding box,
  `LocalDateTime.now(clock)` 같은 기준 시각, 페이지 계산은 전부 여기서 만든다.
  `findSellableNear(lat, lng, radiusMeters, category)`이지 `findSellableWithinBounds(now, category,
  minLat, maxLat, minLng, maxLng)`이 아니다. 이 차이가 service를 "무엇을 하는가"로 유지한다.
- **DB가 대신 못 해주는 조회 연산도 여기서 끝낸다.** 반경 안인지 거르는 haversine이 지금 자바에
  있는 건 PostGIS를 아직 안 써서지, 그게 데이터 접근이 아니어서가 아니다. 확장을 도입하면 이
  계산은 SQL로 내려가고 **바뀌는 건 이 계층뿐**이어야 한다.
- **`@Transactional`을 붙이지 않는다.** 트랜잭션 경계는 service가 연다.
- **엔티티, 또는 엔티티 위의 읽기 모델을 반환한다. 응답 DTO는 만들지 않는다.**
  - 읽기 모델 = 조회 결과를 담기 위한 `record`. 리포지토리 프로젝션(`StoreProductSummary`)이나
    엔티티 묶음(`SellableStoreGroup(store, products, distanceMeters)`)이 여기 해당한다.
  - 응답 DTO = 컨트롤러가 내보내는 모양(`...Response`). 이건 service가 만든다 — function이 만들면
    그 function을 쓰는 **모든 feature가 남의 API 응답 형태에 묶인다.**
  - 판단 기준: **필드를 하나 추가할 때 클라이언트 계약이 바뀌면 응답 DTO다.**
- 두 feature를 조합하지 않는다. 그건 service가 두 개의 function을 주입해서 한다.
- 레퍼런스: `com.swyp.backend.store.function.StoreFunction`.
