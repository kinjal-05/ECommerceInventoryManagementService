package com.inventoryservice.commondtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class StockResponse {
	private Long id;
	private Long productId;
	private Integer availableQuantity;
	private Integer reservedQuantity;
}