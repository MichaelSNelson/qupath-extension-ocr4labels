package qupath.ext.ocr4labels.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.ocr4labels.model.OCRConfiguration;
import qupath.ext.ocr4labels.preferences.OCRPreferences;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;

import java.io.File;
import java.util.ResourceBundle;

/**
 * Dialog for configuring OCR settings.
 */
public class OCRSettingsDialog {

    private static final Logger logger = LoggerFactory.getLogger(OCRSettingsDialog.class);

    private static final ResourceBundle resources =
            ResourceBundle.getBundle("qupath.ext.ocr4labels.ui.strings");

    private OCRSettingsDialog() {
        // Utility class
    }

    /**
     * Shows the OCR settings dialog.
     *
     * @param qupath The QuPath GUI instance
     */
    public static void show(QuPathGUI qupath) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(resources.getString("dialog.settings.title"));
        dialog.initOwner(qupath.getStage());

        // Create content
        VBox content = createContent();
        dialog.getDialogPane().setContent(content);

        // Add buttons
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Show dialog
        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                logger.info("OCR settings saved");
            }
        });
    }

    private static VBox createContent() {
        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(15));
        vbox.setPrefWidth(500);

        // === Tessdata Path Section ===
        TitledPane tessdataPane = new TitledPane();
        tessdataPane.setText("Tesseract Data");
        tessdataPane.setCollapsible(false);

        GridPane tessdataGrid = new GridPane();
        tessdataGrid.setHgap(10);
        tessdataGrid.setVgap(10);
        tessdataGrid.setPadding(new Insets(10));

        Label pathLabel = new Label("Tessdata Path:");
        TextField pathField = new TextField(OCRPreferences.getTessdataPath());
        pathField.setPrefWidth(300);
        pathField.textProperty().addListener((obs, old, newVal) ->
                OCRPreferences.setTessdataPath(newVal));

        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Tessdata Directory");
            String currentPath = pathField.getText();
            if (currentPath != null && !currentPath.isEmpty()) {
                File current = new File(currentPath);
                if (current.exists()) {
                    chooser.setInitialDirectory(current);
                }
            }
            File selected = chooser.showDialog(browseButton.getScene().getWindow());
            if (selected != null) {
                pathField.setText(selected.getAbsolutePath());
            }
        });

        Label languageLabel = new Label("Language:");
        TextField languageField = new TextField(OCRPreferences.getLanguage());
        languageField.setPrefWidth(100);
        languageField.setPromptText("eng");
        languageField.textProperty().addListener((obs, old, newVal) ->
                OCRPreferences.setLanguage(newVal));

        Hyperlink downloadLink = new Hyperlink("Download language files");
        downloadLink.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(
                        new java.net.URI("https://github.com/tesseract-ocr/tessdata"));
            } catch (Exception ex) {
                logger.error("Failed to open browser", ex);
            }
        });

        tessdataGrid.add(pathLabel, 0, 0);
        tessdataGrid.add(pathField, 1, 0);
        tessdataGrid.add(browseButton, 2, 0);
        tessdataGrid.add(languageLabel, 0, 1);
        tessdataGrid.add(languageField, 1, 1);
        tessdataGrid.add(downloadLink, 1, 2);

        GridPane.setHgrow(pathField, Priority.ALWAYS);
        tessdataPane.setContent(tessdataGrid);

        // === OCR Settings Section ===
        TitledPane ocrPane = new TitledPane();
        ocrPane.setText("OCR Settings");
        ocrPane.setCollapsible(false);

        GridPane ocrGrid = new GridPane();
        ocrGrid.setHgap(10);
        ocrGrid.setVgap(10);
        ocrGrid.setPadding(new Insets(10));

        // Page Segmentation Mode
        Label psmLabel = new Label("Page Segmentation:");
        ComboBox<String> psmCombo = new ComboBox<>();
        psmCombo.getItems().addAll(
                "Auto (recommended)",
                "Single block",
                "Single line",
                "Single word",
                "Sparse text"
        );
        int currentPsm = OCRPreferences.getPageSegMode();
        psmCombo.getSelectionModel().select(psmToIndex(currentPsm));
        psmCombo.setOnAction(e -> {
            int idx = psmCombo.getSelectionModel().getSelectedIndex();
            OCRPreferences.setPageSegMode(indexToPsm(idx));
        });

        // Minimum Confidence
        Label confLabel = new Label("Min. Confidence:");
        Slider confSlider = new Slider(0, 100, OCRPreferences.getMinConfidence() * 100);
        confSlider.setShowTickLabels(true);
        confSlider.setShowTickMarks(true);
        confSlider.setMajorTickUnit(25);
        confSlider.setPrefWidth(200);

        Label confValue = new Label(String.format("%.0f%%", confSlider.getValue()));
        confSlider.valueProperty().addListener((obs, old, newVal) -> {
            confValue.setText(String.format("%.0f%%", newVal.doubleValue()));
            OCRPreferences.setMinConfidence(newVal.doubleValue() / 100.0);
        });

        HBox confBox = new HBox(10, confSlider, confValue);

        ocrGrid.add(psmLabel, 0, 0);
        ocrGrid.add(psmCombo, 1, 0);
        ocrGrid.add(confLabel, 0, 1);
        ocrGrid.add(confBox, 1, 1);

        ocrPane.setContent(ocrGrid);

        // === Preprocessing Section ===
        TitledPane preprocPane = new TitledPane();
        preprocPane.setText("Image Preprocessing");
        preprocPane.setCollapsible(false);

        VBox preprocBox = new VBox(8);
        preprocBox.setPadding(new Insets(10));

        CheckBox detectOrientationCb = new CheckBox("Detect text orientation");
        detectOrientationCb.setSelected(OCRPreferences.isDetectOrientation());
        detectOrientationCb.selectedProperty().addListener((obs, old, newVal) ->
                OCRPreferences.setDetectOrientation(newVal));

        CheckBox autoRotateCb = new CheckBox("Auto-rotate to correct orientation");
        autoRotateCb.setSelected(OCRPreferences.isAutoRotate());
        autoRotateCb.selectedProperty().addListener((obs, old, newVal) ->
                OCRPreferences.setAutoRotate(newVal));
        autoRotateCb.disableProperty().bind(detectOrientationCb.selectedProperty().not());

        CheckBox enhanceContrastCb = new CheckBox("Enhance image contrast");
        enhanceContrastCb.setSelected(OCRPreferences.isEnhanceContrast());
        enhanceContrastCb.selectedProperty().addListener((obs, old, newVal) ->
                OCRPreferences.setEnhanceContrast(newVal));

        preprocBox.getChildren().addAll(
                detectOrientationCb,
                autoRotateCb,
                enhanceContrastCb
        );
        preprocPane.setContent(preprocBox);

        // === Metadata Section ===
        TitledPane metadataPane = new TitledPane();
        metadataPane.setText("Metadata");
        metadataPane.setCollapsible(false);

        GridPane metadataGrid = new GridPane();
        metadataGrid.setHgap(10);
        metadataGrid.setVgap(10);
        metadataGrid.setPadding(new Insets(10));

        Label prefixLabel = new Label("Key Prefix:");
        TextField prefixField = new TextField(OCRPreferences.getMetadataPrefix());
        prefixField.setPrefWidth(150);
        prefixField.setPromptText("OCR_");
        prefixField.textProperty().addListener((obs, old, newVal) ->
                OCRPreferences.setMetadataPrefix(newVal));

        Label prefixHint = new Label("Prefix added to all OCR metadata keys");
        prefixHint.setStyle("-fx-text-fill: gray; -fx-font-size: 10px;");

        metadataGrid.add(prefixLabel, 0, 0);
        metadataGrid.add(prefixField, 1, 0);
        metadataGrid.add(prefixHint, 1, 1);

        metadataPane.setContent(metadataGrid);

        // === Reset Button ===
        Button resetButton = new Button("Reset to Defaults");
        resetButton.setOnAction(e -> {
            OCRPreferences.resetToDefaults();
            Dialogs.showInfoNotification("Settings Reset",
                    "OCR settings have been reset to defaults.\n" +
                            "Please reopen this dialog to see the changes.");
        });

        vbox.getChildren().addAll(
                tessdataPane,
                ocrPane,
                preprocPane,
                metadataPane,
                resetButton
        );

        return vbox;
    }

    /**
     * Converts PSM value to combo box index.
     */
    private static int psmToIndex(int psm) {
        return switch (psm) {
            case 3 -> 0;  // AUTO
            case 6 -> 1;  // SINGLE_BLOCK
            case 7 -> 2;  // SINGLE_LINE
            case 8 -> 3;  // SINGLE_WORD
            case 11 -> 4; // SPARSE_TEXT
            default -> 0;
        };
    }

    /**
     * Converts combo box index to PSM value.
     */
    private static int indexToPsm(int index) {
        return switch (index) {
            case 0 -> 3;  // AUTO
            case 1 -> 6;  // SINGLE_BLOCK
            case 2 -> 7;  // SINGLE_LINE
            case 3 -> 8;  // SINGLE_WORD
            case 4 -> 11; // SPARSE_TEXT
            default -> 3;
        };
    }
}
