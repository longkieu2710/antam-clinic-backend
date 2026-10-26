package vn.antam.clinic.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CodesTest {

    @Test
    void next_existingCodes_returnsMaxPlusOne() {
        assertThat(Codes.next("BS-CN1-", List.of("BS-CN1-01", "BS-CN1-07"), 2)).isEqualTo("BS-CN1-08");
    }

    @Test
    void next_otherPrefixOrNonNumericTail_isIgnored() {
        assertThat(Codes.next("BS-CN1-", List.of("BS-CN1-02", "YT-CN1-09"), 2)).isEqualTo("BS-CN1-03");
        assertThat(Codes.next("T", List.of("T01", "T04", "TX"), 2)).isEqualTo("T05");
    }

    @Test
    void next_noExistingCodes_startsAtOne() {
        assertThat(Codes.next("BS-CN2-", List.of(), 2)).isEqualTo("BS-CN2-01");
    }

    @Test
    void blankToNull_blankOrNull_returnsNull() {
        assertThat(Codes.blankToNull(null)).isNull();
        assertThat(Codes.blankToNull("   ")).isNull();
    }

    @Test
    void blankToNull_text_isTrimmed() {
        assertThat(Codes.blankToNull("  CN1 ")).isEqualTo("CN1");
    }
}
