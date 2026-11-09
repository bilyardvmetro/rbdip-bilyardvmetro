package com.rbdip.bookstore.order;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @EntityGraph(attributePaths = "product")
    @Query("select i from OrderItem i where i.order.id = :orderId order by i.id")
    List<OrderItem> findByOrderId(@Param("orderId") Long orderId);

    @Query("""
            select count(i) > 0 from OrderItem i
            join i.order o join o.customer c
            where i.product.id = :productId
                and (case when c.lastName = '' then c.firstName
                    else concat(c.firstName, ' ', c.lastName) end) = :customerFullName
            """)
    boolean existsPurchase(@Param("productId") Long productId, @Param("customerFullName") String customerFullName);
}
