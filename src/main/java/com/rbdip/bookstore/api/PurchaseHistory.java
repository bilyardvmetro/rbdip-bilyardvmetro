package com.rbdip.bookstore.api;

/** Public contract for consulting purchases without exposing order persistence. */
public interface PurchaseHistory {

    boolean hasPurchased(Long productId, String customerFullName);
}
