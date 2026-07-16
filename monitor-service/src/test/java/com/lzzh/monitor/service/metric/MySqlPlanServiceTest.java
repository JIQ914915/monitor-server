package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MySqlPlanServiceTest {
    @Test void allowsSingleReadOrDmlStatementWithoutAnalyze() {
        assertThat(MySqlPlanService.sanitize("select * from t where id=1;"))
                .isEqualTo("select * from t where id=1");
    }

    @Test void rejectsMultipleStatementsAndDdl() {
        assertThatThrownBy(() -> MySqlPlanService.sanitize("select 1; drop table t"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> MySqlPlanService.sanitize("alter table t add c int"))
                .isInstanceOf(BusinessException.class);
    }
}
