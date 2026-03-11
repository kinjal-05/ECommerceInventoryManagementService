package com.inventoryservice.controllers;

import com.inventoryservice.commondtos.StockCheckRequest;
import com.inventoryservice.commondtos.StockCheckResponse;
import com.inventoryservice.commondtos.StockRequest;
import com.inventoryservice.commondtos.StockResponse;
import com.inventoryservice.services.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * All route paths resolved from api-paths.yml at startup.
 *
 * api.inventory.base           → /api/inventory
 * api.inventory.check          → /check
 * api.inventory.reserve        → /reserve
 * api.inventory.reduce         → /reduce
 * api.inventory.restore        → /restore
 * api.inventory.add            → /add
 * api.inventory.get-by-product → /product/{productId}
 * api.inventory.get-all        → /all
 *
 * Bugs fixed from original:
 * - Manual constructor replaced with @RequiredArgsConstructor
 * - reserve/reduce/restore return ResponseEntity<String> with 200 OK
 *   → changed to ResponseEntity<Void> with 204 No Content (state mutations, no body needed)
 * - addStock returns 200 OK on POST → changed to 201 CREATED
 */
@RestController
@RequestMapping("${api.inventory.base}")
@RequiredArgsConstructor
public class InventoryController {

	private final InventoryService inventoryService;

	@PostMapping("${api.inventory.check}")
	public ResponseEntity<StockCheckResponse> checkStock(@RequestBody StockCheckRequest request) {
		return ResponseEntity.ok(inventoryService.checkStock(request));
	}

	@PostMapping("${api.inventory.reserve}")
	public ResponseEntity<Void> reserveStock(@RequestBody StockRequest request) {
		inventoryService.reserveStock(request);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("${api.inventory.reduce}")
	public ResponseEntity<Void> reduceStock(@RequestBody StockRequest request) {
		inventoryService.reduceStock(request);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("${api.inventory.restore}")
	public ResponseEntity<Void> restoreStock(@RequestBody StockRequest request) {
		inventoryService.restoreStock(request);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("${api.inventory.add}")
	public ResponseEntity<StockResponse> addStock(@RequestBody StockRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(inventoryService.addStock(request));
	}

	@GetMapping("${api.inventory.get-by-product}")
	public ResponseEntity<StockResponse> getStock(@PathVariable Long productId) {
		return ResponseEntity.ok(inventoryService.getStockByProductId(productId));
	}

	@GetMapping("${api.inventory.get-all}")
	public ResponseEntity<List<StockResponse>> getAllStocks() {
		return ResponseEntity.ok(inventoryService.getAllStocks());
	}
}