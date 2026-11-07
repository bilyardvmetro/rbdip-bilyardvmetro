package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @ParameterizedTest
    @CsvSource({
        "10, 10, regular, , 100.00",
        "10, 11, regular, , 104.50",
        "10, 0, regular, , 0.00",
        "10, -1, regular, , 0.00",
        "-10, -1, regular, , 10.00",
        "100, 1, vip, , 90.00",
        "100, 1, wholesale, , 85.00",
        "100, 1, regular, SAVE20PERCENT, 80.00",
        "100, 1, vip, SAVE10, 80.00",
        "100, 1, wholesale, SAVE20PERCENT, 68.00",
        "100, 1, , , 100.00",
        "100, 1, unknown, unknown, 100.00",
        "5, 1, regular, SAVE10, 0.00",
        "10, 1, regular, SAVE10, 0.00",
        "10.01, 1, regular, SAVE10, 0.01",
        "999.99, 1, regular, , 999.99",
        "1000, 1, regular, , 1000.00",
        "1000.01, 1, regular, , 980.01",
        "1010, 1, regular, SAVE10, 1000.00",
        "1010.01, 1, regular, SAVE10, 980.01",
        "1250, 1, regular, SAVE20PERCENT, 1000.00",
        "1111.11, 1, vip, , 1000.00",
        "1111.12, 1, vip, , 980.01",
        "100, 1, VIP, save10, 100.00",
        "100, 11, vip, SAVE10, 930.50",
        "200, 11, vip, SAVE20PERCENT, 1474.70",
        "1.005, 1, regular, , 1.01",
        "-5, 1, regular, , 0.00"
    })
    void preservesDiscountOrderAndBoundaries(
            String price, int quantity, String customerType, String coupon, String expected) {
        BigDecimal total = calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(new BigDecimal(price), quantity)), customerType, coupon);

        assertThat(total).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void appliesQuantityDiscountPerLineAndRoundsOnlyTheFinalTotal() {
        BigDecimal total = calculator.calculateOrderTotal(List.of(
                new PricingCalculator.LineItem(new BigDecimal("0.335"), 1),
                new PricingCalculator.LineItem(new BigDecimal("0.335"), 1),
                new PricingCalculator.LineItem(new BigDecimal("10"), 11),
                new PricingCalculator.LineItem(new BigDecimal("10"), 10)), "regular", null);

        assertThat(total).isEqualTo(new BigDecimal("205.17"));
    }

    @Test
    void returnsZeroForAnEmptyOrder() {
        assertThat(calculator.calculateOrderTotal(List.of(), null, "SAVE10"))
                .isEqualTo(new BigDecimal("0.00"));
    }
}
