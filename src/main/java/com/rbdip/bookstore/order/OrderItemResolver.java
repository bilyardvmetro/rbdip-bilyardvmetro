package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Resolves requested product identifiers to order lines. */
@Component
public class OrderItemResolver {

    private final ProductRepository productRepository;
    private final OrderValidator validator;

    public OrderItemResolver(ProductRepository productRepository, OrderValidator validator) {
        this.productRepository = productRepository;
        this.validator = validator;
    }

    public List<OrderLine> resolve(List<CreateOrderRequest.Item> items) {
        List<OrderLine> lines = new ArrayList<>();
        for (CreateOrderRequest.Item item : items) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException("product " + item.productId() + " not found"));
            lines.add(new OrderLine(product, validator.resolveQuantity(item.quantity())));
        }
        return lines;
    }
}
