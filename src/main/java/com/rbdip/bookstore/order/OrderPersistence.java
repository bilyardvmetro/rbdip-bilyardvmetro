package com.rbdip.bookstore.order;

import com.rbdip.bookstore.customer.Customer;
import com.rbdip.bookstore.customer.CustomerService;
import java.util.List;
import org.springframework.stereotype.Component;

/** Stores an order and its customer/product references in the caller's transaction. */
@Component
public class OrderPersistence {

    private static final String INITIAL_STATUS = "new";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerService customerService;

    public OrderPersistence(
            OrderRepository orderRepository, OrderItemRepository orderItemRepository, CustomerService customerService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.customerService = customerService;
    }

    public Order save(CreateOrderRequest request, List<OrderLine> lines) {
        Customer customer = customerService.findOrCreate(
                request.customerFullName(), request.customerAddress(), request.customerPhone());
        Order order = orderRepository.save(new Order(customer, INITIAL_STATUS));
        for (OrderLine line : lines) {
            orderItemRepository.save(order.addItem(line.product(), line.quantity()));
        }
        return order;
    }
}
