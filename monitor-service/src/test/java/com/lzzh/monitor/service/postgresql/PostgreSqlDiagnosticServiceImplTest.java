package com.lzzh.monitor.service.postgresql;

import com.lzzh.monitor.api.response.PgBlockingNodeVo;
import com.lzzh.monitor.api.response.PgSessionVo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostgreSqlDiagnosticServiceImplTest {

    @Test
    void buildsThreeLevelBlockingTreeAndCountsAffectedSessions() {
        PgSessionVo root = session(101, List.of(), List.of());
        PgSessionVo middle = session(102, List.of(101), List.of("public.orders"));
        PgSessionVo leaf = session(103, List.of(102), List.of("public.order_items"));

        List<PgBlockingNodeVo> trees =
                PostgreSqlDiagnosticServiceImpl.buildBlockingTree(List.of(root, middle, leaf));

        assertThat(trees).hasSize(1);
        PgBlockingNodeVo rootNode = trees.getFirst();
        assertThat(rootNode.getPid()).isEqualTo(101);
        assertThat(rootNode.getAffectedSessions()).isEqualTo(2);
        assertThat(rootNode.getChildren()).singleElement()
                .satisfies(node -> {
                    assertThat(node.getPid()).isEqualTo(102);
                    assertThat(node.getLockedObjects()).containsExactly("public.orders");
                    assertThat(node.getChildren()).singleElement()
                            .extracting(PgBlockingNodeVo::getPid).isEqualTo(103);
                });
    }

    private static PgSessionVo session(int pid, List<Integer> blockedBy, List<String> lockedObjects) {
        PgSessionVo session = new PgSessionVo();
        session.setPid(pid);
        session.setBlockedBy(blockedBy);
        session.setLockedObjects(lockedObjects);
        return session;
    }
}