package com.inventoryservice.controllers;
import java.util.List;

import com.inventoryservice.commondtos.StockCheckRequest;
import com.inventoryservice.commondtos.StockCheckResponse;
import com.inventoryservice.commondtos.StockRequest;
import com.inventoryservice.commondtos.StockResponse;
import com.inventoryservice.services.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

	private final InventoryService inventoryService;

	public InventoryController(InventoryService inventoryService) {
		this.inventoryService = inventoryService;
	}

	@PostMapping("/check")
	public ResponseEntity<StockCheckResponse> checkStock(@RequestBody StockCheckRequest request) {
		return ResponseEntity.ok(inventoryService.checkStock(request));
	}

	@PostMapping("/reserve")
	public ResponseEntity<String> reserveStock(@RequestBody StockRequest request) {
		inventoryService.reserveStock(request);
		return ResponseEntity.ok("Stock reserved successfully");
	}

	@PostMapping("/reduce")
	public ResponseEntity<String> reduceStock(@RequestBody StockRequest request) {
		inventoryService.reduceStock(request);
		return ResponseEntity.ok("Stock reduced successfully");
	}

	@PostMapping("/restore")
	public ResponseEntity<String> restoreStock(@RequestBody StockRequest request) {
		inventoryService.restoreStock(request);
		return ResponseEntity.ok("Stock restored successfully");
	}

	@PostMapping("/add")
	public ResponseEntity<StockResponse> addStock(@RequestBody StockRequest request) {
		return ResponseEntity.ok(inventoryService.addStock(request));
	}

	@GetMapping("/product/{productId}")
	public ResponseEntity<StockResponse> getStock(@PathVariable Long productId) {
		return ResponseEntity.ok(inventoryService.getStockByProductId(productId));
	}

	@GetMapping("/all")
	public ResponseEntity<List<StockResponse>> getAllStocks() {
		return ResponseEntity.ok(inventoryService.getAllStocks());
	}
}
