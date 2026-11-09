package com.rbdip.bookstore.order;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public List<OrderSummary> listOrders() {
        return orderRepository.findAll().stream()
                .map(order -> new OrderSummary(order.getId(), order.getCustomerFullName(), order.getStatus(),
                        order.getItems().stream()
                                .map(item -> new OrderSummary.Item(item.getProductName(), item.getQuantity()))
                                .toList()))
                .toList();
    }
}
