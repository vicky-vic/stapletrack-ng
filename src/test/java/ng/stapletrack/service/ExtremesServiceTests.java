package ng.stapletrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ng.stapletrack.entity.ExtremeKind;
import ng.stapletrack.entity.StateExtreme;
import ng.stapletrack.repository.StateExtremeRepository;
import ng.stapletrack.service.ExtremesService.Extremes;

class ExtremesServiceTests {

	private static final String RICE = "Rice local sold loose";
	private static final YearMonth OCT_2024 = YearMonth.of(2024, 10);
	private static final YearMonth OCT_2023 = YearMonth.of(2023, 10);

	private StateExtremeRepository repository;
	private ExtremesService service;

	@BeforeEach
	void setUp() {
		repository = mock(StateExtremeRepository.class);
		when(repository.findByItemAndMonthYearAndKind(any(), any(), any())).thenReturn(Optional.empty());
		service = new ExtremesService(repository);
	}

	@Test
	void buildsCheapestAndPriciestWithSpread() {
		stub(OCT_2024, ExtremeKind.LOWEST, "Benue", "1267.25");
		stub(OCT_2024, ExtremeKind.HIGHEST, "Kogi", "2693.41");

		Extremes result = service.findExtremes(RICE, OCT_2024).orElseThrow();

		assertThat(result.item()).isEqualTo(RICE);
		assertThat(result.month()).isEqualTo(OCT_2024);
		assertThat(result.cheapest().state()).isEqualTo("Benue");
		assertThat(result.cheapest().price()).isEqualByComparingTo("1267.25");
		assertThat(result.priciest().state()).isEqualTo("Kogi");
		assertThat(result.priciest().price()).isEqualByComparingTo("2693.41");
		// (2693.41 − 1267.25) / 1267.25 = 112.5% ≥ 50% → shown as a multiple: 2693.41 / 1267.25 = 2.125… → "2.1×"
		assertThat(result.spread().percent()).isEqualTo(113);
		assertThat(result.spread().label()).isEqualTo("2.1×");
		assertThat(result.hasYearEarlier()).isFalse();
	}

	@Test
	void includesYearEarlierPairWhenThatMonthExists() {
		stub(OCT_2024, ExtremeKind.LOWEST, "Benue", "1267.25");
		stub(OCT_2024, ExtremeKind.HIGHEST, "Kogi", "2693.41");
		stub(OCT_2023, ExtremeKind.LOWEST, "Kebbi", "688.00");
		stub(OCT_2023, ExtremeKind.HIGHEST, "Nassarawa", "1122.42");

		Extremes result = service.findExtremes(RICE, OCT_2024).orElseThrow();

		assertThat(result.hasYearEarlier()).isTrue();
		assertThat(result.yearEarlier().month()).isEqualTo(OCT_2023);
		assertThat(result.yearEarlier().cheapest().state()).isEqualTo("Kebbi");
		assertThat(result.yearEarlier().priciest().state()).isEqualTo("Nasarawa");
		assertThat(result.yearEarlier().priciest().price()).isEqualByComparingTo("1122.42");
	}

	@Test
	void canonicalizesStateNamesForDisplay() {
		stub(OCT_2024, ExtremeKind.LOWEST, "Nassarawa", "900");
		stub(OCT_2024, ExtremeKind.HIGHEST, "Lagos", "1800");

		Extremes result = service.findExtremes(RICE, OCT_2024).orElseThrow();

		assertThat(result.cheapest().state()).isEqualTo("Nasarawa");
		assertThat(result.spread().label()).isEqualTo("2.0×");
	}

	@Test
	void narrowSpreadReadsAsPercentage() {
		// (1150 − 1000) / 1000 = 15% < 50% → "15% more"
		stub(OCT_2024, ExtremeKind.LOWEST, "Kano", "1000");
		stub(OCT_2024, ExtremeKind.HIGHEST, "Lagos", "1150");

		Extremes result = service.findExtremes(RICE, OCT_2024).orElseThrow();

		assertThat(result.spread().multiple()).isFalse();
		assertThat(result.spread().label()).isEqualTo("15% more");
	}

	@Test
	void emptyWhenEitherExtremeIsMissing() {
		stub(OCT_2024, ExtremeKind.HIGHEST, "Kogi", "2693.41");

		assertThat(service.findExtremes(RICE, OCT_2024)).isEmpty();
		assertThat(service.findExtremes("Unknown item", OCT_2024)).isEmpty();
	}

	private void stub(YearMonth month, ExtremeKind kind, String state, String price) {
		when(repository.findByItemAndMonthYearAndKind(eq(RICE), eq(month), eq(kind)))
				.thenReturn(Optional.of(new StateExtreme(RICE, month, kind, state, new BigDecimal(price))));
	}

}
