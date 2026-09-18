package ng.stapletrack.parser;

/**
 * The upload is not a workbook in the NBS Selected Food Prices Watch layout.
 * The message is written for the person uploading the file.
 */
public class NbsFormatException extends RuntimeException {

	public NbsFormatException(String message) {
		super(message);
	}

	public NbsFormatException(String message, Throwable cause) {
		super(message, cause);
	}

}
