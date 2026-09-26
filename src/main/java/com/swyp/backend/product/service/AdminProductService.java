package com.swyp.backend.product.service;

import com.swyp.backend.common.response.PageResponse;
import com.swyp.backend.product.dto.AdminProductFilter;
import com.swyp.backend.product.dto.AdminProductResponse;
import com.swyp.backend.product.entity.Product;
import com.swyp.backend.product.function.ProductFunction;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProductService {

	private final ProductFunction productFunction;
	private final Clock clock;

	public PageResponse<AdminProductResponse> getProducts(
			@Nullable Long storeId, AdminProductFilter filter, Pageable pageable) {
		LocalDateTime now = LocalDateTime.now(clock);
		Page<Product> products = productFunction.findForAdmin(storeId, filter, pageable);
		List<AdminProductResponse> content = products.getContent().stream()
				.map(product -> AdminProductResponse.from(product, now))
				.toList();
		return PageResponse.of(content, products);
	}
}
