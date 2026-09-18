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
 * The most or least expensive state for one NBS item in one month,
 * e.g. "Bauchi (3750)" from the Highest column.
 */
@Entity
@Table(name = "state_extreme",
		uniqueConstraints = @UniqueConstraint(name = "uk_extreme_item_month_kind",
				columnNames = { "item", "month_year", "kind" }))
public class StateExtreme {

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
	@Column(nullable = false, length = 10)
	private ExtremeKind kind;

	@NotBlank
	@Size(max = 40)
	@Column(nullable = false, length = 40)
	private String state;

	@NotNull
	@Positive
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal price;

	protected StateExtreme() {
	}

	public StateExtreme(String item, YearMonth monthYear, ExtremeKind kind, String state, BigDecimal price) {
		this.item = item;
		this.monthYear = monthYear;
		this.kind = kind;
		this.state = state;
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

	public ExtremeKind getKind() {
		return kind;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

}
