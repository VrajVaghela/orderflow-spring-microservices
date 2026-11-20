package com.orderflow.order.api;

import com.orderflow.order.application.PlaceOrderCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PlaceOrderRequest(
        @Schema(example = "cus_1001") String customerId,
        @Schema(example = "USD", allowableValues = {"USD", "EUR"}) String currency,
        @Schema(example = "CARD", allowableValues = {"CARD", "WALLET"}) String paymentMethod,
        @Schema(example = "tok_visa_4242", description = "Fake token. A CARD token ending in 0002 is declined.")
        String paymentToken,
        List<Line> lines
) {

    public record Line(
            @Schema(example = "SKU-100") String sku,
            @Schema(example = "2") int quantity,
            @Schema(example = "1999") long unitPriceMinor
    ) {
    }

    public PlaceOrderCommand toCommand() {
        List<PlaceOrderCommand.Line> mapped = lines == null
                ? List.of()
                : lines.stream()
                .map(line -> new PlaceOrderCommand.Line(line.sku(), line.quantity(), line.unitPriceMinor()))
                .toList();
        return new PlaceOrderCommand(customerId, currency, paymentMethod, paymentToken, mapped);
    }
}
