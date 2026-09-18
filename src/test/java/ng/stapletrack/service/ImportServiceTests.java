package ng.stapletrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import ng.stapletrack.repository.NationalPriceRepository;
import ng.stapletrack.repository.StateExtremeRepository;
import ng.stapletrack.repository.ZonalPriceRepository;

/**
 * Runs against the local MySQL database. Each test is @Transactional and rolls back,
 * so nothing it imports is left behind — and existing data does not break the assertions.
 */
@SpringBootTest
@Transactional
class ImportServiceTests {

	private static final Path OCT_FILE = Path.of("data/raw/selected_food_oct_2024.xlsx");
	private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	@Autowired
	private ImportService importService;

	@Autowired
	private NationalPriceRepository nationalRepository;

	@Autowired
	private ZonalPriceRepository zonalRepository;

	@Autowired
	private StateExtremeRepository extremeRepository;

	@Test
	void importingSameFileTwiceUpdatesInsteadOfDuplicating() throws IOException {
		assumeTrue(Files.exists(OCT_FILE), "NBS sample file not present: " + OCT_FILE);
		MockMultipartFile upload = new MockMultipartFile("file", "selected_food_oct_2024.xlsx", XLSX,
				Files.readAllBytes(OCT_FILE));

		ImportResult first = importService.importFile(upload);
		long nationalRows = nationalRepository.count();
		long zonalRows = zonalRepository.count();
		long extremeRows = extremeRepository.count();

		ImportResult second = importService.importFile(upload);

		assertThat(first.releaseMonth()).isEqualTo(YearMonth.of(2024, 10));
		assertThat(first.nationalTotal()).isEqualTo(129);
		assertThat(first.zonalTotal()).isEqualTo(258);
		assertThat(first.extremesTotal()).isEqualTo(86);

		assertThat(second.nationalInserted()).isZero();
		assertThat(second.zonalInserted()).isZero();
		assertThat(second.extremesInserted()).isZero();
		assertThat(second.nationalUpdated()).isEqualTo(first.nationalTotal());
		assertThat(second.zonalUpdated()).isEqualTo(first.zonalTotal());
		assertThat(second.extremesUpdated()).isEqualTo(first.extremesTotal());

		assertThat(nationalRepository.count()).isEqualTo(nationalRows);
		assertThat(zonalRepository.count()).isEqualTo(zonalRows);
		assertThat(extremeRepository.count()).isEqualTo(extremeRows);
	}

	@Test
	void csvIsNotSupportedYet() {
		MockMultipartFile csv = new MockMultipartFile("file", "prices.csv", "text/csv", "item,price\n".getBytes());
		assertThatThrownBy(() -> importService.importFile(csv))
				.isInstanceOf(UnsupportedOperationException.class)
				.hasMessage("CSV upload lands in a later iteration");
	}

	@Test
	void otherExtensionsAreRejected() {
		MockMultipartFile pdf = new MockMultipartFile("file", "report.pdf", "application/pdf", new byte[] { 1 });
		assertThatThrownBy(() -> importService.importFile(pdf)).isInstanceOf(IllegalArgumentException.class);
	}

}
