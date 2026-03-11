package com.inventoryservice.servicesImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.inventoryservice.commondtos.*;
import com.inventoryservice.exceptions.ResourceNotFoundException;
import com.inventoryservice.models.ProductStock;
import com.inventoryservice.repositories.ProductStockRepository;
import com.inventoryservice.services.InventoryService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

	private final ProductStockRepository stockRepository;
	private final StreamBridge streamBridge;

	private static final int LOW_STOCK_THRESHOLD = 10;

	@Override
	public StockCheckResponse checkStock(StockCheckRequest request) {

		StockCheckResponse response = new StockCheckResponse();
		List<StockCheckResponse.StockItemStatus> itemStatuses = new ArrayList<>();
		boolean allAvailable = true;

		for (StockCheckRequest.StockItem item : request.getItems()) {

			ProductStock stock = stockRepository.findByProductId(item.getProductId()).orElse(null);

			StockCheckResponse.StockItemStatus status = new StockCheckResponse.StockItemStatus();
			status.setProductId(item.getProductId());
			status.setRequested(item.getQuantity());

			if (stock == null) {
				status.setAvailable(0);
				status.setSufficient(false);
				allAvailable = false;

			} else {
				status.setAvailable(stock.getAvailableQuantity());
				boolean sufficient = stock.getAvailableQuantity() >= item.getQuantity();
				status.setSufficient(sufficient);
				if (!sufficient) {
					allAvailable = false;
				}

			}

			itemStatuses.add(status);
		}

		response.setAvailable(allAvailable);
		response.setItems(itemStatuses);
		response.setMessage(allAvailable ? "All items available" : "Some items out of stock");

		return response;
	}

	@Override
	public void reserveStock(StockRequest request) {
		for (StockRequest.StockItem item : request.getItems()) {
			ProductStock stock = stockRepository.findByProductId(item.getProductId()).orElseThrow(
					() -> new ResourceNotFoundException("Stock not found for product: " + item.getProductId()));

			if (stock.getAvailableQuantity() < item.getQuantity()) {
				throw new RuntimeException("Insufficient stock for product: " + item.getProductId());
			}

			stock.setAvailableQuantity(stock.getAvailableQuantity() - item.getQuantity());
			stock.setReservedQuantity(stock.getReservedQuantity() + item.getQuantity());
			stockRepository.save(stock);

		}
	}

	@Override
	public void reduceStock(StockRequest request) {
		for (StockRequest.StockItem item : request.getItems()) {
			ProductStock stock = stockRepository.findByProductId(item.getProductId()).orElseThrow(
					() -> new ResourceNotFoundException("Stock not found for product: " + item.getProductId()));

			if (stock.getReservedQuantity() >= item.getQuantity()) {
				stock.setReservedQuantity(stock.getReservedQuantity() - item.getQuantity());
			}
			stockRepository.save(stock);

			if (stock.getAvailableQuantity() <= LOW_STOCK_THRESHOLD) {
				publishLowStockAlert(stock);
			}
		}
	}

	@CircuitBreaker(name = "inventoryService", fallbackMethod = "lowStockFallback")
	private void publishLowStockAlert(ProductStock stock) {
		try {
			LowStockEvent event = new LowStockEvent();
			event.setProductId(stock.getProductId());
			event.setAvailableQuantity(stock.getAvailableQuantity());
			event.setMessage("Low stock alert! Product " + stock.getProductId() + " has only "
					+ stock.getAvailableQuantity() + " units left.");

			streamBridge.send("lowStockAlert-out-0", event);

		} catch (Exception e) {
			System.out.println("❌ Failed to publish low stock alert: " + e.getMessage());
		}
	}

	private void lowStockFallback(ProductStock stock, Throwable ex) {
		System.out.println("⚠️ Circuit Breaker triggered for low stock alert");
		System.out.println("Reason: " + ex.getMessage());
	}

	@Override
	public void restoreStock(StockRequest request) {
		for (StockRequest.StockItem item : request.getItems()) {
			ProductStock stock = stockRepository.findByProductId(item.getProductId()).orElseThrow(
					() -> new ResourceNotFoundException("Stock not found for product: " + item.getProductId()));

			stock.setAvailableQuantity(stock.getAvailableQuantity() + item.getQuantity());
			if (stock.getReservedQuantity() >= item.getQuantity()) {
				stock.setReservedQuantity(stock.getReservedQuantity() - item.getQuantity());
			}
			stockRepository.save(stock);

		}
	}

	@Override
	public StockResponse addStock(StockRequest request) {

		StockResponse lastResponse = null;

		for (StockRequest.StockItem item : request.getItems()) {

			ProductStock stock = stockRepository.findByProductId(item.getProductId())
					.orElseThrow(() -> new RuntimeException(
							"Stock not found for productId: " + item.getProductId()));

			stock.setAvailableQuantity(stock.getAvailableQuantity() + item.getQuantity());

			ProductStock updatedStock = stockRepository.save(stock);

			lastResponse = mapToResponse(updatedStock);
		}

		return lastResponse;
	}

	@Override
	public StockResponse getStockByProductId(Long productId) {
		ProductStock stock = stockRepository.findByProductId(productId)
				.orElseThrow(() -> new ResourceNotFoundException("Stock not found for product: " + productId));
		return mapToResponse(stock);
	}

	@Override
	public List<StockResponse> getAllStocks() {
		return stockRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
	}

	private StockResponse mapToResponse(ProductStock stock) {
		StockResponse response = StockResponse.builder().id(stock.getId()).productId(stock.getProductId())
				.availableQuantity(stock.getAvailableQuantity()).reservedQuantity(stock.getReservedQuantity()).build();
		return response;
	}
}