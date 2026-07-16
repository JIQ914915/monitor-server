package com.lzzh.monitor.collector.mysql.item;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CapacityRiskItemTest {
    @Test void calculatesSignedAndUnsignedIntegerLimits() {
        assertThat(CapacityRiskItem.integerMax("int unsigned")).isEqualByComparingTo(new BigDecimal("4294967295"));
        assertThat(CapacityRiskItem.integerMax("bigint(20)")).isEqualByComparingTo(new BigDecimal("9223372036854775807"));
        assertThat(CapacityRiskItem.integerMax("varchar(32)")).isNull();
    }
}
