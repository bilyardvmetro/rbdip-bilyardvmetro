package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/** Calculates discounts in order: quantity, customer, coupon, then order volume. */
@Component
public class PricingCalculator {

    private static final int BULK_QUANTITY_THRESHOLD = 10;
    private static final BigDecimal BULK_PRICE_MULTIPLIER = new BigDecimal("0.95");
    private static final String VIP_CUSTOMER = "vip";
    private static final BigDecimal VIP_PRICE_MULTIPLIER = new BigDecimal("0.9");
    private static final String WHOLESALE_CUSTOMER = "wholesale";
    private static final BigDecimal WHOLESALE_PRICE_MULTIPLIER = new BigDecimal("0.85");
    private static final String FIXED_DISCOUNT_COUPON = "SAVE10";
    private static final BigDecimal FIXED_COUPON_DISCOUNT = BigDecimal.TEN;
    private static final String PERCENT_DISCOUNT_COUPON = "SAVE20PERCENT";
    private static final BigDecimal COUPON_PRICE_MULTIPLIER = new BigDecimal("0.8");
    private static final BigDecimal LARGE_ORDER_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal LARGE_ORDER_PRICE_MULTIPLIER = new BigDecimal("0.98");
    private static final int CURRENCY_SCALE = 2;

    public record LineItem(BigDecimal price, int quantity) {
    }

    public BigDecimal calculateOrderTotal(List<LineItem> items, String customerType, String couponCode) {
        BigDecimal total = BigDecimal.ZERO;

        for (LineItem item : items) {
            total = total.add(calculateLineTotal(item));
        }
        total = applyCustomerDiscount(total, customerType);
        total = applyCoupon(total, couponCode).max(BigDecimal.ZERO);
        if (total.compareTo(LARGE_ORDER_THRESHOLD) > 0) {
            total = total.multiply(LARGE_ORDER_PRICE_MULTIPLIER);
        }
        return total.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateLineTotal(LineItem item) {
        BigDecimal total = item.price().multiply(BigDecimal.valueOf(item.quantity()));
        return item.quantity() > BULK_QUANTITY_THRESHOLD ? total.multiply(BULK_PRICE_MULTIPLIER) : total;
    }

    private BigDecimal applyCustomerDiscount(BigDecimal total, String customerType) {
        if (VIP_CUSTOMER.equals(customerType)) {
            return total.multiply(VIP_PRICE_MULTIPLIER);
        }
        if (WHOLESALE_CUSTOMER.equals(customerType)) {
            return total.multiply(WHOLESALE_PRICE_MULTIPLIER);
        }
        return total;
    }

    private BigDecimal applyCoupon(BigDecimal total, String couponCode) {
        if (FIXED_DISCOUNT_COUPON.equals(couponCode)) {
            return total.subtract(FIXED_COUPON_DISCOUNT);
        }
        if (PERCENT_DISCOUNT_COUPON.equals(couponCode)) {
            return total.multiply(COUPON_PRICE_MULTIPLIER);
        }
        return total;
    }
}
