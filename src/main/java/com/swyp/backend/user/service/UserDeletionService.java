package com.swyp.backend.user.service;

import com.swyp.backend.analytics.function.DomainEventFunction;
import com.swyp.backend.common.exception.BusinessException;
import com.swyp.backend.hold.function.HoldCancelCreditFunction;
import com.swyp.backend.hold.function.HoldFunction;
import com.swyp.backend.notification.function.DeviceTokenFunction;
import com.swyp.backend.notification.function.NotificationFunction;
import com.swyp.backend.product.function.ProductFunction;
import com.swyp.backend.recipe.function.RecipeFunction;
import com.swyp.backend.store.function.StoreFunction;
import com.swyp.backend.terms.function.TermsFunction;
import com.swyp.backend.user.entity.User;
import com.swyp.backend.user.exception.UserAuthErrorCode;
import com.swyp.backend.user.function.UserFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDeletionService {

	private final UserFunction userFunction;
	private final HoldFunction holdFunction;
	private final HoldCancelCreditFunction holdCancelCreditFunction;
	private final ProductFunction productFunction;
	private final StoreFunction storeFunction;
	private final NotificationFunction notificationFunction;
	private final DeviceTokenFunction deviceTokenFunction;
	private final RecipeFunction recipeFunction;
	private final TermsFunction termsFunction;
	private final DomainEventFunction domainEventFunction;

	@Transactional
	public void delete(Long userId) {
		User user = userFunction.getByIdForUpdate(userId);
		if (holdFunction.hasHoldingOf(userId)) {
			throw new BusinessException(UserAuthErrorCode.HOLDING_HOLDS_REMAIN);
		}
		if (holdFunction.hasHoldingAtStoreOwnedBy(userId)) {
			throw new BusinessException(UserAuthErrorCode.STORE_HOLDING_HOLDS_REMAIN);
		}

		domainEventFunction.deleteAllInvolving(userId);
		holdCancelCreditFunction.deleteAllInvolving(userId);
		holdFunction.deleteAllInvolving(userId);
		productFunction.deleteAllOfStoreOwnedBy(userId);
		storeFunction.deleteOwnedBy(userId);
		notificationFunction.deleteAllOf(userId);
		deviceTokenFunction.deleteAllOf(userId);
		recipeFunction.deleteFeedbackOf(userId);
		termsFunction.deleteAgreementsOf(userId);
		userFunction.delete(user);
	}
}
