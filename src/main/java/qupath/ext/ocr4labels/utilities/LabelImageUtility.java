package qupath.ext.ocr4labels.utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;

import java.awt.image.BufferedImage;
import java.util.Collection;

/**
 * Utility class for retrieving and working with label images from whole slide images.
 * Handles different naming conventions used by various slide scanner vendors.
 */
public class LabelImageUtility {

    private static final Logger logger = LoggerFactory.getLogger(LabelImageUtility.class);

    /**
     * Standard label image names across different vendors.
     * Order matters - checked in sequence.
     */
    private static final String[] LABEL_IMAGE_NAMES = {
            "label",        // Standard Bio-Formats convention
            "Label",        // Case variation
            "LABEL",        // All caps variation
    };

    /**
     * Keywords to search for in associated image names.
     */
    private static final String[] LABEL_KEYWORDS = {
            "label",
            "barcode"
    };

    private LabelImageUtility() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks if a label image is available for the given ImageData.
     * Does not retrieve the image, only checks for existence.
     *
     * @param imageData The ImageData to check
     * @return true if a label image exists, false otherwise
     */
    public static boolean isLabelImageAvailable(ImageData<?> imageData) {
        if (imageData == null) {
            return false;
        }

        try {
            ImageServer<?> server = imageData.getServer();
            if (server == null) {
                return false;
            }

            Collection<String> associatedImages = server.getAssociatedImageList();
            if (associatedImages == null || associatedImages.isEmpty()) {
                return false;
            }

            // Check for standard label names
            for (String labelName : LABEL_IMAGE_NAMES) {
                if (associatedImages.contains(labelName)) {
                    return true;
                }
            }

            // Check for names containing label keywords (case-insensitive)
            String lowerNames = associatedImages.toString().toLowerCase();
            for (String keyword : LABEL_KEYWORDS) {
                if (lowerNames.contains(keyword)) {
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            logger.error("Error checking for label image availability", e);
            return false;
        }
    }

    /**
     * Retrieves the label image from the given ImageData.
     * Tries multiple naming conventions and returns the first match found.
     *
     * @param imageData The ImageData to retrieve the label from
     * @return The label image as BufferedImage, or null if not available
     */
    public static BufferedImage retrieveLabelImage(ImageData<?> imageData) {
        if (imageData == null) {
            logger.warn("Cannot retrieve label image from null ImageData");
            return null;
        }

        try {
            ImageServer<?> server = imageData.getServer();
            if (server == null) {
                logger.warn("ImageServer is null");
                return null;
            }

            Collection<String> associatedImages = server.getAssociatedImageList();
            if (associatedImages == null || associatedImages.isEmpty()) {
                logger.debug("No associated images available for: {}", server.getPath());
                return null;
            }

            logger.debug("Available associated images: {}", associatedImages);

            // Try standard label names in order
            for (String labelName : LABEL_IMAGE_NAMES) {
                if (associatedImages.contains(labelName)) {
                    logger.info("Found label image with name: {}", labelName);
                    BufferedImage img = retrieveImageByName(server, labelName);
                    if (img != null) {
                        return img;
                    }
                }
            }

            // Try finding any image containing label keywords (case-insensitive)
            for (String imageName : associatedImages) {
                String lowerName = imageName.toLowerCase();
                for (String keyword : LABEL_KEYWORDS) {
                    if (lowerName.contains(keyword)) {
                        logger.info("Found label image by keyword '{}': {}", keyword, imageName);
                        BufferedImage img = retrieveImageByName(server, imageName);
                        if (img != null) {
                            return img;
                        }
                    }
                }
            }

            logger.warn("No label image found for: {}", server.getPath());
            return null;

        } catch (Exception e) {
            logger.error("Error retrieving label image", e);
            return null;
        }
    }

    /**
     * Retrieves a specific associated image by name.
     *
     * @param server    The ImageServer
     * @param imageName The name of the associated image
     * @return The BufferedImage, or null if retrieval fails
     */
    private static BufferedImage retrieveImageByName(ImageServer<?> server, String imageName) {
        try {
            Object image = server.getAssociatedImage(imageName);
            if (image instanceof BufferedImage) {
                BufferedImage labelImage = (BufferedImage) image;
                logger.debug("Retrieved label image: {}x{} pixels",
                        labelImage.getWidth(), labelImage.getHeight());
                return labelImage;
            } else {
                logger.warn("Associated image '{}' is not a BufferedImage: {}",
                        imageName, image != null ? image.getClass() : "null");
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve associated image: {}", imageName, e);
            return null;
        }
    }

    /**
     * Gets the name of the label image if available.
     *
     * @param imageData The ImageData to check
     * @return The label image name, or null if not found
     */
    public static String getLabelImageName(ImageData<?> imageData) {
        if (imageData == null) {
            return null;
        }

        try {
            ImageServer<?> server = imageData.getServer();
            if (server == null) {
                return null;
            }

            Collection<String> associatedImages = server.getAssociatedImageList();
            if (associatedImages == null) {
                return null;
            }

            // Try standard names first
            for (String labelName : LABEL_IMAGE_NAMES) {
                if (associatedImages.contains(labelName)) {
                    return labelName;
                }
            }

            // Try finding by keyword
            for (String imageName : associatedImages) {
                String lowerName = imageName.toLowerCase();
                for (String keyword : LABEL_KEYWORDS) {
                    if (lowerName.contains(keyword)) {
                        return imageName;
                    }
                }
            }

            return null;

        } catch (Exception e) {
            logger.error("Error getting label image name", e);
            return null;
        }
    }

    /**
     * Gets a list of all associated image names for debugging.
     *
     * @param imageData The ImageData to inspect
     * @return Collection of associated image names, or empty collection if none
     */
    public static Collection<String> getAssociatedImageNames(ImageData<?> imageData) {
        if (imageData == null) {
            return java.util.Collections.emptyList();
        }

        try {
            ImageServer<?> server = imageData.getServer();
            if (server == null) {
                return java.util.Collections.emptyList();
            }

            Collection<String> names = server.getAssociatedImageList();
            return names != null ? names : java.util.Collections.emptyList();

        } catch (Exception e) {
            logger.error("Error getting associated image names", e);
            return java.util.Collections.emptyList();
        }
    }
}
