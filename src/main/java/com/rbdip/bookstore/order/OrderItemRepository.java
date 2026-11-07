package com.rbdip.bookstore.order;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // Load the product for callers outside a transaction. Per-order item queries
    // in OrderController still remain the N+1 exercise for lab 4.
    @EntityGraph(attributePaths = "product")
    List<OrderItem> findByOrderId(Long orderId);
}
