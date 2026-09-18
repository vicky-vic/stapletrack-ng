package ng.stapletrack.entity;

import java.math.BigDecimal;
import java.time.YearMonth;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Average price of one NBS item in one geopolitical zone for one month.
 */
@Entity
@Table(name = "zonal_price",
		uniqueConstraints = @UniqueConstraint(name = "uk_zonal_item_month_zone",
				columnNames = { "item", "month_year", "zone" }))
public class ZonalPrice {

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
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Zone zone;

	@NotNull
	@Positive
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal price;

	protected ZonalPrice() {
	}

	public ZonalPrice(String item, YearMonth monthYear, Zone zone, BigDecimal price) {
		this.item = item;
		this.monthYear = monthYear;
		this.zone = zone;
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

	public Zone getZone() {
		return zone;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

}
