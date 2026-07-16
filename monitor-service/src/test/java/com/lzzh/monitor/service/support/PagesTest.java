package com.lzzh.monitor.service.support;

import com.lzzh.monitor.common.result.PageParam;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PagesTest {
    @Test
    void windowUsesDefaultsAndCapsPageSize() {
        assertThat(Pages.window(null)).isEqualTo(new Pages.PageWindow(1, 10, 0));

        PageParam param = new PageParam();
        param.setPageNum(3);
        param.setPageSize(1000);

        assertThat(Pages.window(param)).isEqualTo(new Pages.PageWindow(3, 200, 400));
    }

    @Test
    void windowNormalizesInvalidValuesAndPreventsOffsetOverflow() {
        PageParam invalid = new PageParam();
        invalid.setPageNum(0);
        invalid.setPageSize(0);
        assertThat(Pages.window(invalid)).isEqualTo(new Pages.PageWindow(1, 10, 0));

        PageParam huge = new PageParam();
        huge.setPageNum(Long.MAX_VALUE);
        huge.setPageSize(200);
        assertThat(Pages.window(huge).offset()).isEqualTo(Long.MAX_VALUE);
    }
}
