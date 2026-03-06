package com.inventoryservice.services;
import com.inventoryservice.commondtos.StockCheckRequest;
import com.inventoryservice.commondtos.StockCheckResponse;
import com.inventoryservice.commondtos.StockRequest;
import com.inventoryservice.commondtos.StockResponse;

import java.util.List;
public interface InventoryService {

	StockCheckResponse checkStock(StockCheckRequest request);
	void reserveStock(StockRequest request);
	void reduceStock(StockRequest request);
	void restoreStock(StockRequest request);
	StockResponse addStock(StockRequest request);
	StockResponse getStockByProductId(Long productId);
	List<StockResponse> getAllStocks();
}
