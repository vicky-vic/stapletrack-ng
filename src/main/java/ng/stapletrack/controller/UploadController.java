package ng.stapletrack.controller;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ng.stapletrack.parser.NbsFormatException;
import ng.stapletrack.service.ImportResult;
import ng.stapletrack.service.ImportService;

@Controller
public class UploadController {

	private static final Logger log = LoggerFactory.getLogger(UploadController.class);
	private static final DateTimeFormatter RELEASE = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

	private final ImportService importService;

	public UploadController(ImportService importService) {
		this.importService = importService;
	}

	@GetMapping("/upload")
	public String form(Model model) {
		model.addAttribute("currentPage", "upload");
		return "upload";
	}

	/**
	 * Imports the file and redirects back (POST-redirect-GET). flashType is
	 * "success", "info" or "error", matching the alert-st-* classes.
	 */
	@PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public String upload(@RequestParam("file") MultipartFile file, RedirectAttributes redirect) {
		if (file.isEmpty()) {
			redirect.addFlashAttribute("flashType", "info");
			redirect.addFlashAttribute("flashMessage", "Choose a file before clicking Upload.");
			return "redirect:/upload";
		}
		try {
			ImportResult result = importService.importFile(file);
			redirect.addFlashAttribute("flashType", "success");
			redirect.addFlashAttribute("flashMessage", summary(file.getOriginalFilename(), result));
			redirect.addFlashAttribute("flashWarnings", result.warnings());
		}
		catch (NbsFormatException | IllegalArgumentException | UnsupportedOperationException e) {
			flashError(redirect, file, e.getMessage());
		}
		catch (IOException e) {
			log.warn("Could not read upload {}", file.getOriginalFilename(), e);
			flashError(redirect, file, "The file could not be read. Try uploading it again.");
		}
		catch (RuntimeException e) {
			log.error("Import failed for {}", file.getOriginalFilename(), e);
			flashError(redirect, file, "Import failed and nothing was saved. See the server log for details.");
		}
		return "redirect:/upload";
	}

	private static String summary(String filename, ImportResult r) {
		return "%s (%s release): imported %d national prices (%d new, %d updated), %d zonal prices (%d new, %d updated), %d state extremes (%d new, %d updated)."
				.formatted(filename, RELEASE.format(r.releaseMonth()),
						r.nationalTotal(), r.nationalInserted(), r.nationalUpdated(),
						r.zonalTotal(), r.zonalInserted(), r.zonalUpdated(),
						r.extremesTotal(), r.extremesInserted(), r.extremesUpdated());
	}

	private static void flashError(RedirectAttributes redirect, MultipartFile file, String message) {
		redirect.addFlashAttribute("flashType", "error");
		redirect.addFlashAttribute("flashMessage", file.getOriginalFilename() + ": " + message);
	}

}
