package qupath.ext.ocr4labels.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.ocr4labels.controller.OCRController;
import qupath.ext.ocr4labels.model.BoundingBox;
import qupath.ext.ocr4labels.model.OCRConfiguration;
import qupath.ext.ocr4labels.model.OCRResult;
import qupath.ext.ocr4labels.model.TextBlock;
import qupath.ext.ocr4labels.preferences.OCRPreferences;
import qupath.ext.ocr4labels.service.OCREngine;
import qupath.ext.ocr4labels.utilities.MetadataKeyValidator;
import qupath.ext.ocr4labels.utilities.OCRMetadataManager;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.projects.Project;
import qupath.lib.scripting.QP;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Main dialog for OCR processing and field labeling.
 */
public class OCRDialog {

    private static final Logger logger = LoggerFactory.getLogger(OCRDialog.class);

    private static final ResourceBundle resources =
            ResourceBundle.getBundle("qupath.ext.ocr4labels.ui.strings");

    private final QuPathGUI qupath;
    private final BufferedImage labelImage;
    private final OCREngine ocrEngine;

    private Stage stage;
    private ImageView imageView;
    private Canvas overlayCanvas;
    private TableView<OCRFieldEntry> fieldsTable;
    private ObservableList<OCRFieldEntry> fieldEntries;
    private TextArea metadataPreview;
    private ProgressIndicator progressIndicator;

    private OCRResult currentResult;
    private int selectedIndex = -1;

    // Toolbar controls for OCR settings
    private ComboBox<PSMOption> psmCombo;
    private CheckBox invertCheckBox;
    private CheckBox thresholdCheckBox;
    private Slider confSlider;

    /**
     * Shows the OCR dialog for a label image.
     */
    public static void show(QuPathGUI qupath, BufferedImage labelImage, OCREngine ocrEngine) {
        OCRDialog dialog = new OCRDialog(qupath, labelImage, ocrEngine);
        dialog.showDialog();
    }

    private OCRDialog(QuPathGUI qupath, BufferedImage labelImage, OCREngine ocrEngine) {
        this.qupath = qupath;
        this.labelImage = labelImage;
        this.ocrEngine = ocrEngine;
        this.fieldEntries = FXCollections.observableArrayList();
    }

    private void showDialog() {
        stage = new Stage();
        stage.setTitle(resources.getString("dialog.title"));
        stage.initOwner(qupath.getStage());
        stage.initModality(Modality.WINDOW_MODAL);

        BorderPane root = new BorderPane();
        root.setTop(createToolbar());
        root.setCenter(createMainContent());
        root.setBottom(createButtonBar());

        Scene scene = new Scene(root, OCRPreferences.getDialogWidth(), OCRPreferences.getDialogHeight());
        stage.setScene(scene);

        // Save dialog size on close
        stage.setOnCloseRequest(e -> {
            OCRPreferences.setDialogWidth(stage.getWidth());
            OCRPreferences.setDialogHeight(stage.getHeight());
        });

        stage.show();

        // Auto-run OCR when dialog opens
        Platform.runLater(this::runOCR);
    }

