package servicesImpl;

import com.inventoryservice.commondtos.*;
import com.inventoryservice.exceptions.ResourceNotFoundException;
import com.inventoryservice.models.ProductStock;
import com.inventoryservice.repositories.ProductStockRepository;
import com.inventoryservice.servicesImpl.InventoryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryServiceImpl Tests")
class InventoryServiceImplTest {

	@Mock
	private ProductStockRepository stockRepository;

	@Mock
	private StreamBridge streamBridge;

	@InjectMocks
	private InventoryServiceImpl inventoryService;

	// ─────────────────────────────────────────────────────────────────────────
	// Helpers
	// ─────────────────────────────────────────────────────────────────────────

	private ProductStock buildStock(Long productId, int available, int reserved) {
		ProductStock stock = new ProductStock();
		stock.setId(productId * 10);
		stock.setProductId(productId);
		stock.setAvailableQuantity(available);
		stock.setReservedQuantity(reserved);
		return stock;
	}

	private StockCheckRequest.StockItem buildCheckItem(Long productId, int qty) {
		StockCheckRequest.StockItem item = new StockCheckRequest.StockItem();
		item.setProductId(productId);
		item.setQuantity(qty);
		return item;
	}

	private StockRequest.StockItem buildRequestItem(Long productId, int qty) {
		StockRequest.StockItem item = new StockRequest.StockItem();
		item.setProductId(productId);
		item.setQuantity(qty);
		return item;
	}

	// =========================================================================
	// checkStock
	// =========================================================================

	@Nested
	@DisplayName("checkStock()")
	class CheckStock {

		@Test
		@DisplayName("All items available → getAvailable() = true")
		void allItemsAvailable() {
			ProductStock stock = buildStock(1L, 50, 0);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

			StockCheckRequest request = new StockCheckRequest();
			request.setItems(List.of(buildCheckItem(1L, 10)));

			StockCheckResponse response = inventoryService.checkStock(request);

			// StockCheckResponse.available is Boolean (wrapper) → getAvailable(), NOT isAvailable()
			assertThat(response.getAvailable()).isTrue();
			assertThat(response.getMessage()).isEqualTo("All items available");
			assertThat(response.getItems()).hasSize(1);

			StockCheckResponse.StockItemStatus status = response.getItems().get(0);
			assertThat(status.getProductId()).isEqualTo(1L);
			assertThat(status.getRequested()).isEqualTo(10);
			// StockItemStatus.available is Integer → getAvailable()
			assertThat(status.getAvailable()).isEqualTo(50);
			// StockItemStatus.sufficient is Boolean (wrapper) → getSufficient(), NOT isSufficient()
			assertThat(status.getSufficient()).isTrue();
		}

		@Test
		@DisplayName("Exact quantity match → sufficient = true")
		void exactQuantityMatch() {
			ProductStock stock = buildStock(2L, 5, 0);
			when(stockRepository.findByProductId(2L)).thenReturn(Optional.of(stock));

			StockCheckRequest request = new StockCheckRequest();
			request.setItems(List.of(buildCheckItem(2L, 5)));

			StockCheckResponse response = inventoryService.checkStock(request);

			assertThat(response.getAvailable()).isTrue();
			assertThat(response.getItems().get(0).getSufficient()).isTrue();
		}

		@Test
		@DisplayName("Insufficient stock → getAvailable() = false")
		void insufficientStock() {
			ProductStock stock = buildStock(3L, 3, 0);
			when(stockRepository.findByProductId(3L)).thenReturn(Optional.of(stock));

			StockCheckRequest request = new StockCheckRequest();
			request.setItems(List.of(buildCheckItem(3L, 10)));

			StockCheckResponse response = inventoryService.checkStock(request);

			assertThat(response.getAvailable()).isFalse();
			assertThat(response.getMessage()).isEqualTo("Some items out of stock");
			assertThat(response.getItems().get(0).getSufficient()).isFalse();
		}

		@Test
		@DisplayName("Product not found → available = 0, sufficient = false")
		void productNotFound() {
			when(stockRepository.findByProductId(99L)).thenReturn(Optional.empty());

			StockCheckRequest request = new StockCheckRequest();
			request.setItems(List.of(buildCheckItem(99L, 1)));

			StockCheckResponse response = inventoryService.checkStock(request);

			assertThat(response.getAvailable()).isFalse();
			StockCheckResponse.StockItemStatus status = response.getItems().get(0);
			assertThat(status.getAvailable()).isZero();
			assertThat(status.getSufficient()).isFalse();
		}

