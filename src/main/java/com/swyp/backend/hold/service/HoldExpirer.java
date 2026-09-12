package com.swyp.backend.hold.service;

import com.swyp.backend.hold.entity.Hold;
import com.swyp.backend.hold.entity.HoldItem;
import com.swyp.backend.hold.entity.HoldStatus;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.notification.entity.NotificationType;
import com.swyp.backend.notification.function.NotificationFunction;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.function.ProductFunction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class HoldExpirer {

	private final HoldFunction holdFunction;
	private final ProductFunction productFunction;
	private final NotificationFunction notificationFunction;

	@Transactional
	public boolean expire(Long holdId, List<Long> productIds) {
		Map<Long, Product> locked = new LinkedHashMap<>();
		productIds.stream().distinct().sorted()
				.forEach(id -> locked.put(id, productFunction.getByIdForUpdate(id)));

		Hold hold = holdFunction.getByIdForUpdate(holdId);
		if (hold.getStatus() != HoldStatus.HOLDING) {
			return false;
		}
		hold.expire();
		for (HoldItem item : hold.getItems()) {
			locked.get(item.getProduct().getId()).releaseHold(item.getQty());
		}
		notificationFunction.notify(
				hold.getStore().getOwner(),
				NotificationType.HOLD_UNCONFIRMED,
				"수령 확인이 안 된 찜이 있어요",
				hold.getUser().getNickname() + "님의 찜 시간이 지났어요. 이미 수령했다면 수령 완료를 눌러주세요.",
				null);
		return true;
	}
}