    private ToolBar createToolbar() {
        Button runOCRButton = new Button(resources.getString("button.runOCR"));
        runOCRButton.setOnAction(e -> runOCR());

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(24, 24);
        progressIndicator.setVisible(false);

        // PSM Mode dropdown - Sparse Text is best default for slide labels
        Label psmLabel = new Label("Mode:");
        psmCombo = new ComboBox<>();
        psmCombo.getItems().addAll(PSMOption.values());
        psmCombo.setValue(PSMOption.SPARSE_TEXT);  // Best for scattered text on labels
        psmCombo.setTooltip(new Tooltip(
                "How to search for text on the label:\n\n" +
                "Sparse Text: Best for labels - finds text scattered across the image\n" +
                "Single Block: For labels with one paragraph of text\n" +
                "Single Line/Word: For very simple labels\n\n" +
                "Try different modes if text isn't detected."));

        // Confidence slider - important setting, placed prominently
        Label confLabel = new Label("Min Conf:");
        confSlider = new Slider(0, 100, OCRPreferences.getMinConfidence() * 100);
        confSlider.setPrefWidth(100);
        confSlider.setShowTickMarks(true);
        confSlider.setMajorTickUnit(50);
        confSlider.setTooltip(new Tooltip(
                "Minimum confidence level for detected text.\n\n" +
                "Lower = show more text (may include errors)\n" +
                "Higher = show only confident detections\n\n" +
                "Try lowering this if text isn't being detected."));

        Label confValue = new Label(String.format("%.0f%%", confSlider.getValue()));
        confSlider.valueProperty().addListener((obs, old, newVal) -> {
            confValue.setText(String.format("%.0f%%", newVal.doubleValue()));
        });

        // Preprocessing options
        invertCheckBox = new CheckBox("Invert");
        invertCheckBox.setTooltip(new Tooltip(
                "Flip dark and light colors.\n\n" +
                "Use this if your label has light/white text on a dark background.\n" +
                "Most labels have dark text on light background and don't need this."));

        thresholdCheckBox = new CheckBox("Enhance");
        thresholdCheckBox.setSelected(true);
        thresholdCheckBox.setTooltip(new Tooltip(
                "Improve image contrast before reading text.\n\n" +
                "Helps with faded labels, colored backgrounds, or poor lighting.\n" +
                "Usually best to leave this enabled."));

        return new ToolBar(
                runOCRButton,
                progressIndicator,
                new Separator(),
                psmLabel,
                psmCombo,
                new Separator(),
                confLabel,
                confSlider,
                confValue,
                new Separator(),
                invertCheckBox,
                thresholdCheckBox
        );
    }

    private SplitPane createMainContent() {
        SplitPane splitPane = new SplitPane();

        // Left panel: Image display
        VBox imagePanel = createImagePanel();

        // Right panel: Fields table and preview
        VBox fieldsPanel = createFieldsPanel();

        splitPane.getItems().addAll(imagePanel, fieldsPanel);
        splitPane.setDividerPositions(0.5);

        return splitPane;
    }

    private VBox createImagePanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        Label titleLabel = new Label(resources.getString("label.image"));
        titleLabel.setStyle("-fx-font-weight: bold;");

        // Image display with overlay
        StackPane imageStack = new StackPane();

        imageView = new ImageView();
        imageView.setPreserveRatio(true);

        // Convert BufferedImage to JavaFX Image
        Image fxImage = SwingFXUtils.toFXImage(labelImage, null);
        imageView.setImage(fxImage);

        // Overlay canvas for bounding boxes
        overlayCanvas = new Canvas();

        imageStack.getChildren().addAll(imageView, overlayCanvas);

        // Bind canvas size to image size
        imageView.fitWidthProperty().addListener((obs, old, newVal) -> updateCanvasSize());
        imageView.fitHeightProperty().addListener((obs, old, newVal) -> updateCanvasSize());

        ScrollPane scrollPane = new ScrollPane(imageStack);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Make image fit the scroll pane
        imageView.fitWidthProperty().bind(scrollPane.widthProperty().subtract(20));

        // Zoom controls
        HBox zoomControls = new HBox(10);
        zoomControls.setAlignment(Pos.CENTER_LEFT);

        Button fitButton = new Button("Fit");
        fitButton.setOnAction(e -> {
            imageView.fitWidthProperty().bind(scrollPane.widthProperty().subtract(20));
            // Redraw bounding boxes after view change
            Platform.runLater(this::drawBoundingBoxes);
        });

        Button actualButton = new Button("100%");
        actualButton.setOnAction(e -> {
            imageView.fitWidthProperty().unbind();
            imageView.setFitWidth(labelImage.getWidth());
            // Redraw bounding boxes after view change
            Platform.runLater(this::drawBoundingBoxes);
        });

        zoomControls.getChildren().addAll(fitButton, actualButton);

