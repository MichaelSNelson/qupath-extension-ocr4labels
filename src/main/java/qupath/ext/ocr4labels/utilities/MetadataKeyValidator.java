package qupath.ext.ocr4labels.utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates metadata keys to ensure they are safe and don't conflict
 * with QuPath's internal metadata or reserved keys.
 */
public class MetadataKeyValidator {

    private static final Logger logger = LoggerFactory.getLogger(MetadataKeyValidator.class);

    /**
     * Reserved metadata keys used by QuPath internally.
     * These should NEVER be overwritten by OCR.
     */
    private static final Set<String> RESERVED_KEYS = new HashSet<>(Arrays.asList(
            // Core QuPath metadata
            "name",
            "imageName",
            "imageId",
            "id",
            "path",
            "uri",
            "description",

            // Project-related
            "project",
            "projectName",
            "projectPath",

            // Server-related
            "server",
            "serverPath",
            "serverType",

            // Pixel calibration
            "pixelWidth",
            "pixelHeight",
            "pixelWidthMicrons",
            "pixelHeightMicrons",
            "pixelSizeMicrons",
            "timepoint",
            "zPosition",

            // Image properties
            "width",
            "height",
            "nChannels",
            "nZSlices",
            "nTimepoints",
            "bitDepth",
            "imageType",

            // Common metadata keys
            "dateCreated",
            "dateModified",
            "owner",
            "version"
    ));

    /**
     * Pattern for valid metadata keys.
     * Allows: letters, numbers, underscores, hyphens
     * Must start with a letter or underscore
     * No spaces or special characters
     */
    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_-]*$");

    /**
     * Maximum length for metadata keys.
     */
    private static final int MAX_KEY_LENGTH = 128;

    private MetadataKeyValidator() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates a metadata key for safety and compliance.
     *
     * @param key The metadata key to validate
     * @return ValidationResult containing success status and error message
     */
    public static ValidationResult validateKey(String key) {
        // Null or empty check
        if (key == null || key.trim().isEmpty()) {
            return ValidationResult.invalid("Metadata key cannot be null or empty");
        }

        String trimmedKey = key.trim();

        // Length check
        if (trimmedKey.length() > MAX_KEY_LENGTH) {
            return ValidationResult.invalid(
                    String.format("Metadata key exceeds maximum length of %d characters", MAX_KEY_LENGTH)
            );
        }

        // Reserved key check (case-insensitive for safety)
        if (isReservedKey(trimmedKey)) {
            return ValidationResult.invalid(
                    String.format("'%s' is a reserved key and cannot be used for OCR metadata", trimmedKey)
            );
        }

        // Pattern check
        if (!VALID_KEY_PATTERN.matcher(trimmedKey).matches()) {
            return ValidationResult.invalid(
                    String.format("'%s' is not a valid metadata key. " +
                                    "Keys must start with a letter or underscore and contain only " +
                                    "letters, numbers, underscores, and hyphens",
                            trimmedKey)
            );
        }

        return ValidationResult.valid();
    }

    /**
     * Checks if a key is reserved (case-insensitive).
     *
     * @param key The key to check
     * @return true if the key is reserved
     */
    public static boolean isReservedKey(String key) {
        if (key == null) return false;
        return RESERVED_KEYS.stream()
                .anyMatch(reserved -> reserved.equalsIgnoreCase(key));
    }

    /**
     * Sanitizes a proposed metadata key to make it valid.
     * Attempts to convert invalid characters to valid ones.
     *
     * @param proposedKey The proposed key
     * @return A sanitized version of the key, or null if cannot be sanitized
     */
    public static String sanitizeKey(String proposedKey) {
        if (proposedKey == null || proposedKey.trim().isEmpty()) {
            return null;
        }

        String sanitized = proposedKey.trim();

        // Replace spaces with underscores
        sanitized = sanitized.replace(' ', '_');

        // Replace common problematic characters
        sanitized = sanitized.replace('.', '_');
        sanitized = sanitized.replace('/', '_');
        sanitized = sanitized.replace('\\', '_');
        sanitized = sanitized.replace(':', '_');

        // Remove other invalid characters
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9_-]", "");

        // Ensure starts with letter or underscore
        if (!sanitized.isEmpty() && !Character.isLetter(sanitized.charAt(0)) &&
                sanitized.charAt(0) != '_') {
            sanitized = "ocr_" + sanitized;
        }

        // Handle empty result
        if (sanitized.isEmpty()) {
            return null;
        }

        // Truncate if too long
        if (sanitized.length() > MAX_KEY_LENGTH) {
            sanitized = sanitized.substring(0, MAX_KEY_LENGTH);
        }

        // Handle reserved keys
        if (isReservedKey(sanitized)) {
            sanitized = "ocr_" + sanitized;
        }

        // Validate the sanitized version
        if (validateKey(sanitized).isValid()) {
            return sanitized;
        }

        return null;
    }

    /**
     * Suggests valid alternatives for a reserved key.
     *
     * @param reservedKey The reserved key
     * @return A suggested alternative key
     */
    public static String suggestAlternative(String reservedKey) {
        if (reservedKey == null) return "ocr_field";

        // Try adding "ocr_" prefix
        String suggestion = "ocr_" + reservedKey;
        if (validateKey(suggestion).isValid()) {
            return suggestion;
        }

        // Try adding "label_" prefix
        suggestion = "label_" + reservedKey;
        if (validateKey(suggestion).isValid()) {
            return suggestion;
        }

        // Fallback
        return "custom_" + reservedKey;
    }

    /**
     * Result of metadata key validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public String toString() {
            return valid ? "Valid" : "Invalid: " + errorMessage;
        }
    }
}
