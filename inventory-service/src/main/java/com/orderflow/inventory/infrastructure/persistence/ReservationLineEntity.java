package com.orderflow.inventory.infrastructure.persistence;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservation_lines")
public class ReservationLineEntity {

    @EmbeddedId
    private ReservationLineId id;

    private int quantity;

    public ReservationLineId getId() {
        return id;
    }

    public void setId(ReservationLineId id) {
        this.id = id;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
