package com.orderflow.inventory.domain;

public class UnknownSkuException extends RuntimeException {

    public UnknownSkuException(String sku) {
        super("unknown sku " + sku);
    }
}
