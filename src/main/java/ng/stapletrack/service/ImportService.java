package ng.stapletrack.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ng.stapletrack.entity.NationalPrice;
import ng.stapletrack.entity.StateExtreme;
import ng.stapletrack.entity.ZonalPrice;
import ng.stapletrack.parser.NbsPriceWatchParser;
import ng.stapletrack.parser.ParsedFile;
import ng.stapletrack.repository.NationalPriceRepository;
import ng.stapletrack.repository.StateExtremeRepository;
import ng.stapletrack.repository.ZonalPriceRepository;

@Service
public class ImportService {

	private final NbsPriceWatchParser parser;
	private final NationalPriceRepository nationalRepository;
	private final ZonalPriceRepository zonalRepository;
	private final StateExtremeRepository extremeRepository;

	public ImportService(NbsPriceWatchParser parser, NationalPriceRepository nationalRepository,
			ZonalPriceRepository zonalRepository, StateExtremeRepository extremeRepository) {
		this.parser = parser;
		this.nationalRepository = nationalRepository;
		this.zonalRepository = zonalRepository;
		this.extremeRepository = extremeRepository;
	}

	/**
	 * Parses an uploaded NBS workbook and upserts every row on its entity's unique key.
	 * All-or-nothing: any failure rolls back the whole file.
	 *
	 * @throws IllegalArgumentException for an empty file or unsupported extension
	 * @throws UnsupportedOperationException for .csv uploads (not implemented yet)
	 * @throws ng.stapletrack.parser.NbsFormatException when the workbook is not in the NBS layout
	 */
	@Transactional(rollbackFor = Exception.class)
	public ImportResult importFile(MultipartFile file) throws IOException {
		if (file.isEmpty()) {
			throw new IllegalArgumentException("The uploaded file is empty.");
		}
		String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
		if (name.endsWith(".csv")) {
			throw new UnsupportedOperationException("CSV upload lands in a later iteration");
		}
		if (!name.endsWith(".xlsx")) {
			throw new IllegalArgumentException("Unsupported file type — upload an NBS Selected Food Prices Watch .xlsx file.");
		}

		ParsedFile parsed;
		try (InputStream in = file.getInputStream()) {
			parsed = parser.parse(in);
		}

		int nationalInserted = 0;
		int nationalUpdated = 0;
		for (NationalPrice incoming : parsed.nationalPrices()) {
			Optional<NationalPrice> existing = nationalRepository
					.findByItemAndMonthYear(incoming.getItem(), incoming.getMonthYear());
			if (existing.isPresent()) {
				existing.get().setPrice(incoming.getPrice());
				nationalRepository.save(existing.get());
				nationalUpdated++;
			}
			else {
				nationalRepository.save(incoming);
				nationalInserted++;
			}
		}

		int zonalInserted = 0;
		int zonalUpdated = 0;
		for (ZonalPrice incoming : parsed.zonalPrices()) {
			Optional<ZonalPrice> existing = zonalRepository
					.findByItemAndMonthYearAndZone(incoming.getItem(), incoming.getMonthYear(), incoming.getZone());
			if (existing.isPresent()) {
				existing.get().setPrice(incoming.getPrice());
				zonalRepository.save(existing.get());
				zonalUpdated++;
			}
			else {
				zonalRepository.save(incoming);
				zonalInserted++;
			}
		}

		int extremesInserted = 0;
		int extremesUpdated = 0;
		for (StateExtreme incoming : parsed.stateExtremes()) {
			Optional<StateExtreme> existing = extremeRepository
					.findByItemAndMonthYearAndKind(incoming.getItem(), incoming.getMonthYear(), incoming.getKind());
			if (existing.isPresent()) {
				existing.get().setState(incoming.getState());
				existing.get().setPrice(incoming.getPrice());
				extremeRepository.save(existing.get());
				extremesUpdated++;
			}
			else {
				extremeRepository.save(incoming);
				extremesInserted++;
			}
		}

		return new ImportResult(parsed.currentMonth(), nationalInserted, nationalUpdated, zonalInserted, zonalUpdated,
				extremesInserted, extremesUpdated, parsed.warnings());
	}

}
