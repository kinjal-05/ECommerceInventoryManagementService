package com.inventoryservice.repositories;
import java.util.Optional;

import com.inventoryservice.models.ProductStock;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {
	Optional<ProductStock> findByProductId(Long productId);

}
