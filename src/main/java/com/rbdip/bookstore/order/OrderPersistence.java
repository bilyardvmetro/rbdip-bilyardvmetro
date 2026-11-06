package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import java.util.List;
import org.springframework.stereotype.Component;

/** Stores an order and its product snapshots in the caller's transaction. */
@Component
public class OrderPersistence {

    private static final String INITIAL_STATUS = "new";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderPersistence(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    public Order save(CreateOrderRequest request, List<OrderLine> lines) {
        Order order = orderRepository.save(new Order(
                request.customerFullName(), request.customerAddress(), request.customerPhone(), INITIAL_STATUS));
        for (OrderLine line : lines) {
            Product product = line.product();
            orderItemRepository.save(new OrderItem(
                    order.getId(), product.getName(), product.getPrice(), line.quantity()));
        }
        return order;
    }
}
