package qupath.ext.ocr4labels.model;

import java.util.Objects;

/**
 * Represents a detected text region from OCR processing.
 * Contains the recognized text, its location, and confidence score.
 */
public class TextBlock {

    /**
     * Type of text block based on Tesseract's segmentation.
     */
    public enum BlockType {
        WORD,
        LINE,
        PARAGRAPH,
        BLOCK
    }

    private final String text;
    private final BoundingBox boundingBox;
    private final float confidence;
    private final BlockType type;

    /**
     * Creates a new text block.
     *
     * @param text        The recognized text
     * @param boundingBox The location of the text in the image
     * @param confidence  Confidence score (0.0 to 1.0)
     * @param type        The segmentation level
     */
    public TextBlock(String text, BoundingBox boundingBox, float confidence, BlockType type) {
        this.text = text != null ? text.trim() : "";
        this.boundingBox = Objects.requireNonNull(boundingBox, "BoundingBox cannot be null");
        this.confidence = Math.max(0.0f, Math.min(1.0f, confidence)); // Clamp to [0, 1]
        this.type = type != null ? type : BlockType.WORD;
    }

    /**
     * Creates a word-level text block.
     */
    public static TextBlock word(String text, BoundingBox boundingBox, float confidence) {
        return new TextBlock(text, boundingBox, confidence, BlockType.WORD);
    }

    /**
     * Creates a line-level text block.
     */
    public static TextBlock line(String text, BoundingBox boundingBox, float confidence) {
        return new TextBlock(text, boundingBox, confidence, BlockType.LINE);
    }

    public String getText() {
        return text;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    /**
     * Gets the confidence score as a value between 0.0 and 1.0.
     */
    public float getConfidence() {
        return confidence;
    }

    /**
     * Gets the confidence score as a percentage (0-100).
     */
    public int getConfidencePercent() {
        return Math.round(confidence * 100);
    }

    public BlockType getType() {
        return type;
    }

    /**
     * Checks if this text block meets the minimum confidence threshold.
     *
     * @param threshold Minimum confidence (0.0 to 1.0)
     * @return true if confidence >= threshold
     */
    public boolean meetsConfidenceThreshold(double threshold) {
        return confidence >= threshold;
    }

    /**
     * Checks if the text is empty or whitespace only.
     */
    public boolean isEmpty() {
        return text.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextBlock textBlock = (TextBlock) o;
        return Float.compare(textBlock.confidence, confidence) == 0 &&
               Objects.equals(text, textBlock.text) &&
               Objects.equals(boundingBox, textBlock.boundingBox) &&
               type == textBlock.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, boundingBox, confidence, type);
    }

    @Override
    public String toString() {
        return String.format("TextBlock[text='%s', confidence=%.0f%%, type=%s, %s]",
                text.length() > 20 ? text.substring(0, 20) + "..." : text,
                confidence * 100,
                type,
                boundingBox);
    }
}
