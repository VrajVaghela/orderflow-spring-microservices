package com.orderflow.query.api;

import com.orderflow.query.model.OrderViewRepository;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class OrderGraphqlController {

    private final OrderViewRepository orders;

    public OrderGraphqlController(OrderViewRepository orders) {
        this.orders = orders;
    }

    @QueryMapping
    public OrderView order(@Argument String id) {
        return orders.findById(id).map(OrderView::from).orElse(null);
    }

    @QueryMapping
    public List<OrderView> ordersByCustomer(@Argument String customerId) {
        return orders.findByCustomerIdOrderByUpdatedAtDesc(customerId).stream().map(OrderView::from).toList();
    }
}
