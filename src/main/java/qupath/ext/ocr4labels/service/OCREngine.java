package qupath.ext.ocr4labels.service;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.ocr4labels.model.BoundingBox;
import qupath.ext.ocr4labels.model.OCRConfiguration;
import qupath.ext.ocr4labels.model.OCRResult;
import qupath.ext.ocr4labels.model.TextBlock;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * OCR engine that wraps Tess4J for text detection on label images.
 * Handles preprocessing, orientation detection, and text extraction.
 */
public class OCREngine {

    private static final Logger logger = LoggerFactory.getLogger(OCREngine.class);

    private final ITesseract tesseract;
    private boolean initialized = false;
    private String tessdataPath;

    /**
     * Creates a new OCR engine instance.
     */
    public OCREngine() {
        this.tesseract = new Tesseract();
    }

    /**
     * Initializes the OCR engine with the specified configuration.
     * Must be called before processing images.
     *
     * @param tessdataPath Path to the tessdata directory containing language files
     * @param language     Language code (e.g., "eng" for English)
     * @throws OCRException if initialization fails
     */
    public void initialize(String tessdataPath, String language) throws OCRException {
        try {
            this.tessdataPath = tessdataPath;

            // Verify tessdata path exists
            File tessdataDir = new File(tessdataPath);
            if (!tessdataDir.exists() || !tessdataDir.isDirectory()) {
                throw new OCRException("Tessdata directory not found: " + tessdataPath);
            }

            // Check for language file
            File langFile = new File(tessdataDir, language + ".traineddata");
            if (!langFile.exists()) {
                throw new OCRException("Language file not found: " + langFile.getAbsolutePath() +
                        ". Please download " + language + ".traineddata from " +
                        "https://github.com/tesseract-ocr/tessdata");
            }

            tesseract.setDatapath(tessdataPath);
            tesseract.setLanguage(language);

            // Default settings optimized for label images
            tesseract.setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY);
            tesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO);

