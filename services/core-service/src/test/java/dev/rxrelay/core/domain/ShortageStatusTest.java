package dev.rxrelay.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ShortageStatusTest {
  @ParameterizedTest
  @CsvSource({
    "Current,CURRENT",
    "Resolved,RESOLVED",
    "To Be Discontinued,TO_BE_DISCONTINUED",
    "unexpected,UNKNOWN"
  })
  void normalizesSourceStatuses(String source, ShortageStatus expected) {
    assertThat(ShortageStatus.fromSource(source)).isEqualTo(expected);
  }
}