        panel.getChildren().addAll(titleLabel, scrollPane, zoomControls);
        return panel;
    }

    private VBox createFieldsPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        Label titleLabel = new Label(resources.getString("label.detectedFields"));
        titleLabel.setStyle("-fx-font-weight: bold;");

        // Fields table
        fieldsTable = new TableView<>(fieldEntries);
        fieldsTable.setEditable(true);
        fieldsTable.setPlaceholder(new Label("Run OCR to detect text fields"));

        // Text column
        TableColumn<OCRFieldEntry, String> textCol = new TableColumn<>(resources.getString("column.text"));
        textCol.setCellValueFactory(data -> data.getValue().textProperty());
        textCol.setCellFactory(TextFieldTableCell.forTableColumn());
        textCol.setOnEditCommit(e -> {
            e.getRowValue().setText(e.getNewValue());
            updateMetadataPreview();
        });
        textCol.setPrefWidth(200);

        // Metadata key column
        TableColumn<OCRFieldEntry, String> keyCol = new TableColumn<>(resources.getString("column.metadataKey"));
        keyCol.setCellValueFactory(data -> data.getValue().metadataKeyProperty());
        keyCol.setCellFactory(col -> new MetadataKeyCell());
        keyCol.setOnEditCommit(e -> {
            String newKey = e.getNewValue();
            MetadataKeyValidator.ValidationResult validation = MetadataKeyValidator.validateKey(newKey);
            if (validation.isValid()) {
                e.getRowValue().setMetadataKey(newKey);
            } else {
                Dialogs.showWarningNotification("Invalid Key", validation.getErrorMessage());
                fieldsTable.refresh();
            }
            updateMetadataPreview();
        });
        keyCol.setPrefWidth(150);

        // Confidence column
        TableColumn<OCRFieldEntry, String> confCol = new TableColumn<>("Conf.");
        confCol.setCellValueFactory(data -> new SimpleStringProperty(
                String.format("%.0f%%", data.getValue().getConfidence() * 100)));
        confCol.setPrefWidth(50);

        fieldsTable.getColumns().addAll(textCol, keyCol, confCol);
        VBox.setVgrow(fieldsTable, Priority.ALWAYS);

        // Handle selection
        fieldsTable.getSelectionModel().selectedIndexProperty().addListener((obs, old, newVal) -> {
            selectedIndex = newVal.intValue();
            drawBoundingBoxes();
        });

        // Button bar
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_LEFT);

        Button addButton = new Button(resources.getString("button.addField"));
        addButton.setOnAction(e -> addManualField());

        Button clearButton = new Button(resources.getString("button.clearAll"));
        clearButton.setOnAction(e -> {
            fieldEntries.clear();
            updateMetadataPreview();
            drawBoundingBoxes();
        });

        buttonBar.getChildren().addAll(addButton, clearButton);

        // Metadata preview
        Label previewLabel = new Label(resources.getString("label.metadataPreview"));
        previewLabel.setStyle("-fx-font-weight: bold;");

        metadataPreview = new TextArea();
        metadataPreview.setEditable(false);
        metadataPreview.setPrefRowCount(5);
        metadataPreview.setStyle("-fx-font-family: monospace;");

        panel.getChildren().addAll(titleLabel, fieldsTable, buttonBar, previewLabel, metadataPreview);
        return panel;
    }

    private HBox createButtonBar() {
        HBox buttonBar = new HBox(15);
        buttonBar.setPadding(new Insets(10));
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button applyButton = new Button(resources.getString("button.apply"));
        applyButton.setDefaultButton(true);
        applyButton.setOnAction(e -> applyMetadata());

        Button cancelButton = new Button(resources.getString("button.cancel"));
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> stage.close());

        buttonBar.getChildren().addAll(spacer, applyButton, cancelButton);
        return buttonBar;
    }

    private void runOCR() {
        progressIndicator.setVisible(true);

        // Build configuration from toolbar settings
        PSMOption selectedPSM = psmCombo.getValue();
        OCRConfiguration.PageSegMode psm = selectedPSM != null ? selectedPSM.getMode() : OCRConfiguration.PageSegMode.AUTO;

        OCRConfiguration config = OCRConfiguration.builder()
                .pageSegMode(psm)
                .language(OCRPreferences.getLanguage())
                .minConfidence(confSlider.getValue() / 100.0)
                .autoRotate(OCRPreferences.isAutoRotate())
                .detectOrientation(OCRPreferences.isDetectOrientation())
                .enhanceContrast(thresholdCheckBox.isSelected())
                .enablePreprocessing(true)
                .build();

        // Preprocess the image based on checkbox settings
        BufferedImage imageToProcess = preprocessForOCR(labelImage);

        OCRController.getInstance().performOCRAsync(imageToProcess, config)
                .thenAccept(result -> Platform.runLater(() -> {
                    currentResult = result;
                    populateFieldsTable(result);
                    drawBoundingBoxes();
                    progressIndicator.setVisible(false);

                    String modeInfo = selectedPSM != null ? selectedPSM.toString() : "Auto";
                    Dialogs.showInfoNotification("OCR Complete",
                            String.format("Detected %d text blocks in %dms (Mode: %s)",
                                    result.getBlockCount(), result.getProcessingTimeMs(), modeInfo));
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        progressIndicator.setVisible(false);
                        Dialogs.showErrorMessage("OCR Failed", ex.getMessage());
                    });
                    return null;
                });
    }

    /**
     * Preprocesses the image based on dialog settings.
     */
    private BufferedImage preprocessForOCR(BufferedImage source) {
        BufferedImage result = source;

        // Invert if checkbox selected
        if (invertCheckBox.isSelected()) {
            result = invertImage(result);
        }

        return result;
    }

    /**
     * Inverts image colors.
     */
    private BufferedImage invertImage(BufferedImage source) {
        BufferedImage inverted = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);
                int r = 255 - ((rgb >> 16) & 0xFF);
                int g = 255 - ((rgb >> 8) & 0xFF);
                int b = 255 - (rgb & 0xFF);
                inverted.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        return inverted;
    }

    private void populateFieldsTable(OCRResult result) {
        fieldEntries.clear();

        String prefix = OCRPreferences.getMetadataPrefix();
        int index = 0;

        for (TextBlock block : result.getTextBlocks()) {
            if (block.getType() == TextBlock.BlockType.LINE && !block.isEmpty()) {
                String suggestedKey = prefix + "field_" + index;
                OCRFieldEntry entry = new OCRFieldEntry(
                        block.getText(),
                        suggestedKey,
                        block.getConfidence(),
                        block.getBoundingBox()
                );
                fieldEntries.add(entry);
                index++;
            }
        }

        // If no lines, use words
        if (fieldEntries.isEmpty()) {
            for (TextBlock block : result.getTextBlocks()) {
                if (block.getType() == TextBlock.BlockType.WORD && !block.isEmpty()) {
                    String suggestedKey = prefix + "field_" + index;
                    OCRFieldEntry entry = new OCRFieldEntry(
                            block.getText(),
                            suggestedKey,
                            block.getConfidence(),
                            block.getBoundingBox()
                    );
                    fieldEntries.add(entry);
                    index++;
                }
            }
        }

        updateMetadataPreview();
    }

    private void drawBoundingBoxes() {
        if (overlayCanvas == null) return;

        updateCanvasSize();

        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());

        if (fieldEntries.isEmpty()) return;

        // Calculate scale factor
        double scaleX = imageView.getBoundsInLocal().getWidth() / labelImage.getWidth();
        double scaleY = imageView.getBoundsInLocal().getHeight() / labelImage.getHeight();
        double scale = Math.min(scaleX, scaleY);

        int index = 0;
        for (OCRFieldEntry entry : fieldEntries) {
            BoundingBox bbox = entry.getBoundingBox();
            if (bbox == null) continue;

            // Scale coordinates
            double x = bbox.getX() * scale;
            double y = bbox.getY() * scale;
            double w = bbox.getWidth() * scale;
            double h = bbox.getHeight() * scale;

            // Draw box
            if (index == selectedIndex) {
                gc.setStroke(Color.YELLOW);
                gc.setLineWidth(3);
            } else {
                gc.setStroke(Color.LIME);
                gc.setLineWidth(2);
            }
            gc.strokeRect(x, y, w, h);

            // Draw label
            gc.setFill(Color.rgb(0, 0, 0, 0.7));
            gc.fillRect(x, y - 16, Math.min(w, 60), 16);
            gc.setFill(Color.WHITE);
            gc.fillText(String.valueOf(index + 1), x + 3, y - 3);

            index++;
        }
    }

    private void updateCanvasSize() {
        if (imageView != null && overlayCanvas != null) {
            overlayCanvas.setWidth(imageView.getBoundsInLocal().getWidth());
            overlayCanvas.setHeight(imageView.getBoundsInLocal().getHeight());
        }
    }

    private void updateMetadataPreview() {
        StringBuilder sb = new StringBuilder();

        for (OCRFieldEntry entry : fieldEntries) {
            String key = entry.getMetadataKey();
            String value = entry.getText();

            if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
                sb.append(key).append(": ").append(value).append("\n");
            }
        }

        metadataPreview.setText(sb.toString());
    }

    private void addManualField() {
        String prefix = OCRPreferences.getMetadataPrefix();
        String key = prefix + "field_" + fieldEntries.size();
        OCRFieldEntry entry = new OCRFieldEntry("", key, 1.0f, null);
        fieldEntries.add(entry);
        fieldsTable.getSelectionModel().select(entry);
        updateMetadataPreview();
    }

    private void applyMetadata() {
        var projectEntry = QP.getProjectEntry();
        if (projectEntry == null) {
            Dialogs.showWarningNotification("No Project Entry",
                    "Cannot apply metadata - no project entry is available.");
            return;
        }

        Map<String, String> metadata = new HashMap<>();
        Map<Integer, String> fieldMappings = new HashMap<>();

        int fieldIndex = 0;
        for (OCRFieldEntry entry : fieldEntries) {
            String key = entry.getMetadataKey();
            String value = entry.getText();

            if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
                MetadataKeyValidator.ValidationResult validation = MetadataKeyValidator.validateKey(key);
                if (validation.isValid()) {
                    metadata.put(key, value);
                    fieldMappings.put(fieldIndex, key);
                }
            }
            fieldIndex++;
        }

        if (metadata.isEmpty()) {
            Dialogs.showWarningNotification("No Metadata",
                    "No valid metadata fields to apply.");
            return;
        }

        Project<?> project = qupath.getProject();
        int count = OCRMetadataManager.setMetadataBatch(projectEntry, metadata, project);

        if (count > 0) {
            // Add workflow step for reproducibility
            addWorkflowStep(fieldMappings);

            Dialogs.showInfoNotification("Metadata Applied",
                    String.format("Successfully applied %d metadata fields.", count));
            stage.close();
        } else {
            Dialogs.showErrorMessage("Apply Failed",
                    "Failed to apply metadata. Check the log for details.");
        }
    }

    /**
     * Adds a workflow step to the current image's history.
     * This allows the OCR operation to be reproduced via script.
     */
    private void addWorkflowStep(Map<Integer, String> fieldMappings) {
        var imageData = qupath.getImageData();
        if (imageData == null) {
            logger.debug("No image data - skipping workflow step");
            return;
        }

        // Build configuration string based on current settings
        PSMOption selectedPSM = psmCombo.getValue();
        boolean invert = invertCheckBox.isSelected();
        boolean enhance = thresholdCheckBox.isSelected();
        double confidence = confSlider.getValue() / 100.0;

        StringBuilder script = new StringBuilder();
        script.append("// OCR for Labels - Auto-generated script\n");
        script.append("// Run this script to reproduce OCR results on similar images\n");
        script.append("import qupath.ext.ocr4labels.OCR4Labels\n\n");

        // Check for label image
        script.append("if (!OCR4Labels.hasLabelImage()) {\n");
        script.append("    println \"No label image available\"\n");
        script.append("    return\n");
        script.append("}\n\n");

        // Run OCR with configuration
        script.append("def results = OCR4Labels.builder()\n");

        // Page segmentation mode
        if (selectedPSM != null) {
            switch (selectedPSM.getMode()) {
                case SPARSE_TEXT:
                    script.append("    .sparseText()\n");
                    break;
                case AUTO:
                    script.append("    .autoDetect()\n");
                    break;
                case SINGLE_BLOCK:
                    script.append("    .singleBlock()\n");
                    break;
                case SINGLE_LINE:
                    script.append("    .singleLine()\n");
                    break;
                case SINGLE_WORD:
                    script.append("    .singleWord()\n");
                    break;
                default:
                    script.append("    .sparseText()\n");
            }
        }

        // Preprocessing
        if (enhance) {
            script.append("    .enhance()\n");
        } else {
            script.append("    .noEnhance()\n");
        }

        if (invert) {
            script.append("    .invert()\n");
        }

        // Confidence
        script.append("    .minConfidence(").append(String.format("%.2f", confidence)).append(")\n");
        script.append("    .run()\n\n");

        // Apply metadata mappings
        script.append("// Apply detected text to metadata fields\n");
        for (Map.Entry<Integer, String> entry : fieldMappings.entrySet()) {
            int idx = entry.getKey();
            String key = entry.getValue();
            script.append("if (results.size() > ").append(idx).append(") {\n");
            script.append("    OCR4Labels.setMetadataValue(\"").append(key).append("\", results[").append(idx).append("])\n");
            script.append("}\n");
        }

        script.append("\nprintln \"Applied \" + results.size() + \" OCR fields\"\n");

        // Add to workflow
        try {
            imageData.getHistoryWorkflow().addStep(
                    new DefaultScriptableWorkflowStep(
                            "OCR Label Recognition",
                            script.toString()
                    )
            );
            logger.info("Added OCR workflow step");
        } catch (Exception e) {
            logger.warn("Failed to add workflow step: {}", e.getMessage());
        }
    }

    /**
     * Data class for table entries.
     */
    public static class OCRFieldEntry {
        private final SimpleStringProperty text;
        private final SimpleStringProperty metadataKey;
        private final float confidence;
        private final BoundingBox boundingBox;

        public OCRFieldEntry(String text, String metadataKey, float confidence, BoundingBox boundingBox) {
            this.text = new SimpleStringProperty(text);
            this.metadataKey = new SimpleStringProperty(metadataKey);
            this.confidence = confidence;
            this.boundingBox = boundingBox;
        }

        public String getText() { return text.get(); }
        public void setText(String value) { text.set(value); }
        public SimpleStringProperty textProperty() { return text; }

        public String getMetadataKey() { return metadataKey.get(); }
        public void setMetadataKey(String value) { metadataKey.set(value); }
        public SimpleStringProperty metadataKeyProperty() { return metadataKey; }

        public float getConfidence() { return confidence; }
        public BoundingBox getBoundingBox() { return boundingBox; }
    }

    /**
     * Custom cell for metadata key editing with validation feedback.
     */
    private static class MetadataKeyCell extends TextFieldTableCell<OCRFieldEntry, String> {
        public MetadataKeyCell() {
            super(new javafx.util.converter.DefaultStringConverter());
        }

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (!empty && item != null) {
                MetadataKeyValidator.ValidationResult validation =
                        MetadataKeyValidator.validateKey(item);
                if (!validation.isValid()) {
                    setStyle("-fx-background-color: #ffcccc;");
                    setTooltip(new Tooltip(validation.getErrorMessage()));
                } else {
                    setStyle("");
                    setTooltip(null);
                }
            }
        }
    }

    /**
     * User-friendly PSM options for the dropdown.
     */
    public enum PSMOption {
        AUTO("Auto (default)", OCRConfiguration.PageSegMode.AUTO),
        AUTO_OSD("Auto + Orientation", OCRConfiguration.PageSegMode.AUTO_OSD),
        SINGLE_BLOCK("Single Block", OCRConfiguration.PageSegMode.SINGLE_BLOCK),
        SINGLE_LINE("Single Line", OCRConfiguration.PageSegMode.SINGLE_LINE),
        SINGLE_WORD("Single Word", OCRConfiguration.PageSegMode.SINGLE_WORD),
        SPARSE_TEXT("Sparse Text", OCRConfiguration.PageSegMode.SPARSE_TEXT),
        SPARSE_TEXT_OSD("Sparse + Orientation", OCRConfiguration.PageSegMode.SPARSE_TEXT_OSD);

        private final String displayName;
        private final OCRConfiguration.PageSegMode mode;

        PSMOption(String displayName, OCRConfiguration.PageSegMode mode) {
            this.displayName = displayName;
            this.mode = mode;
        }

        public OCRConfiguration.PageSegMode getMode() {
            return mode;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
