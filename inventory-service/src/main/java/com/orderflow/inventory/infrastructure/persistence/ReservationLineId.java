package com.orderflow.inventory.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ReservationLineId implements Serializable {

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "sku")
    private String sku;

    public ReservationLineId() {
    }

    public ReservationLineId(String orderId, String sku) {
        this.orderId = orderId;
        this.sku = sku;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getSku() {
        return sku;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReservationLineId that)) {
            return false;
        }
        return Objects.equals(orderId, that.orderId) && Objects.equals(sku, that.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, sku);
    }
}
