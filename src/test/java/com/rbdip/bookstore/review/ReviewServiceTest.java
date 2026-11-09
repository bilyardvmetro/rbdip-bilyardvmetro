package com.rbdip.bookstore.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rbdip.bookstore.api.PurchaseHistory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviews;

    @Mock
    private PurchaseHistory history;

    @InjectMocks
    private ReviewService service;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void consultsTheContractAndKeepsAcceptingReviewsRegardlessOfPurchase(boolean purchased) {
        when(history.hasPurchased(1L, "Anna")).thenReturn(purchased);
        when(reviews.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Review result = service.addReview(1L, "Anna", 5, "Good book");

        assertThat(result.getProductId()).isEqualTo(1L);
        assertThat(result.getAuthorName()).isEqualTo("Anna");
        assertThat(result.getRating()).isEqualTo(5);
        assertThat(result.getComment()).isEqualTo("Good book");
        verify(history).hasPurchased(1L, "Anna");
        verify(reviews).save(result);
    }

    @Test
    void retainsTheAnonymousAuthorAndNullableComment() {
        when(reviews.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Review result = service.addReview(1L, null, 4, null);

        assertThat(result.getAuthorName()).isEqualTo("anonymous");
        assertThat(result.getComment()).isNull();
        verify(history).hasPurchased(1L, null);
    }

    @Test
    void listsReviewsWithoutConsultingPurchaseHistory() {
        List<Review> expected = List.of(new Review(1L, "Anna", 5, "Good book"));
        when(reviews.findByProductId(1L)).thenReturn(expected);

        assertThat(service.listReviews(1L)).isEqualTo(expected);
        verifyNoInteractions(history);
    }
}
