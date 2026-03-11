package com.inventoryservice.consumer;

import java.util.function.Consumer;
import com.inventoryservice.commondtos.ProductEvent1;
import com.inventoryservice.models.ProductStock;
import com.inventoryservice.repositories.ProductStockRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InventoryKafkaConsumer {

	private final ProductStockRepository stockRepository;

	public InventoryKafkaConsumer(ProductStockRepository stockRepository) {
		this.stockRepository = stockRepository;
	}

	@Bean
	public Consumer<ProductEvent1> productInventoryConsumer() {
		return event -> {
			if ("CREATED".equals(event.getAction())) {
				boolean exists = stockRepository.findByProductId(event.getProductId()).isPresent();

				if (!exists) {
					ProductStock stock = new ProductStock(event.getProductId(), 0, 0);
					stockRepository.save(stock);
				} else {
					System.out.println("⚠️ Stock already exists for " + "productId: " + event.getProductId());
				}
			}
		};
	}
}