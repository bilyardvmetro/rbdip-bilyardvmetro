package com.rbdip.bookstore.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ReviewApiTest extends AbstractIntegrationTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void acceptsReviewsWithOrWithoutAPurchaseAndListsOnlyTheRequestedProduct(boolean purchased) {
        Long productId = createProduct("First");
        Long otherProductId = createProduct("Second");
        if (purchased) {
            ResponseEntity<Map> order = restTemplate.postForEntity("/orders", Map.of(
                    "customerFullName", "Anna Sidorova", "customerAddress", "Moscow",
                    "items", List.of(Map.of("productId", productId, "quantity", 1))), Map.class);
            assertThat(order.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        ResponseEntity<Map> response = restTemplate.postForEntity("/products/" + productId + "/reviews",
                Map.of("authorName", "Anna Sidorova", "rating", 5, "comment", "Good book"), Map.class);
        restTemplate.postForEntity("/products/" + otherProductId + "/reviews",
                Map.of("authorName", "Other", "rating", 3, "comment", "Other book"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("id")).isNotNull();
        ResponseEntity<List> listed = restTemplate.getForEntity("/products/" + productId + "/reviews", List.class);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody())
                .isEqualTo(List.of(Map.of("authorName", "Anna Sidorova", "rating", 5, "comment", "Good book")));
    }

    @Test
    void keepsAnonymousAuthorAndEmptyCommentInTheResponse() {
        Long productId = createProduct("First");
        ResponseEntity<Map> created = restTemplate.postForEntity("/products/" + productId + "/reviews",
                Map.of("rating", 4), Map.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(restTemplate.getForEntity("/products/" + productId + "/reviews", List.class).getBody())
                .isEqualTo(List.of(Map.of("authorName", "anonymous", "rating", 4, "comment", "")));
    }

    private Long createProduct(String name) {
        ResponseEntity<Map> response = restTemplate.postForEntity("/products", Map.of("name", name, "price", "10.00"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Long.valueOf(response.getBody().get("id").toString());
    }
}
