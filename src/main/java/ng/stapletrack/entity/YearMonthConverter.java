package ng.stapletrack.entity;

import java.time.YearMonth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link YearMonth} as its ISO "YYYY-MM" string in a VARCHAR(7) column.
 * Zero-padded ISO strings sort chronologically, so ORDER BY and BETWEEN still work.
 */
@Converter(autoApply = true)
public class YearMonthConverter implements AttributeConverter<YearMonth, String> {

	@Override
	public String convertToDatabaseColumn(YearMonth attribute) {
		return attribute == null ? null : attribute.toString();
	}

	@Override
	public YearMonth convertToEntityAttribute(String dbData) {
		return dbData == null ? null : YearMonth.parse(dbData);
	}

}