            initialized = true;
            logger.info("OCR engine initialized with tessdata: {}, language: {}", tessdataPath, language);

        } catch (Exception e) {
            initialized = false;
            throw new OCRException("Failed to initialize OCR engine: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if the engine is initialized and ready to process images.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Processes an image and extracts text blocks.
     *
     * @param image  The image to process
     * @param config OCR configuration settings
     * @return OCR result containing detected text blocks
     * @throws OCRException if processing fails
     */
    public OCRResult processImage(BufferedImage image, OCRConfiguration config) throws OCRException {
        if (!initialized) {
            throw new OCRException("OCR engine not initialized. Call initialize() first.");
        }

        if (image == null) {
            throw new OCRException("Image cannot be null");
        }

        long startTime = System.currentTimeMillis();

        try {
            // Apply configuration
            applyConfiguration(config);

            // Preprocess image if enabled
            BufferedImage processedImage = image;
            int detectedOrientation = 0;

            if (config.isEnablePreprocessing()) {
                processedImage = preprocessImage(image, config);
            }

            // Detect and correct orientation if enabled
            if (config.isDetectOrientation()) {
                OrientationResult orientation = detectOrientation(processedImage);
                detectedOrientation = orientation.degrees;
                if (orientation.degrees != 0 && config.isAutoRotate()) {
                    processedImage = rotateImage(processedImage, orientation.degrees);
                    logger.info("Image rotated by {} degrees", orientation.degrees);
                }
            }

            // Extract text blocks
            List<TextBlock> textBlocks = extractTextBlocks(processedImage, config);

            long processingTime = System.currentTimeMillis() - startTime;

            OCRResult result = new OCRResult(
                    textBlocks,
                    processingTime,
                    image.getWidth(),
                    image.getHeight(),
                    detectedOrientation
            );

            logger.info("OCR complete: {} blocks detected in {}ms", textBlocks.size(), processingTime);
            return result;

        } catch (TesseractException e) {
            throw new OCRException("Tesseract processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Performs simple OCR and returns only the full text.
     *
     * @param image  The image to process
     * @param config OCR configuration settings
     * @return Extracted text as a string
     * @throws OCRException if processing fails
     */
    public String extractText(BufferedImage image, OCRConfiguration config) throws OCRException {
        if (!initialized) {
            throw new OCRException("OCR engine not initialized. Call initialize() first.");
        }

        try {
            applyConfiguration(config);

            BufferedImage processedImage = image;
            if (config.isEnablePreprocessing()) {
                processedImage = preprocessImage(image, config);
            }

            if (config.isDetectOrientation() && config.isAutoRotate()) {
                OrientationResult orientation = detectOrientation(processedImage);
                if (orientation.degrees != 0) {
                    processedImage = rotateImage(processedImage, orientation.degrees);
                }
            }

            return tesseract.doOCR(processedImage).trim();

        } catch (TesseractException e) {
            throw new OCRException("Text extraction failed: " + e.getMessage(), e);
        }
    }

    /**
     * Applies configuration settings to the Tesseract instance.
     */
    private void applyConfiguration(OCRConfiguration config) {
        tesseract.setPageSegMode(config.getPageSegMode().getValue());
        tesseract.setOcrEngineMode(config.getEngineMode().getValue());

        if (config.getLanguage() != null && !config.getLanguage().isEmpty()) {
            tesseract.setLanguage(config.getLanguage());
        }
    }

    /**
     * Preprocesses the image to improve OCR accuracy.
     */
    private BufferedImage preprocessImage(BufferedImage image, OCRConfiguration config) {
        BufferedImage result = image;

        // Convert to grayscale if needed
        if (result.getType() != BufferedImage.TYPE_BYTE_GRAY) {
            BufferedImage grayImage = new BufferedImage(
                    result.getWidth(), result.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = grayImage.createGraphics();
            g.drawImage(result, 0, 0, null);
            g.dispose();
            result = grayImage;
        }

        // Enhance contrast if enabled
        if (config.isEnhanceContrast()) {
            result = enhanceContrast(result);
        }

        return result;
    }

    /**
     * Enhances image contrast using a simple rescale operation.
     */
    private BufferedImage enhanceContrast(BufferedImage image) {
        // Scale factor of 1.2 increases contrast, offset of 0 maintains brightness
        RescaleOp rescaleOp = new RescaleOp(1.2f, 0, null);
        return rescaleOp.filter(image, null);
    }

    /**
     * Detects the orientation of text in the image.
     */
    private OrientationResult detectOrientation(BufferedImage image) {
        try {
            // Use Tesseract's OSD mode to detect orientation
            int originalPsm = tesseract.getPageSegMode();
            tesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_OSD_ONLY);

            String osdResult = tesseract.doOCR(image);

            // Restore original PSM
            tesseract.setPageSegMode(originalPsm);

            // Parse orientation from OSD output
            // Output contains lines like "Orientation in degrees: 90"
            int orientation = 0;
            for (String line : osdResult.split("\n")) {
                if (line.contains("Orientation in degrees:")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        orientation = Integer.parseInt(parts[1].trim());
                    }
                    break;
                }
            }

            logger.debug("Detected orientation: {} degrees", orientation);
            return new OrientationResult(orientation, 1.0f);

        } catch (Exception e) {
            logger.warn("Orientation detection failed, assuming upright: {}", e.getMessage());
            return new OrientationResult(0, 0.0f);
        }
    }

    /**
     * Rotates an image by the specified degrees (must be 0, 90, 180, or 270).
     */
    private BufferedImage rotateImage(BufferedImage image, int degrees) {
        // Normalize to 0, 90, 180, 270
        degrees = ((degrees % 360) + 360) % 360;

        if (degrees == 0) {
            return image;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // For 90 or 270 degree rotation, swap width and height
        int newWidth = (degrees == 90 || degrees == 270) ? height : width;
        int newHeight = (degrees == 90 || degrees == 270) ? width : height;

        BufferedImage rotated = new BufferedImage(newWidth, newHeight, image.getType());
        Graphics2D g2d = rotated.createGraphics();

        // Set up the rotation transform
        AffineTransform transform = new AffineTransform();

        switch (degrees) {
            case 90:
                transform.translate(newWidth, 0);
                transform.rotate(Math.PI / 2);
                break;
            case 180:
                transform.translate(newWidth, newHeight);
                transform.rotate(Math.PI);
                break;
            case 270:
                transform.translate(0, newHeight);
                transform.rotate(-Math.PI / 2);
                break;
        }

        g2d.setTransform(transform);
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        return rotated;
    }

    /**
     * Extracts text blocks with bounding boxes from the image.
     */
    private List<TextBlock> extractTextBlocks(BufferedImage image, OCRConfiguration config)
            throws TesseractException {

        List<TextBlock> blocks = new ArrayList<>();

        // Get words with bounding boxes
        List<Word> words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);

        for (Word word : words) {
            String text = word.getText();
            if (text == null || text.trim().isEmpty()) {
                continue;
            }

            Rectangle rect = word.getBoundingBox();
            float confidence = word.getConfidence() / 100.0f; // Convert from 0-100 to 0-1

            // Filter by minimum confidence
            if (confidence < config.getMinConfidence()) {
                logger.debug("Skipping low-confidence word: '{}' ({}%)", text, confidence * 100);
                continue;
            }

            BoundingBox bbox = new BoundingBox(rect.x, rect.y, rect.width, rect.height);
            TextBlock block = TextBlock.word(text.trim(), bbox, confidence);
            blocks.add(block);
        }

        // Also try to get lines for better grouping
        try {
            List<Word> lines = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE);
            for (Word line : lines) {
                String text = line.getText();
                if (text == null || text.trim().isEmpty()) {
                    continue;
                }

                Rectangle rect = line.getBoundingBox();
                float confidence = line.getConfidence() / 100.0f;

                if (confidence >= config.getMinConfidence()) {
                    BoundingBox bbox = new BoundingBox(rect.x, rect.y, rect.width, rect.height);
                    TextBlock block = TextBlock.line(text.trim(), bbox, confidence);
                    blocks.add(block);
                }
            }
        } catch (Exception e) {
            logger.debug("Could not extract lines: {}", e.getMessage());
        }

        return blocks;
    }

    /**
     * Releases resources held by this engine.
     */
    public void dispose() {
        initialized = false;
        logger.debug("OCR engine disposed");
    }

    /**
     * Gets the path to tessdata directory.
     */
    public String getTessdataPath() {
        return tessdataPath;
    }

    /**
     * Result of orientation detection.
     */
    private static class OrientationResult {
        final int degrees;
        final float confidence;

        OrientationResult(int degrees, float confidence) {
            this.degrees = degrees;
            this.confidence = confidence;
        }
    }

    /**
     * Exception for OCR-related errors.
     */
    public static class OCRException extends Exception {
        public OCRException(String message) {
            super(message);
        }

        public OCRException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
