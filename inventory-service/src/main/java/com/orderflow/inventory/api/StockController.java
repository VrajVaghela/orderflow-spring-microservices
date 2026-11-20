package com.orderflow.inventory.api;

import com.orderflow.inventory.domain.UnknownSkuException;
import com.orderflow.inventory.infrastructure.persistence.StockItemRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock")
@Tag(name = "stock", description = "On-hand and reserved quantities")
public class StockController {

    private final StockItemRepository stockItems;

    public StockController(StockItemRepository stockItems) {
        this.stockItems = stockItems;
    }

    @GetMapping("/{sku}")
    @Operation(summary = "Read stock for a SKU")
    public StockResponse get(@PathVariable String sku) {
        return stockItems.findById(sku)
                .map(item -> new StockResponse(item.getSku(), item.getAvailable(), item.getReserved()))
                .orElseThrow(() -> new UnknownSkuException(sku));
    }
}
