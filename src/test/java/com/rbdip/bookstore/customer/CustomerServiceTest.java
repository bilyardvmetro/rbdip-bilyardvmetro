package com.rbdip.bookstore.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class CustomerServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CustomerService service;

    @Autowired
    private CustomerRepository customers;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "+79990000000")
    void reusesIdenticalDetailsWithoutMergingDifferentContacts(String phone) {
        Customer first = service.findOrCreate("Anna", "Moscow", phone);
        Customer repeated = service.findOrCreate("Anna", "Moscow", phone);

        assertThat(repeated.getId()).isEqualTo(first.getId());
        assertThat(repeated.getFullName()).isEqualTo("Anna");
        assertThat(repeated.getAddress()).isEqualTo("Moscow");
        assertThat(repeated.getPhone()).isEqualTo(phone);
        assertThat(customers.count()).isEqualTo(1);

        Customer otherAddress = service.findOrCreate("Anna", "SPb", phone);
        Customer otherPhone = service.findOrCreate("Anna", "Moscow", phone == null ? "" : null);
        Customer otherName = service.findOrCreate("Maria", "Moscow", phone);

        assertThat(List.of(otherAddress.getId(), otherPhone.getId(), otherName.getId()))
                .doesNotContain(first.getId()).doesNotHaveDuplicates();
        assertThat(customers.count()).isEqualTo(4);
    }

    @Test
    void concurrentRequestsReuseTheSameCustomer() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> ids = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 4; i++) {
                ids.add(pool.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("concurrent test did not start");
                    }
                    return service.findOrCreate("Anna", "Moscow", null).getId();
                }));
            }
            start.countDown();
            Long firstId = ids.get(0).get(10, TimeUnit.SECONDS);
            for (Future<Long> id : ids) {
                assertThat(id.get(10, TimeUnit.SECONDS)).isEqualTo(firstId);
            }
        }
        assertThat(customers.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "'Anna Sidorova','Anna','Sidorova'",
        "'Anna','Anna',''",
        "'Anna ','Anna ',''",
        "'  Anna  Sidorova  ','',' Anna  Sidorova  '",
        "'Иванов Иван Иванович','Иванов','Иван Иванович'",
        "'李 雷','李','雷'",
        "'Anna\tSidorova','Anna\tSidorova',''"
    }, ignoreLeadingAndTrailingWhitespace = false)
    void preservesFullNameAndReadsOnlyTheNewColumns(String fullName, String firstName, String lastName) {
        Customer customer = service.findOrCreate(fullName, "Moscow", null);

        assertThat(customer.getFullName()).isEqualTo(fullName);
        assertThat(customer.getFirstName()).isEqualTo(firstName);
        assertThat(customer.getLastName()).isEqualTo(lastName);
        assertThat(service.findOrCreate(fullName, "Moscow", null).getId()).isEqualTo(customer.getId());
    }
}
