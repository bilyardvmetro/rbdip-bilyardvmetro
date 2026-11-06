package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;

/** Keeps a product and its validated quantity together for pricing and storage. */
record OrderLine(Product product, int quantity) {

    PricingCalculator.LineItem toPricingItem() {
        return new PricingCalculator.LineItem(product.getPrice(), quantity);
    }
}
