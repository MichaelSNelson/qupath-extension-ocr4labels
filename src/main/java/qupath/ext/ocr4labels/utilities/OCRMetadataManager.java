package qupath.ext.ocr4labels.utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages OCR-derived metadata for QuPath project images.
 * Handles reading, writing, and validation of metadata fields.
 */
public class OCRMetadataManager {

    private static final Logger logger = LoggerFactory.getLogger(OCRMetadataManager.class);

    /**
     * Default prefix for OCR-derived metadata keys.
     */
    public static final String DEFAULT_PREFIX = "OCR_";

    private OCRMetadataManager() {
        // Utility class - prevent instantiation
    }

    /**
     * Sets a metadata value for an image entry after validation.
     *
     * @param entry   The image entry
     * @param key     The metadata key (will be validated)
     * @param value   The value to set
     * @param project The project (for syncing changes), can be null
     * @return true if successful, false if validation failed
     */
    public static boolean setMetadata(ProjectImageEntry<?> entry, String key, String value,
                                      Project<?> project) {
        if (entry == null) {
            logger.error("Cannot set metadata on null entry");
            return false;
        }

        // Validate the key
        MetadataKeyValidator.ValidationResult validation = MetadataKeyValidator.validateKey(key);
        if (!validation.isValid()) {
            logger.error("Invalid metadata key '{}': {}", key, validation.getErrorMessage());
            return false;
        }

        // Set the metadata
        Map<String, String> metadata = entry.getMetadata();
        String oldValue = metadata.get(key);
        metadata.put(key, value);

        logger.debug("Set metadata for {}: {} = {} (was: {})",
                entry.getImageName(), key, value, oldValue);

        // Sync changes if project provided
        if (project != null) {
            try {
                project.syncChanges();
                logger.debug("Synced project changes");
            } catch (IOException e) {
                logger.error("Failed to sync project changes", e);
                return false;
            }
        }

        return true;
    }

    /**
     * Sets multiple metadata values at once (batch operation).
     *
     * @param entry       The image entry
     * @param metadataMap Map of key-value pairs to set
     * @param project     The project (for syncing changes), can be null
     * @return Number of successfully set metadata fields
     */
    public static int setMetadataBatch(ProjectImageEntry<?> entry,
                                       Map<String, String> metadataMap,
                                       Project<?> project) {
        if (entry == null || metadataMap == null || metadataMap.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        Map<String, String> metadata = entry.getMetadata();

        for (Map.Entry<String, String> kvp : metadataMap.entrySet()) {
            String key = kvp.getKey();
            String value = kvp.getValue();

            // Validate each key
            MetadataKeyValidator.ValidationResult validation = MetadataKeyValidator.validateKey(key);
            if (!validation.isValid()) {
                logger.warn("Skipping invalid metadata key '{}': {}", key, validation.getErrorMessage());
                continue;
            }

            metadata.put(key, value);
            successCount++;
        }

        logger.info("Set {} metadata fields for {}", successCount, entry.getImageName());

        // Sync once after all changes
        if (project != null && successCount > 0) {
            try {
                project.syncChanges();
            } catch (IOException e) {
                logger.error("Failed to sync project changes", e);
            }
        }

        return successCount;
    }

    /**
     * Gets a metadata value for an image entry.
     *
     * @param entry The image entry
     * @param key   The metadata key
     * @return The value, or null if not found
     */
    public static String getMetadata(ProjectImageEntry<?> entry, String key) {
        if (entry == null || key == null) {
            return null;
        }

        return entry.getMetadata().get(key);
    }

    /**
     * Removes a metadata key from an entry.
     *
     * @param entry   The image entry
     * @param key     The key to remove
     * @param project The project (for syncing changes), can be null
     * @return true if the key was removed, false if it didn't exist
     */
    public static boolean removeMetadata(ProjectImageEntry<?> entry, String key,
                                         Project<?> project) {
        if (entry == null || key == null) {
            return false;
        }

        String removed = entry.getMetadata().remove(key);
        if (removed != null) {
            logger.debug("Removed metadata from {}: {}", entry.getImageName(), key);

            if (project != null) {
                try {
                    project.syncChanges();
                } catch (IOException e) {
                    logger.error("Failed to sync project changes", e);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Checks if an entry has a specific metadata key.
     *
     * @param entry The image entry
     * @param key   The key to check
     * @return true if the key exists
     */
    public static boolean hasMetadata(ProjectImageEntry<?> entry, String key) {
        if (entry == null || key == null) {
            return false;
        }

        return entry.getMetadata().containsKey(key);
    }

    /**
     * Gets all OCR-derived metadata for an entry.
     * Returns only metadata keys that start with the OCR prefix.
     *
     * @param entry  The image entry
     * @param prefix The prefix to filter by (e.g., "OCR_")
     * @return Map of OCR metadata (may be empty)
     */
    public static Map<String, String> getMetadataByPrefix(ProjectImageEntry<?> entry, String prefix) {
        if (entry == null || prefix == null) {
            return Collections.emptyMap();
        }

        return entry.getMetadata().entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1,
                        LinkedHashMap::new
                ));
    }

    /**
     * Clears all metadata with a specific prefix from an entry.
     *
     * @param entry   The image entry
     * @param prefix  The prefix to match (e.g., "OCR_")
     * @param project The project (for syncing changes), can be null
     * @return Number of metadata fields removed
     */
    public static int clearMetadataByPrefix(ProjectImageEntry<?> entry, String prefix,
                                            Project<?> project) {
        if (entry == null || prefix == null) {
            return 0;
        }

        Map<String, String> metadata = entry.getMetadata();
        int removeCount = 0;

        // Collect keys to remove (avoid concurrent modification)
        var keysToRemove = metadata.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .collect(Collectors.toList());

        for (String key : keysToRemove) {
            metadata.remove(key);
            removeCount++;
        }

        if (removeCount > 0) {
            logger.info("Removed {} metadata fields with prefix '{}' from {}",
                    removeCount, prefix, entry.getImageName());

            if (project != null) {
                try {
                    project.syncChanges();
                } catch (IOException e) {
                    logger.error("Failed to sync project changes", e);
                }
            }
        }

        return removeCount;
    }

    /**
     * Creates a metadata key with the default OCR prefix.
     *
     * @param baseName The base name for the key
     * @return The prefixed key, or null if the resulting key is invalid
     */
    public static String createOCRKey(String baseName) {
        if (baseName == null || baseName.trim().isEmpty()) {
            return null;
        }

        String key = DEFAULT_PREFIX + MetadataKeyValidator.sanitizeKey(baseName);

        if (key.equals(DEFAULT_PREFIX)) {
            return null;
        }

        return MetadataKeyValidator.validateKey(key).isValid() ? key : null;
    }
}