		@Test
		@DisplayName("Mixed items (some sufficient, some not) → overall false")
		void mixedAvailability() {
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(buildStock(1L, 100, 0)));
			when(stockRepository.findByProductId(2L)).thenReturn(Optional.of(buildStock(2L, 2, 0)));

			StockCheckRequest request = new StockCheckRequest();
			request.setItems(List.of(buildCheckItem(1L, 5), buildCheckItem(2L, 10)));

			StockCheckResponse response = inventoryService.checkStock(request);

			assertThat(response.getAvailable()).isFalse();
			assertThat(response.getItems().get(0).getSufficient()).isTrue();
			assertThat(response.getItems().get(1).getSufficient()).isFalse();
		}

		@Test
		@DisplayName("Empty items list → available = true")
		void emptyItemsList() {
			StockCheckRequest request = new StockCheckRequest();
			request.setItems(Collections.emptyList());

			StockCheckResponse response = inventoryService.checkStock(request);

			assertThat(response.getAvailable()).isTrue();
			assertThat(response.getItems()).isEmpty();
		}
	}

	// =========================================================================
	// reserveStock
	// =========================================================================

	@Nested
	@DisplayName("reserveStock()")
	class ReserveStock {

		@Test
		@DisplayName("Sufficient stock → quantities updated correctly")
		void reservesSuccessfully() {
			ProductStock stock = buildStock(1L, 20, 5);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(1L, 10)));

			inventoryService.reserveStock(request);

			assertThat(stock.getAvailableQuantity()).isEqualTo(10);
			assertThat(stock.getReservedQuantity()).isEqualTo(15);
			verify(stockRepository).save(stock);
		}

		@Test
		@DisplayName("Product not found → ResourceNotFoundException")
		void productNotFound_throwsException() {
			when(stockRepository.findByProductId(99L)).thenReturn(Optional.empty());

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(99L, 5)));

			assertThatThrownBy(() -> inventoryService.reserveStock(request))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessageContaining("Stock not found for product: 99");
		}

		@Test
		@DisplayName("Insufficient stock → RuntimeException")
		void insufficientStock_throwsException() {
			ProductStock stock = buildStock(1L, 3, 0);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(1L, 10)));

			assertThatThrownBy(() -> inventoryService.reserveStock(request))
					.isInstanceOf(RuntimeException.class)
					.hasMessageContaining("Insufficient stock for product: 1");
		}

		@Test
		@DisplayName("Exact available quantity → reserve succeeds, available becomes 0")
		void exactAvailableQuantity() {
			ProductStock stock = buildStock(1L, 10, 0);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(1L, 10)));

			inventoryService.reserveStock(request);

			assertThat(stock.getAvailableQuantity()).isZero();
			assertThat(stock.getReservedQuantity()).isEqualTo(10);
		}

		@Test
		@DisplayName("Multiple items → each saved individually")
		void multipleItems_eachSaved() {
			ProductStock stock1 = buildStock(1L, 50, 0);
			ProductStock stock2 = buildStock(2L, 30, 0);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock1));
			when(stockRepository.findByProductId(2L)).thenReturn(Optional.of(stock2));

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(1L, 5), buildRequestItem(2L, 3)));

			inventoryService.reserveStock(request);

			verify(stockRepository, times(2)).save(any(ProductStock.class));
			assertThat(stock1.getAvailableQuantity()).isEqualTo(45);
			assertThat(stock2.getAvailableQuantity()).isEqualTo(27);
		}
	}

	// =========================================================================
	// reduceStock
	// =========================================================================

	@Nested
	@DisplayName("reduceStock()")
	class ReduceStock {

		@Test
		@DisplayName("Reserved >= quantity → reserved decremented")
		void reducesReservedQuantity() {
			ProductStock stock = buildStock(1L, 20, 10);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(1L, 5)));

			inventoryService.reduceStock(request);

			assertThat(stock.getReservedQuantity()).isEqualTo(5);
			verify(stockRepository).save(stock);
		}

		@Test
		@DisplayName("Reserved < quantity → reserved stays unchanged")
		void reservedLessThanQuantity_noop() {
			ProductStock stock = buildStock(1L, 20, 2);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(1L, 5)));

			inventoryService.reduceStock(request);

			assertThat(stock.getReservedQuantity()).isEqualTo(2);
			verify(stockRepository).save(stock);
		}

		@Test
		@DisplayName("Available <= LOW_STOCK_THRESHOLD (10) → publishes LowStockEvent")
		void lowStock_publishesAlert() {
			ProductStock stock = buildStock(1L, 5, 10);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));
			when(streamBridge.send(anyString(), any())).thenReturn(true);

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(1L, 5)));

			inventoryService.reduceStock(request);

			// LowStockEvent: productId (Long), availableQuantity (Integer), message (String)
			ArgumentCaptor<LowStockEvent> eventCaptor = ArgumentCaptor.forClass(LowStockEvent.class);
			verify(streamBridge).send(eq("lowStockAlert-out-0"), eventCaptor.capture());

			LowStockEvent event = eventCaptor.getValue();
			assertThat(event.getProductId()).isEqualTo(1L);
			assertThat(event.getAvailableQuantity()).isEqualTo(5);
			assertThat(event.getMessage()).contains("Low stock alert");
		}

		@Test
		@DisplayName("Available above threshold → StreamBridge NOT called")
		void aboveThreshold_noAlert() {
			ProductStock stock = buildStock(1L, 50, 10);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(1L, 5)));

			inventoryService.reduceStock(request);

			verifyNoInteractions(streamBridge);
		}

		@Test
		@DisplayName("Available exactly at threshold (10) → publishes alert")
		void exactlyAtThreshold_publishesAlert() {
			ProductStock stock = buildStock(1L, 10, 5);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));
			when(streamBridge.send(anyString(), any())).thenReturn(true);

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(1L, 5)));

			inventoryService.reduceStock(request);

			verify(streamBridge).send(eq("lowStockAlert-out-0"), any(LowStockEvent.class));
		}

		@Test
		@DisplayName("Product not found → ResourceNotFoundException")
		void productNotFound_throwsException() {
			when(stockRepository.findByProductId(99L)).thenReturn(Optional.empty());

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(99L, 5)));

			assertThatThrownBy(() -> inventoryService.reduceStock(request))
					.isInstanceOf(ResourceNotFoundException.class);
		}
	}

	// =========================================================================
	// restoreStock
	// =========================================================================

	@Nested
	@DisplayName("restoreStock()")
	class RestoreStock {

		@Test
		@DisplayName("Restores available and decrements reserved when reserved >= quantity")
		void restoresSuccessfully() {
			ProductStock stock = buildStock(1L, 10, 15);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(1L, 5)));

			inventoryService.restoreStock(request);

			assertThat(stock.getAvailableQuantity()).isEqualTo(15);
			assertThat(stock.getReservedQuantity()).isEqualTo(10);
			verify(stockRepository).save(stock);
		}

		@Test
		@DisplayName("Reserved < quantity → available restored, reserved unchanged")
		void reservedLessThanQuantity_reservedUnchanged() {
			ProductStock stock = buildStock(1L, 10, 2);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(1L, 5)));

			inventoryService.restoreStock(request);

			assertThat(stock.getAvailableQuantity()).isEqualTo(15);
			assertThat(stock.getReservedQuantity()).isEqualTo(2); // unchanged
		}

		@Test
		@DisplayName("Product not found → ResourceNotFoundException")
		void productNotFound_throwsException() {
			when(stockRepository.findByProductId(99L)).thenReturn(Optional.empty());

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(99L, 5)));

			assertThatThrownBy(() -> inventoryService.restoreStock(request))
					.isInstanceOf(ResourceNotFoundException.class);
		}

		@Test
		@DisplayName("Multiple items → all restored and saved")
		void multipleItems_allRestored() {
			ProductStock s1 = buildStock(1L, 5, 10);
			ProductStock s2 = buildStock(2L, 0, 8);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(s1));
			when(stockRepository.findByProductId(2L)).thenReturn(Optional.of(s2));

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(1L, 4), buildRequestItem(2L, 8)));

			inventoryService.restoreStock(request);

			assertThat(s1.getAvailableQuantity()).isEqualTo(9);
			assertThat(s2.getAvailableQuantity()).isEqualTo(8);
			verify(stockRepository, times(2)).save(any(ProductStock.class));
		}
	}

	// =========================================================================
	// addStock
	// =========================================================================

	@Nested
	@DisplayName("addStock()")
	class AddStock {

		@Test
		@DisplayName("Single item → available quantity increased, StockResponse returned")
		void addStockSingleItem() {
			ProductStock stock = buildStock(1L, 20, 0);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));
			when(stockRepository.save(stock)).thenReturn(stock);

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(1L, 30)));

			StockResponse response = inventoryService.addStock(request);

			assertThat(stock.getAvailableQuantity()).isEqualTo(50);
			assertThat(response).isNotNull();
			// StockResponse fields: id (Long), productId (Long),
			//                       availableQuantity (Integer), reservedQuantity (Integer)
			assertThat(response.getProductId()).isEqualTo(1L);
			assertThat(response.getAvailableQuantity()).isEqualTo(50);
			assertThat(response.getReservedQuantity()).isZero();
		}

		@Test
		@DisplayName("Multiple items → returns last item's StockResponse")
		void multipleItems_returnsLastResponse() {
			ProductStock s1 = buildStock(1L, 10, 0);
			ProductStock s2 = buildStock(2L, 5, 3);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(s1));
			when(stockRepository.findByProductId(2L)).thenReturn(Optional.of(s2));
			when(stockRepository.save(s1)).thenReturn(s1);
			when(stockRepository.save(s2)).thenReturn(s2);

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(1L, 10), buildRequestItem(2L, 20)));

			StockResponse response = inventoryService.addStock(request);

			// Last item processed is product 2
			assertThat(response.getProductId()).isEqualTo(2L);
			assertThat(response.getAvailableQuantity()).isEqualTo(25);
			assertThat(response.getReservedQuantity()).isEqualTo(3);
		}

		@Test
		@DisplayName("Product not found → RuntimeException")
		void productNotFound_throwsException() {
			when(stockRepository.findByProductId(99L)).thenReturn(Optional.empty());

			StockRequest request = new StockRequest();
			request.setItems(List.of(buildRequestItem(99L, 10)));

			assertThatThrownBy(() -> inventoryService.addStock(request))
					.isInstanceOf(RuntimeException.class)
					.hasMessageContaining("Stock not found for productId: 99");
		}
	}

	// =========================================================================
	// getStockByProductId
	// =========================================================================

	@Nested
	@DisplayName("getStockByProductId()")
	class GetStockByProductId {

		@Test
		@DisplayName("Existing product → returns correctly mapped StockResponse")
		void existingProduct_returnsResponse() {
			ProductStock stock = buildStock(1L, 100, 5);
			when(stockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

			StockResponse response = inventoryService.getStockByProductId(1L);

			assertThat(response.getId()).isEqualTo(10L);       // id = productId * 10
			assertThat(response.getProductId()).isEqualTo(1L);
			assertThat(response.getAvailableQuantity()).isEqualTo(100);
			assertThat(response.getReservedQuantity()).isEqualTo(5);
		}

		@Test
		@DisplayName("Non-existent product → ResourceNotFoundException")
		void nonExistentProduct_throwsException() {
			when(stockRepository.findByProductId(42L)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> inventoryService.getStockByProductId(42L))
					.isInstanceOf(ResourceNotFoundException.class)
					.hasMessageContaining("Stock not found for product: 42");
		}
	}

	// =========================================================================
	// getAllStocks
	// =========================================================================

	@Nested
	@DisplayName("getAllStocks()")
	class GetAllStocks {

		@Test
		@DisplayName("Returns all stocks mapped to StockResponse list")
		void returnsAllStocks() {
			List<ProductStock> stocks = List.of(
					buildStock(1L, 10, 2),
					buildStock(2L, 30, 0)
			);
			when(stockRepository.findAll()).thenReturn(stocks);

			List<StockResponse> responses = inventoryService.getAllStocks();

			assertThat(responses).hasSize(2);
			assertThat(responses.get(0).getProductId()).isEqualTo(1L);
			assertThat(responses.get(0).getAvailableQuantity()).isEqualTo(10);
			assertThat(responses.get(0).getReservedQuantity()).isEqualTo(2);
			assertThat(responses.get(1).getProductId()).isEqualTo(2L);
			assertThat(responses.get(1).getAvailableQuantity()).isEqualTo(30);
			assertThat(responses.get(1).getReservedQuantity()).isZero();
		}

		@Test
		@DisplayName("Empty repository → returns empty list")
		void emptyRepository_returnsEmptyList() {
			when(stockRepository.findAll()).thenReturn(Collections.emptyList());

			List<StockResponse> responses = inventoryService.getAllStocks();

			assertThat(responses).isEmpty();
		}
	}
}