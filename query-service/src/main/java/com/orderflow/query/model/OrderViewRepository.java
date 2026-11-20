package com.orderflow.query.model;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderViewRepository extends MongoRepository<OrderViewDocument, String> {

    List<OrderViewDocument> findByCustomerIdOrderByUpdatedAtDesc(String customerId);
}
