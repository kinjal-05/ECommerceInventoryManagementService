package com.inventoryservice.consumer;
import java.util.function.Consumer;

import com.inventoryservice.commondtos.OrderCancelledEvent;
import com.inventoryservice.repositories.ProductStockRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceConsumer {

	private final ProductStockRepository stockRepository;

	@Bean
	public Consumer<OrderCancelledEvent> orderCancelled() {
		return event -> {
			log.info("📩 Inventory received order-cancelled for orderId: {}", event.getOrderId());

			// ✅ Loop through each item and release stock by productId
			event.getItems().forEach(item -> {
				stockRepository.findByProductId(item.getProductId()).ifPresentOrElse(stock -> {
					stockRepository
							.save(stock.toBuilder().availableQuantity(stock.getAvailableQuantity() + item.getQuantity()) // ✅
									// release
									// back
									.reservedQuantity(Math.max(0, stock.getReservedQuantity() - item.getQuantity())) // ✅
									// reduce
									// reserved
									.build());

					log.info("✅ Released {} units for productId: {}", item.getQuantity(), item.getProductId());
				}, () -> log.warn("⚠️ Stock not found for productId: {}", item.getProductId()));
			});
		};
	}
}