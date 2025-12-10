package qupath.ext.ocr4labels.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Template for batch OCR processing.
 * Stores the mapping between detected field positions and metadata keys.
 * Can be saved/loaded to allow reuse across sessions.
 */
public class OCRTemplate {

    private static final Logger logger = LoggerFactory.getLogger(OCRTemplate.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String name;
    private String description;
    private List<FieldMapping> fieldMappings;
    private OCRConfiguration configuration;
    private long createdTimestamp;

    /**
     * Creates a new empty template.
     */
    public OCRTemplate() {
        this.fieldMappings = new ArrayList<>();
        this.createdTimestamp = System.currentTimeMillis();
    }

    /**
     * Creates a template with the given name.
     */
    public OCRTemplate(String name) {
        this();
        this.name = name;
    }

    /**
     * Represents a mapping from a detected field to a metadata key.
     */
    public static class FieldMapping {
        private int fieldIndex;
        private String metadataKey;
        private String exampleText;
        private boolean enabled;

        public FieldMapping() {
            this.enabled = true;
        }

        public FieldMapping(int fieldIndex, String metadataKey, String exampleText) {
            this.fieldIndex = fieldIndex;
            this.metadataKey = metadataKey;
            this.exampleText = exampleText;
            this.enabled = true;
        }

        public int getFieldIndex() {
            return fieldIndex;
        }

        public void setFieldIndex(int fieldIndex) {
            this.fieldIndex = fieldIndex;
        }

        public String getMetadataKey() {
            return metadataKey;
        }

        public void setMetadataKey(String metadataKey) {
            this.metadataKey = metadataKey;
        }

        public String getExampleText() {
            return exampleText;
        }

        public void setExampleText(String exampleText) {
            this.exampleText = exampleText;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public String toString() {
            return String.format("Field %d -> %s (example: '%s')",
                    fieldIndex, metadataKey, exampleText);
        }
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<FieldMapping> getFieldMappings() {
        return fieldMappings;
    }

    public void setFieldMappings(List<FieldMapping> fieldMappings) {
        this.fieldMappings = fieldMappings != null ? fieldMappings : new ArrayList<>();
    }

    public void addFieldMapping(FieldMapping mapping) {
        if (fieldMappings == null) {
            fieldMappings = new ArrayList<>();
        }
        fieldMappings.add(mapping);
    }

    public OCRConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(OCRConfiguration configuration) {
        this.configuration = configuration;
    }

    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    /**
     * Gets the number of enabled field mappings.
     */
    public int getEnabledMappingCount() {
        if (fieldMappings == null) return 0;
        return (int) fieldMappings.stream().filter(FieldMapping::isEnabled).count();
    }

    /**
     * Saves this template to a JSON file.
     *
     * @param file The file to save to
     * @throws IOException if saving fails
     */
    public void saveToFile(File file) throws IOException {
        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(this, writer);
            logger.info("Saved OCR template to: {}", file.getAbsolutePath());
        }
    }

    /**
     * Loads a template from a JSON file.
     *
     * @param file The file to load from
     * @return The loaded template
     * @throws IOException if loading fails
     */
    public static OCRTemplate loadFromFile(File file) throws IOException {
        try (Reader reader = new FileReader(file)) {
            OCRTemplate template = GSON.fromJson(reader, OCRTemplate.class);
            logger.info("Loaded OCR template from: {}", file.getAbsolutePath());
            return template;
        }
    }

    /**
     * Creates a template from the current OCR dialog field entries.
     *
     * @param name The template name
     * @param entries The field entries from the OCR dialog
     * @param config The OCR configuration used
     * @return A new template
     */
    public static OCRTemplate fromFieldEntries(String name,
            List<? extends FieldEntryProvider> entries,
            OCRConfiguration config) {
        OCRTemplate template = new OCRTemplate(name);
        template.setConfiguration(config);

        for (int i = 0; i < entries.size(); i++) {
            FieldEntryProvider entry = entries.get(i);
            String key = entry.getMetadataKey();
            String text = entry.getText();

            // Only include entries with valid keys
            if (key != null && !key.isEmpty()) {
                FieldMapping mapping = new FieldMapping(i, key, text);
                template.addFieldMapping(mapping);
            }
        }

        return template;
    }

    /**
     * Interface for field entry providers (allows different implementations).
     */
    public interface FieldEntryProvider {
        String getText();
        String getMetadataKey();
    }

    @Override
    public String toString() {
        return String.format("OCRTemplate[name='%s', mappings=%d]",
                name, fieldMappings != null ? fieldMappings.size() : 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OCRTemplate that = (OCRTemplate) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
