package com.orderflow.order.domain.validation;

import com.orderflow.order.application.PlaceOrderCommand;
import com.orderflow.order.domain.OrderValidationException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class LineItemsValidator extends ValidationHandler {

    public static final int MAX_LINES = 20;
    public static final int MAX_QUANTITY = 100;
    public static final long MAX_UNIT_PRICE_MINOR = 10_000_000L;
    public static final long MAX_TOTAL_MINOR = 100_000_000L;
    private static final Pattern SKU = Pattern.compile("^[A-Z0-9-]{1,32}$");

    @Override
    protected void check(PlaceOrderCommand command) {
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new OrderValidationException("at least one line is required");
        }
        if (command.lines().size() > MAX_LINES) {
            throw new OrderValidationException("at most " + MAX_LINES + " lines are allowed");
        }
        Set<String> seen = new HashSet<>();
        long total = 0;
        for (PlaceOrderCommand.Line line : command.lines()) {
            if (line.sku() == null || !SKU.matcher(line.sku()).matches()) {
                throw new OrderValidationException("sku must match " + SKU.pattern());
            }
            if (!seen.add(line.sku())) {
                throw new OrderValidationException("duplicate sku " + line.sku());
            }
            if (line.quantity() < 1 || line.quantity() > MAX_QUANTITY) {
                throw new OrderValidationException("quantity must be between 1 and " + MAX_QUANTITY);
            }
            if (line.unitPriceMinor() < 1 || line.unitPriceMinor() > MAX_UNIT_PRICE_MINOR) {
                throw new OrderValidationException("unitPriceMinor must be between 1 and " + MAX_UNIT_PRICE_MINOR);
            }
            try {
                long lineTotal = Math.multiplyExact(line.unitPriceMinor(), line.quantity());
                total = Math.addExact(total, lineTotal);
            } catch (ArithmeticException ex) {
                throw new OrderValidationException("order total overflowed");
            }
        }
        if (total > MAX_TOTAL_MINOR) {
            throw new OrderValidationException("order total exceeds " + MAX_TOTAL_MINOR + " minor units");
        }
    }
}
