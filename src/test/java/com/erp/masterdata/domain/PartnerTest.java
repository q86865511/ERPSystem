package com.erp.masterdata.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartnerTest {

    @Test
    void createsActiveVendor() {
        Partner p = new Partner("V-1", "Acme Supplies", true, false, "TAX-1", 30, null, null);

        assertThat(p.isVendor()).isTrue();
        assertThat(p.isCustomer()).isFalse();
        assertThat(p.getPaymentTermsDays()).isEqualTo(30);
        assertThat(p.isActive()).isTrue();
    }

    @Test
    void rejectsBlankCode() {
        assertThatThrownBy(() -> new Partner(" ", "Acme", true, false, null, 30, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativePaymentTerms() {
        assertThatThrownBy(() -> new Partner("V-2", "Acme", true, false, null, -1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
