package qupath.ext.ocr4labels.model;

import java.util.Objects;

/**
 * Immutable configuration for OCR processing.
 * Use the {@link Builder} to create instances.
 */
public class OCRConfiguration {

    /**
     * Tesseract Page Segmentation Modes.
     */
    public enum PageSegMode {
        /** Orientation and script detection only */
        OSD_ONLY(0),
        /** Automatic page segmentation with OSD */
        AUTO_OSD(1),
        /** Automatic page segmentation, no OSD */
        AUTO(3),
        /** Assume a single column of text */
        SINGLE_COLUMN(4),
        /** Assume a single uniform block of vertically aligned text */
        SINGLE_BLOCK_VERT(5),
        /** Assume a single uniform block of text */
        SINGLE_BLOCK(6),
        /** Treat the image as a single text line */
        SINGLE_LINE(7),
        /** Treat the image as a single word */
        SINGLE_WORD(8),
        /** Treat the image as a single word in a circle */
        CIRCLE_WORD(9),
        /** Treat the image as a single character */
        SINGLE_CHAR(10),
        /** Find as much text as possible in no particular order */
        SPARSE_TEXT(11),
        /** Sparse text with OSD */
        SPARSE_TEXT_OSD(12),
        /** Raw line - treat the image as a single text line, no hacks */
        RAW_LINE(13);

        private final int value;

        PageSegMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Tesseract OCR Engine Modes.
     */
    public enum EngineMode {
        /** Legacy Tesseract only */
        LEGACY(0),
        /** Neural net LSTM only */
        LSTM_ONLY(1),
        /** Legacy + LSTM (most accurate but slower) */
        COMBINED(2),
        /** Default based on what's available */
        DEFAULT(3);

        private final int value;

        EngineMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    private final PageSegMode pageSegMode;
    private final EngineMode engineMode;
    private final String language;
    private final double minConfidence;
    private final boolean enablePreprocessing;
    private final boolean autoRotate;
    private final boolean enhanceContrast;
    private final boolean detectOrientation;

    private OCRConfiguration(Builder builder) {
        this.pageSegMode = builder.pageSegMode;
        this.engineMode = builder.engineMode;
        this.language = builder.language;
        this.minConfidence = builder.minConfidence;
        this.enablePreprocessing = builder.enablePreprocessing;
        this.autoRotate = builder.autoRotate;
        this.enhanceContrast = builder.enhanceContrast;
        this.detectOrientation = builder.detectOrientation;
    }

    /**
     * Creates a default configuration optimized for slide labels.
     */
    public static OCRConfiguration createDefault() {
        return builder().build();
    }

    /**
     * Creates a new builder with default values.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder pre-populated with this configuration's values.
     */
    public Builder toBuilder() {
        return new Builder()
                .pageSegMode(pageSegMode)
                .engineMode(engineMode)
                .language(language)
                .minConfidence(minConfidence)
                .enablePreprocessing(enablePreprocessing)
                .autoRotate(autoRotate)
                .enhanceContrast(enhanceContrast)
                .detectOrientation(detectOrientation);
    }

    public PageSegMode getPageSegMode() {
        return pageSegMode;
    }

    public EngineMode getEngineMode() {
        return engineMode;
    }

    public String getLanguage() {
        return language;
    }

    public double getMinConfidence() {
        return minConfidence;
    }

    public boolean isEnablePreprocessing() {
        return enablePreprocessing;
    }

    public boolean isAutoRotate() {
        return autoRotate;
    }

    public boolean isEnhanceContrast() {
        return enhanceContrast;
    }

    public boolean isDetectOrientation() {
        return detectOrientation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OCRConfiguration that = (OCRConfiguration) o;
        return Double.compare(that.minConfidence, minConfidence) == 0 &&
               enablePreprocessing == that.enablePreprocessing &&
               autoRotate == that.autoRotate &&
               enhanceContrast == that.enhanceContrast &&
               detectOrientation == that.detectOrientation &&
               pageSegMode == that.pageSegMode &&
               engineMode == that.engineMode &&
               Objects.equals(language, that.language);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageSegMode, engineMode, language, minConfidence,
                enablePreprocessing, autoRotate, enhanceContrast, detectOrientation);
    }

    @Override
    public String toString() {
        return String.format("OCRConfiguration[psm=%s, oem=%s, lang=%s, minConf=%.0f%%, " +
                        "preprocess=%b, autoRotate=%b, contrast=%b, detectOrient=%b]",
                pageSegMode, engineMode, language, minConfidence * 100,
                enablePreprocessing, autoRotate, enhanceContrast, detectOrientation);
    }

    /**
     * Builder for creating {@link OCRConfiguration} instances.
     */
    public static class Builder {
        private PageSegMode pageSegMode = PageSegMode.AUTO;
        private EngineMode engineMode = EngineMode.LSTM_ONLY;
        private String language = "eng";
        private double minConfidence = 0.5;
        private boolean enablePreprocessing = true;
        private boolean autoRotate = true;
        private boolean enhanceContrast = true;
        private boolean detectOrientation = true;

        public Builder pageSegMode(PageSegMode mode) {
            this.pageSegMode = mode != null ? mode : PageSegMode.AUTO;
            return this;
        }

        public Builder engineMode(EngineMode mode) {
            this.engineMode = mode != null ? mode : EngineMode.LSTM_ONLY;
            return this;
        }

        public Builder language(String language) {
            this.language = language != null && !language.isEmpty() ? language : "eng";
            return this;
        }

        public Builder minConfidence(double confidence) {
            this.minConfidence = Math.max(0.0, Math.min(1.0, confidence));
            return this;
        }

        public Builder enablePreprocessing(boolean enable) {
            this.enablePreprocessing = enable;
            return this;
        }

        public Builder autoRotate(boolean enable) {
            this.autoRotate = enable;
            return this;
        }

        public Builder enhanceContrast(boolean enable) {
            this.enhanceContrast = enable;
            return this;
        }

        public Builder detectOrientation(boolean enable) {
            this.detectOrientation = enable;
            return this;
        }

        public OCRConfiguration build() {
            return new OCRConfiguration(this);
        }
    }
}
