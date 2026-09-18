package com.swyp.backend.product.service;

import com.swyp.backend.product.function.ProductFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductCloseService {

	private final ProductFunction productFunction;

	@Scheduled(
			fixedDelayString = "${product.close-scan-interval}",
			initialDelayString = "${product.close-scan-interval}")
	@Transactional
	public int closeEndedProducts() {
		int closed = productFunction.closeEndedProducts();
		if (closed > 0) {
			log.info("Closed {} products past their pickup end", closed);
		}
		return closed;
	}
}
