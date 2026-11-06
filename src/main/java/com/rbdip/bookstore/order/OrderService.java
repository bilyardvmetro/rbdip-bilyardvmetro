package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates order creation within a single transaction. */
@Service
public class OrderService {

    private final OrderValidator validator;
    private final OrderItemResolver itemResolver;
    private final PricingCalculator pricingCalculator;
    private final OrderPersistence persistence;
    private final OrderConfirmationNotifier notifier;

    public OrderService(
            OrderValidator validator,
            OrderItemResolver itemResolver,
            PricingCalculator pricingCalculator,
            OrderPersistence persistence,
            OrderConfirmationNotifier notifier) {
        this.validator = validator;
        this.itemResolver = itemResolver;
        this.pricingCalculator = pricingCalculator;
        this.persistence = persistence;
        this.notifier = notifier;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        validator.validate(request);
        List<OrderLine> lines = itemResolver.resolve(request.items());
        BigDecimal total = pricingCalculator.calculateOrderTotal(
                lines.stream().map(OrderLine::toPricingItem).toList(),
                request.customerType(), request.couponCode());
        Order order = persistence.save(request, lines);
        notifier.sendConfirmationEmail(request.customerFullName(), order.getId(), total);
        return order;
    }
}
