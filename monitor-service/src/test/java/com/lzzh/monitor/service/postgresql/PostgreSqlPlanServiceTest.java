package com.lzzh.monitor.service.postgresql;

import com.lzzh.monitor.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgreSqlPlanServiceTest {

    @Test
    void acceptsReadOnlyStatementsAndRemovesTrailingSemicolon() {
        assertThat(PostgreSqlPlanService.sanitizePlanSql(" select * from orders; "))
                .isEqualTo("select * from orders");
        assertThat(PostgreSqlPlanService.sanitizePlanSql("WITH x AS (SELECT 1) SELECT * FROM x"))
                .startsWith("WITH");
    }

    @Test
    void rejectsMutatingMultiStatementAndAnalyzeInputs() {
        assertThatThrownBy(() -> PostgreSqlPlanService.sanitizePlanSql("delete from orders"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PostgreSqlPlanService.sanitizePlanSql("select 1; select 2"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PostgreSqlPlanService.sanitizePlanSql("EXPLAIN ANALYZE select 1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsParametersAndCommentsToKeepStatementBoundaryUnambiguous() {
        assertThatThrownBy(() -> PostgreSqlPlanService.sanitizePlanSql("select * from t where id=$1"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PostgreSqlPlanService.sanitizePlanSql("select 1 -- comment"))
                .isInstanceOf(BusinessException.class);
    }
}