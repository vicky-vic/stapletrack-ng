package ng.stapletrack.entity;

import java.math.BigDecimal;
import java.time.YearMonth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * National average price of one NBS item for one month.
 */
@Entity
@Table(name = "national_price",
		uniqueConstraints = @UniqueConstraint(name = "uk_national_item_month", columnNames = { "item", "month_year" }))
public class NationalPrice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Size(max = 120)
	@Column(nullable = false, length = 120)
	private String item;

	@NotNull
	@Column(name = "month_year", nullable = false, length = 7)
	private YearMonth monthYear;

	@NotNull
	@Positive
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal price;

	protected NationalPrice() {
	}

	public NationalPrice(String item, YearMonth monthYear, BigDecimal price) {
		this.item = item;
		this.monthYear = monthYear;
		this.price = price;
	}

	public Long getId() {
		return id;
	}

	public String getItem() {
		return item;
	}

	public YearMonth getMonthYear() {
		return monthYear;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

}
