package com.orderflow.query.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("order_views")
public class OrderViewDocument {

    @Id
    private String id;

    @Indexed
    private String customerId;

    private String status;
    private String currency;
    private long totalMinor;
    private String paymentMethod;
    private String reservationId;
    private String reason;
    private List<Line> lines = new ArrayList<>();
    private long version;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public long getTotalMinor() {
        return totalMinor;
    }

    public void setTotalMinor(long totalMinor) {
        this.totalMinor = totalMinor;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = lines;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static class Line {
        private String sku;
        private int quantity;
        private long unitPriceMinor;

        public Line() {
        }

        public Line(String sku, int quantity, long unitPriceMinor) {
            this.sku = sku;
            this.quantity = quantity;
            this.unitPriceMinor = unitPriceMinor;
        }

        public String getSku() {
            return sku;
        }

        public int getQuantity() {
            return quantity;
        }

        public long getUnitPriceMinor() {
            return unitPriceMinor;
        }
    }
}
