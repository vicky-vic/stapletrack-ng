package ng.stapletrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class SpreadTests {

	@Test
	void underFiftyPercentReadsAsPercentage() {
		Spread spread = Spread.between(new BigDecimal("1800"), new BigDecimal("2106"));

		assertThat(spread.multiple()).isFalse();
		assertThat(spread.percent()).isEqualTo(17);
		assertThat(spread.label()).isEqualTo("17% more");
		assertThat(spread.shortLabel()).isEqualTo("17%");
	}

	@Test
	void fiftyPercentOrMoreReadsAsMultiple() {
		// 2693.41 / 1267.25 = 2.125… → 2.1×
		Spread spread = Spread.between(new BigDecimal("1267.25"), new BigDecimal("2693.41"));

		assertThat(spread.multiple()).isTrue();
		assertThat(spread.percent()).isEqualTo(113);
		assertThat(spread.label()).isEqualTo("2.1×");
		assertThat(spread.shortLabel()).isEqualTo("2.1×");
	}

	@Test
	void thresholdIsInclusiveAtExactlyFiftyPercent() {
		assertThat(Spread.between(new BigDecimal("100"), new BigDecimal("150")).label()).isEqualTo("1.5×");
		assertThat(Spread.between(new BigDecimal("100"), new BigDecimal("149.99")).label()).isEqualTo("50% more");
	}

	@Test
	void equalPricesAreZeroPercent() {
		assertThat(Spread.between(new BigDecimal("500"), new BigDecimal("500")).label()).isEqualTo("0% more");
	}

	@Test
	void rejectsNonPositiveLowPrice() {
		assertThatThrownBy(() -> Spread.between(BigDecimal.ZERO, BigDecimal.TEN))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
