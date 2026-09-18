package ng.stapletrack.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;

class YearMonthConverterTests {

	private final YearMonthConverter converter = new YearMonthConverter();

	@Test
	void storesZeroPaddedIsoString() {
		assertThat(converter.convertToDatabaseColumn(YearMonth.of(2024, 3))).isEqualTo("2024-03");
	}

	@Test
	void readsIsoStringBack() {
		assertThat(converter.convertToEntityAttribute("2024-10")).isEqualTo(YearMonth.of(2024, 10));
	}

	@Test
	void storedStringsSortChronologically() {
		String sep = converter.convertToDatabaseColumn(YearMonth.of(2024, 9));
		String oct = converter.convertToDatabaseColumn(YearMonth.of(2024, 10));
		assertThat(sep).isLessThan(oct);
	}

	@Test
	void passesNullThrough() {
		assertThat(converter.convertToDatabaseColumn(null)).isNull();
		assertThat(converter.convertToEntityAttribute(null)).isNull();
	}

}
