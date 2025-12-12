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
import qupath.ext.ocr4labels.utilities.LabelImageUtility;
import qupath.ext.ocr4labels.utilities.MetadataKeyValidator;
import qupath.ext.ocr4labels.utilities.OCRMetadataManager;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import javafx.stage.FileChooser;
import qupath.ext.ocr4labels.model.OCRTemplate;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Main dialog for OCR processing and field labeling.
 * Supports navigating through all project entries and applying OCR results as metadata.
 */
public class OCRDialog {

    private static final Logger logger = LoggerFactory.getLogger(OCRDialog.class);

    private static final ResourceBundle resources =
            ResourceBundle.getBundle("qupath.ext.ocr4labels.ui.strings");

    private final QuPathGUI qupath;
    private final OCREngine ocrEngine;
    private final Project<?> project;

    // Current state
    private ProjectImageEntry<?> selectedEntry;
    private BufferedImage labelImage;

    private Stage stage;
    private ImageView imageView;
    private Canvas overlayCanvas;
    private StackPane imageStack;
    private ScrollPane imageScrollPane;
    private Label noLabelLabel;
    private TableView<OCRFieldEntry> fieldsTable;
    private ObservableList<OCRFieldEntry> fieldEntries;
    private TextArea metadataPreview;
    private ProgressIndicator progressIndicator;
    private ListView<ProjectImageEntry<?>> entryListView;
    private Button runOCRButton;

    private OCRResult currentResult;
    private int selectedIndex = -1;

    // Toolbar controls for OCR settings
    private ComboBox<PSMOption> psmCombo;
    private CheckBox invertCheckBox;
    private CheckBox thresholdCheckBox;
    private Slider confSlider;

    // Region selection state
    private boolean regionSelectionMode = false;
    private double selectionStartX, selectionStartY;
    private double selectionEndX, selectionEndY;
    private boolean hasSelection = false;
    private ToggleButton selectRegionButton;
    private Button scanRegionButton;

    // Template state
    private OCRTemplate currentTemplate;
    private CheckBox useFixedPositionsCheckBox;

    // Previous field entries for metadata key preservation across slides
    private List<OCRFieldEntry> previousFieldEntries = new ArrayList<>();
    private int previousImageWidth = 0;
    private int previousImageHeight = 0;

    /**
     * Shows the OCR dialog for processing project entries.
     *
     * @param qupath The QuPath GUI instance
     * @param ocrEngine The OCR engine to use
     */
    public static void show(QuPathGUI qupath, OCREngine ocrEngine) {
        Project<?> project = qupath.getProject();
        if (project == null) {
            Dialogs.showWarningNotification("No Project",
                    "Please open a project first to use the OCR dialog.");
            return;
        }

        if (project.getImageList().isEmpty()) {
            Dialogs.showWarningNotification("Empty Project",
                    "The project has no images. Add images to the project first.");
            return;
        }

        OCRDialog dialog = new OCRDialog(qupath, project, ocrEngine);
        dialog.showDialog();
    }

    private OCRDialog(QuPathGUI qupath, Project<?> project, OCREngine ocrEngine) {
        this.qupath = qupath;
        this.project = project;
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

        // Select initial entry (current image if available, otherwise none)
        selectInitialEntry();
    }

    /**
     * Selects the initial entry based on the currently open image in QuPath.
     */
    private void selectInitialEntry() {
        ImageData<?> currentImageData = qupath.getImageData();
        if (currentImageData == null) {
            // No image open - show empty state
            return;
        }

        // Find the project entry matching the current image
        var currentServer = currentImageData.getServer();
        if (currentServer == null) return;

        var currentUris = currentServer.getURIs();
        String currentUri = currentUris.isEmpty() ? null : currentUris.iterator().next().toString();
        if (currentUri == null) return;

        for (ProjectImageEntry<?> entry : project.getImageList()) {
            try {
                var entryUris = entry.getURIs();
                String entryUri = entryUris.isEmpty() ? null : entryUris.iterator().next().toString();
                if (currentUri.equals(entryUri)) {
                    entryListView.getSelectionModel().select(entry);
                    return;
                }
            } catch (Exception e) {
                // Continue searching
            }
        }
    }

    private ToolBar createToolbar() {
        runOCRButton = new Button(resources.getString("button.runOCR"));
        runOCRButton.setOnAction(e -> runOCR());
        // Highlight the Run OCR button with high contrast styling
        runOCRButton.setStyle("-fx-font-weight: bold; -fx-background-color: #4a90d9; -fx-text-fill: white; " +
                "-fx-border-color: #2d5a87; -fx-border-width: 2px; -fx-border-radius: 3px; -fx-background-radius: 3px;");

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(24, 24);
        progressIndicator.setVisible(false);

        // PSM Mode dropdown - Sparse Text is best default for slide labels
        Label psmLabel = new Label("Mode:");
        psmCombo = new ComboBox<>();
        psmCombo.getItems().addAll(PSMOption.values());
        psmCombo.setValue(PSMOption.SPARSE_TEXT);
        psmCombo.setTooltip(new Tooltip(
                "How to search for text on the label:\n\n" +
                "Sparse Text: Best for labels - finds text scattered across the image\n" +
                "Single Block: For labels with one paragraph of text\n" +
                "Single Line/Word: For very simple labels\n\n" +
                "Try different modes if text isn't detected."));

        // Confidence slider
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
                // Persist the value for next session
                OCRPreferences.setMinConfidence(newVal.doubleValue() / 100.0);
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

        // Region selection tools
        selectRegionButton = new ToggleButton("Select Region");
        selectRegionButton.setTooltip(new Tooltip(
                "Draw a rectangle on the image to target a specific area.\n\n" +
                "Use this when text isn't being detected - select the area\n" +
                "containing the text, then click 'Scan Region' to analyze\n" +
                "just that area with very sensitive settings."));
        selectRegionButton.setOnAction(e -> {
            regionSelectionMode = selectRegionButton.isSelected();
            if (!regionSelectionMode) {
                hasSelection = false;
                drawBoundingBoxes();
            }
            updateScanRegionButton();
        });

        scanRegionButton = new Button("Scan Region");
        scanRegionButton.setDisable(true);
        scanRegionButton.setTooltip(new Tooltip(
                "Run OCR on the selected region with very low confidence threshold.\n\n" +
                "This is useful for difficult-to-read text that isn't being\n" +
                "detected with normal settings."));
        scanRegionButton.setOnAction(e -> scanSelectedRegion());

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
                thresholdCheckBox,
                new Separator(),
                selectRegionButton,
                scanRegionButton
        );
    }

    private SplitPane createMainContent() {
        SplitPane mainSplit = new SplitPane();

        // Left panel: Project entry list
        VBox entryListPanel = createEntryListPanel();

        // Right panel: Image and Fields stacked vertically
        SplitPane rightSplit = new SplitPane();
        rightSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);

        VBox imagePanel = createImagePanel();
        VBox fieldsPanel = createFieldsPanel();

        rightSplit.getItems().addAll(imagePanel, fieldsPanel);
        rightSplit.setDividerPositions(0.55);

        mainSplit.getItems().addAll(entryListPanel, rightSplit);
        mainSplit.setDividerPositions(0.2);

        return mainSplit;
    }

    /**
     * Creates the project entry list panel.
     */
    private VBox createEntryListPanel() {
        VBox panel = new VBox(5);
        panel.setPadding(new Insets(10));
        panel.setMinWidth(150);
        panel.setPrefWidth(200);

        Label titleLabel = new Label("Project Images");
        titleLabel.setStyle("-fx-font-weight: bold;");

        // Create ListView with project entries
        entryListView = new ListView<>();
        entryListView.getItems().addAll(project.getImageList());

        // Custom cell factory to show entry names
        entryListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ProjectImageEntry<?> entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    String name = entry.getImageName();
                    setText(name);
                    setTooltip(new Tooltip(name));
                }
            }
        });

        // Handle selection changes
        entryListView.getSelectionModel().selectedItemProperty().addListener((obs, oldEntry, newEntry) -> {
            if (newEntry != null) {
                onEntrySelected(newEntry);
            }
        });

        VBox.setVgrow(entryListView, Priority.ALWAYS);

        // Entry count label
        Label countLabel = new Label(String.format("%d images", project.getImageList().size()));
        countLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        panel.getChildren().addAll(titleLabel, entryListView, countLabel);
        return panel;
    }

    /**
     * Handles selection of a project entry from the list.
     */
    private void onEntrySelected(ProjectImageEntry<?> entry) {
        selectedEntry = entry;

        // Save current field entries and image dimensions for metadata key preservation
        if (!fieldEntries.isEmpty() && labelImage != null) {
            previousFieldEntries = new ArrayList<>(fieldEntries);
            previousImageWidth = labelImage.getWidth();
            previousImageHeight = labelImage.getHeight();
            logger.debug("Saved {} field entries from previous image ({}x{})",
                    previousFieldEntries.size(), previousImageWidth, previousImageHeight);
        }

        // Clear detected fields but preserve region selection
        fieldEntries.clear();
        currentResult = null;

        // Load label image for the entry
        loadLabelImageForEntry(entry);

        // Update UI
        updateMetadataPreview();
        drawBoundingBoxes();

        // Auto-run OCR if enabled and we have a label image
        if (labelImage != null && OCRPreferences.isAutoRunOnEntrySwitch()) {
            Platform.runLater(this::runOCR);
        }
    }

    /**
     * Loads the label image for a project entry.
     */
    private void loadLabelImageForEntry(ProjectImageEntry<?> entry) {
        try {
            ImageData<?> imageData = entry.readImageData();
            if (imageData != null && LabelImageUtility.isLabelImageAvailable(imageData)) {
                labelImage = LabelImageUtility.retrieveLabelImage(imageData);
                updateImageDisplay();
            } else {
                labelImage = null;
                showNoLabelPlaceholder();
            }
        } catch (IOException e) {
            logger.error("Failed to load image data for entry: {}", entry.getImageName(), e);
            labelImage = null;
            showNoLabelPlaceholder();
        }
    }

    /**
     * Updates the image display with the current label image.
     */
    private void updateImageDisplay() {
        if (labelImage != null) {
            Image fxImage = SwingFXUtils.toFXImage(labelImage, null);
            imageView.setImage(fxImage);
            imageView.setVisible(true);
            overlayCanvas.setVisible(true);
            noLabelLabel.setVisible(false);
            // Fit image to pane after a short delay to allow layout
            Platform.runLater(() -> {
                fitImageToPane();
                drawBoundingBoxes();
            });
        } else {
            showNoLabelPlaceholder();
        }
    }

    /**
     * Fits the image to the scroll pane while maintaining aspect ratio.
     * Ensures the entire image is visible without scrolling.
     */
    private void fitImageToPane() {
        if (labelImage == null || imageScrollPane == null) return;

        double paneWidth = imageScrollPane.getViewportBounds().getWidth();
        double paneHeight = imageScrollPane.getViewportBounds().getHeight();

        // If viewport not yet sized, use scroll pane dimensions with padding
        if (paneWidth <= 0) paneWidth = imageScrollPane.getWidth() - 20;
        if (paneHeight <= 0) paneHeight = imageScrollPane.getHeight() - 20;

        if (paneWidth <= 0 || paneHeight <= 0) return;

        double imgWidth = labelImage.getWidth();
        double imgHeight = labelImage.getHeight();

        // Calculate scale to fit both dimensions
        double scaleX = paneWidth / imgWidth;
        double scaleY = paneHeight / imgHeight;
        double scale = Math.min(scaleX, scaleY);

        // Apply scaled dimensions
        imageView.setFitWidth(imgWidth * scale);
        imageView.setFitHeight(imgHeight * scale);
    }

    /**
     * Shows a placeholder when no label image is available.
     */
    private void showNoLabelPlaceholder() {
        imageView.setImage(null);
        imageView.setVisible(false);
        overlayCanvas.setVisible(false);
        noLabelLabel.setVisible(true);
    }

    private VBox createImagePanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        Label titleLabel = new Label(resources.getString("label.image"));
        titleLabel.setStyle("-fx-font-weight: bold;");

        // Image display with overlay
        imageStack = new StackPane();

        imageView = new ImageView();
        imageView.setPreserveRatio(true);

        // Placeholder for no label
        noLabelLabel = new Label("No Label Found\n\nSelect an image from the list,\nor this image has no label.");
        noLabelLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: gray; -fx-text-alignment: center;");
        noLabelLabel.setVisible(true);

        // Overlay canvas for bounding boxes and selection
        overlayCanvas = new Canvas();
        overlayCanvas.setVisible(false);

        // Mouse handlers for region selection and refresh
        overlayCanvas.setOnMousePressed(e -> {
            if (regionSelectionMode) {
                selectionStartX = e.getX();
                selectionStartY = e.getY();
                selectionEndX = e.getX();
                selectionEndY = e.getY();
                hasSelection = false;
                drawBoundingBoxes();
            } else {
                drawBoundingBoxes();
            }
        });

        overlayCanvas.setOnMouseDragged(e -> {
            if (regionSelectionMode) {
                selectionEndX = e.getX();
                selectionEndY = e.getY();
                hasSelection = true;
                drawBoundingBoxes();
            }
        });

        overlayCanvas.setOnMouseReleased(e -> {
            if (regionSelectionMode && hasSelection) {
                selectionEndX = e.getX();
                selectionEndY = e.getY();
                double width = Math.abs(selectionEndX - selectionStartX);
                double height = Math.abs(selectionEndY - selectionStartY);
                if (width < 5 || height < 5) {
                    hasSelection = false;
                }
                drawBoundingBoxes();
                updateScanRegionButton();
            }
        });

        imageStack.getChildren().addAll(noLabelLabel, imageView, overlayCanvas);

        // Bind canvas size to image size
        imageView.fitWidthProperty().addListener((obs, old, newVal) -> updateCanvasSize());
        imageView.fitHeightProperty().addListener((obs, old, newVal) -> updateCanvasSize());

        imageScrollPane = new ScrollPane(imageStack);
        imageScrollPane.setFitToWidth(true);
        imageScrollPane.setFitToHeight(true);
        VBox.setVgrow(imageScrollPane, Priority.ALWAYS);

        // Auto-refresh bounding boxes when scroll pane is resized
        imageScrollPane.widthProperty().addListener((obs, old, newVal) -> {
            fitImageToPane();
            Platform.runLater(this::drawBoundingBoxes);
        });
        imageScrollPane.heightProperty().addListener((obs, old, newVal) -> {
            fitImageToPane();
            Platform.runLater(this::drawBoundingBoxes);
        });

        // Zoom controls
        HBox zoomControls = new HBox(10);
        zoomControls.setAlignment(Pos.CENTER_LEFT);

        Button fitButton = new Button("Fit");
        fitButton.setOnAction(e -> {
            fitImageToPane();
            Platform.runLater(this::drawBoundingBoxes);
        });

        Button actualButton = new Button("100%");
        actualButton.setOnAction(e -> {
            if (labelImage != null) {
                imageView.setFitWidth(labelImage.getWidth());
                imageView.setFitHeight(labelImage.getHeight());
            }
            Platform.runLater(this::drawBoundingBoxes);
        });

        zoomControls.getChildren().addAll(fitButton, actualButton);

        panel.getChildren().addAll(titleLabel, imageScrollPane, zoomControls);
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
        fieldsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Text column - flexible width, user can resize
        TableColumn<OCRFieldEntry, String> textCol = new TableColumn<>(resources.getString("column.text"));
        textCol.setCellValueFactory(data -> data.getValue().textProperty());
        textCol.setCellFactory(TextFieldTableCell.forTableColumn());
        textCol.setOnEditCommit(e -> {
            e.getRowValue().setText(e.getNewValue());
            updateMetadataPreview();
        });
        textCol.setMinWidth(80);
        textCol.setPrefWidth(150);

        // Metadata key column - flexible width, user can resize
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
        keyCol.setMinWidth(100);
        keyCol.setPrefWidth(150);

        // Confidence column - fixed width (always shows e.g. "93%")
        TableColumn<OCRFieldEntry, String> confCol = new TableColumn<>("Conf");
        confCol.setCellValueFactory(data -> new SimpleStringProperty(
                String.format("%.0f%%", data.getValue().getConfidence() * 100)));
        confCol.setMinWidth(50);
        confCol.setMaxWidth(60);
        confCol.setPrefWidth(55);

        fieldsTable.getColumns().addAll(textCol, keyCol, confCol);
        VBox.setVgrow(fieldsTable, Priority.ALWAYS);

        // Handle selection
        fieldsTable.getSelectionModel().selectedIndexProperty().addListener((obs, old, newVal) -> {
            selectedIndex = newVal.intValue();
            drawBoundingBoxes();
        });

        // Button bar for field actions
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_LEFT);

        Button addButton = new Button(resources.getString("button.addField"));
        addButton.setOnAction(e -> addManualField());

        Button removeButton = new Button("Remove");
        removeButton.setTooltip(new Tooltip("Remove the selected field from the list"));
        removeButton.setOnAction(e -> {
            OCRFieldEntry selected = fieldsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                fieldEntries.remove(selected);
                updateMetadataPreview();
                drawBoundingBoxes();
            }
        });
        // Disable when nothing is selected
        removeButton.disableProperty().bind(
                fieldsTable.getSelectionModel().selectedItemProperty().isNull());

        Button clearButton = new Button(resources.getString("button.clearAll"));
        clearButton.setOnAction(e -> {
            fieldEntries.clear();
            updateMetadataPreview();
            drawBoundingBoxes();
        });

        buttonBar.getChildren().addAll(addButton, removeButton, clearButton);

        // Template toolbar
        HBox templateBar = new HBox(10);
        templateBar.setAlignment(Pos.CENTER_LEFT);

        Button saveTemplateBtn = new Button("Save Template...");
        saveTemplateBtn.setTooltip(new Tooltip(
                "Save current field positions and metadata keys to a template file.\n" +
                "Templates can be reused to process similar labels automatically."));
        saveTemplateBtn.setOnAction(e -> saveTemplate());

        Button loadTemplateBtn = new Button("Load Template...");
        loadTemplateBtn.setTooltip(new Tooltip(
                "Load a saved template to apply its field positions and metadata keys.\n" +
                "Use 'Apply Template' to extract text from the loaded positions."));
        loadTemplateBtn.setOnAction(e -> loadTemplate());

        Button applyTemplateBtn = new Button("Apply Template");
        applyTemplateBtn.setTooltip(new Tooltip(
                "Run OCR using the loaded template's fixed positions.\n" +
                "Each field will be extracted from its saved location with 20% dilation."));
        applyTemplateBtn.setOnAction(e -> applyTemplateToCurrentImage());
        applyTemplateBtn.setDisable(true);

        // Enable apply template button when template is loaded
        useFixedPositionsCheckBox = new CheckBox("Use Fixed Positions");
        useFixedPositionsCheckBox.setTooltip(new Tooltip(
                "When enabled, uses saved bounding box positions from the template\n" +
                "instead of running OCR detection. Each box is dilated 20% to\n" +
                "account for slight variations in label positioning."));
        useFixedPositionsCheckBox.setDisable(true);
        useFixedPositionsCheckBox.selectedProperty().addListener((obs, old, newVal) -> {
            applyTemplateBtn.setDisable(!newVal || currentTemplate == null);
        });

        templateBar.getChildren().addAll(saveTemplateBtn, loadTemplateBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                useFixedPositionsCheckBox, applyTemplateBtn);

        // Metadata preview
        Label previewLabel = new Label(resources.getString("label.metadataPreview"));
        previewLabel.setStyle("-fx-font-weight: bold;");

        metadataPreview = new TextArea();
        metadataPreview.setEditable(false);
        metadataPreview.setPrefRowCount(3);
        metadataPreview.setStyle("-fx-font-family: monospace;");

        panel.getChildren().addAll(titleLabel, fieldsTable, buttonBar, templateBar, previewLabel, metadataPreview);
        return panel;
    }

    private HBox createButtonBar() {
        HBox buttonBar = new HBox(15);
        buttonBar.setPadding(new Insets(10));
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        // Show selected entry name
        Label selectedLabel = new Label();
        selectedLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        entryListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                selectedLabel.setText("Applying to: " + newVal.getImageName());
            } else {
                selectedLabel.setText("");
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button applyButton = new Button(resources.getString("button.apply"));
        applyButton.setDefaultButton(true);
        applyButton.setOnAction(e -> applyMetadata());

        Button cancelButton = new Button(resources.getString("button.cancel"));
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> stage.close());

        buttonBar.getChildren().addAll(selectedLabel, spacer, applyButton, cancelButton);
        return buttonBar;
    }

    private void runOCR() {
        if (labelImage == null) {
            Dialogs.showWarningNotification("No Label Image",
                    "Please select an image with a label first.");
            return;
        }

        progressIndicator.setVisible(true);

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

    private BufferedImage preprocessForOCR(BufferedImage source) {
        BufferedImage result = source;
        if (invertCheckBox.isSelected()) {
            result = invertImage(result);
        }
        return result;
    }

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
                String suggestedKey = findMatchingMetadataKey(block.getBoundingBox(), prefix + "field_" + index);
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
                    String suggestedKey = findMatchingMetadataKey(block.getBoundingBox(), prefix + "field_" + index);
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

    /**
     * Finds a metadata key from previous field entries if the bounding box overlaps by at least 50%.
     * This preserves user-defined metadata keys when switching between slides with similar label layouts.
     *
     * @param newBox The bounding box of the new field
     * @param defaultKey The default key to use if no match is found
     * @return The matched metadata key or the default key
     */
    private String findMatchingMetadataKey(BoundingBox newBox, String defaultKey) {
        if (newBox == null || previousFieldEntries.isEmpty()) {
            logger.info("findMatchingMetadataKey: no previous entries or null box, using default: {}", defaultKey);
            return defaultKey;
        }

        // Need both current and previous image dimensions for proper normalization
        if (labelImage == null || previousImageWidth <= 0 || previousImageHeight <= 0) {
            logger.info("findMatchingMetadataKey: missing image dimensions (prev={}x{}), using default: {}",
                    previousImageWidth, previousImageHeight, defaultKey);
            return defaultKey;
        }

        logger.info("findMatchingMetadataKey: checking {} previous entries for overlap with new box at ({}, {}, {}, {})",
                previousFieldEntries.size(), newBox.getX(), newBox.getY(), newBox.getWidth(), newBox.getHeight());

        // Normalize NEW bounding box using CURRENT image dimensions
        double currImgWidth = labelImage.getWidth();
        double currImgHeight = labelImage.getHeight();

        double newNormX = newBox.getX() / currImgWidth;
        double newNormY = newBox.getY() / currImgHeight;
        double newNormW = newBox.getWidth() / currImgWidth;
        double newNormH = newBox.getHeight() / currImgHeight;
        double newArea = newNormW * newNormH;

        for (OCRFieldEntry prevEntry : previousFieldEntries) {
            BoundingBox prevBox = prevEntry.getBoundingBox();
            if (prevBox == null) continue;

            // Normalize PREVIOUS bounding box using PREVIOUS image dimensions
            double prevNormX = prevBox.getX() / previousImageWidth;
            double prevNormY = prevBox.getY() / previousImageHeight;
            double prevNormW = prevBox.getWidth() / previousImageWidth;
            double prevNormH = prevBox.getHeight() / previousImageHeight;
            double prevArea = prevNormW * prevNormH;

            // Calculate intersection (both are now in 0-1 normalized space)
            double interX1 = Math.max(newNormX, prevNormX);
            double interY1 = Math.max(newNormY, prevNormY);
            double interX2 = Math.min(newNormX + newNormW, prevNormX + prevNormW);
            double interY2 = Math.min(newNormY + newNormH, prevNormY + prevNormH);

            double interW = Math.max(0, interX2 - interX1);
            double interH = Math.max(0, interY2 - interY1);
            double interArea = interW * interH;

            // Check if overlap is at least 50% of the smaller box
            double minArea = Math.min(newArea, prevArea);
            double overlapPercent = minArea > 0 ? (interArea / minArea) * 100 : 0;
            String prevKey = prevEntry.getMetadataKey();

            logger.info("  Comparing with prev entry '{}' at norm({},{},{},{}) - overlap: {}%",
                    prevKey,
                    String.format("%.3f", prevNormX), String.format("%.3f", prevNormY),
                    String.format("%.3f", prevNormW), String.format("%.3f", prevNormH),
                    String.format("%.1f", overlapPercent));

            if (minArea > 0 && interArea / minArea >= 0.5) {
                // Only reuse non-default keys (user has customized them)
                if (prevKey != null && !prevKey.isEmpty()) {
                    logger.info("  -> MATCH! Reusing metadata key '{}'", prevKey);
                    return prevKey;
                }
            }
        }

        logger.info("  -> No match found, using default: {}", defaultKey);
        return defaultKey;
    }

    private void drawBoundingBoxes() {
        if (overlayCanvas == null || labelImage == null) return;

        updateCanvasSize();

        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());

        // Draw selection rectangle if in selection mode
        if (hasSelection || regionSelectionMode) {
            drawSelectionRectangle(gc);
        }

        if (fieldEntries.isEmpty()) return;

        // Calculate scale factor
        double scaleX = imageView.getBoundsInLocal().getWidth() / labelImage.getWidth();
        double scaleY = imageView.getBoundsInLocal().getHeight() / labelImage.getHeight();
        double scale = Math.min(scaleX, scaleY);

        int index = 0;
        for (OCRFieldEntry entry : fieldEntries) {
            BoundingBox bbox = entry.getBoundingBox();
            if (bbox == null) continue;

            double x = bbox.getX() * scale;
            double y = bbox.getY() * scale;
            double w = bbox.getWidth() * scale;
            double h = bbox.getHeight() * scale;

            if (index == selectedIndex) {
                gc.setStroke(Color.YELLOW);
                gc.setLineWidth(3);
            } else {
                gc.setStroke(Color.LIME);
                gc.setLineWidth(2);
            }
            gc.strokeRect(x, y, w, h);

            gc.setFill(Color.rgb(0, 0, 0, 0.7));
            gc.fillRect(x, y - 16, Math.min(w, 60), 16);
            gc.setFill(Color.WHITE);
            gc.fillText(String.valueOf(index + 1), x + 3, y - 3);

            index++;
        }
    }

    private void drawSelectionRectangle(GraphicsContext gc) {
        if (!hasSelection && !regionSelectionMode) return;

        double x = Math.min(selectionStartX, selectionEndX);
        double y = Math.min(selectionStartY, selectionEndY);
        double w = Math.abs(selectionEndX - selectionStartX);
        double h = Math.abs(selectionEndY - selectionStartY);

        if (w < 2 || h < 2) return;

        gc.setFill(Color.rgb(0, 120, 255, 0.2));
        gc.fillRect(x, y, w, h);

        gc.setStroke(Color.rgb(0, 120, 255));
        gc.setLineWidth(2);
        gc.setLineDashes(5, 5);
        gc.strokeRect(x, y, w, h);
        gc.setLineDashes(null);

        gc.setFill(Color.rgb(0, 120, 255, 0.9));
        gc.fillRect(x, y - 18, 80, 18);
        gc.setFill(Color.WHITE);
        gc.fillText("Selection", x + 4, y - 4);
    }

    private void updateScanRegionButton() {
        scanRegionButton.setDisable(!hasSelection || labelImage == null);
    }

    private void scanSelectedRegion() {
        if (!hasSelection || labelImage == null) {
            Dialogs.showWarningNotification("No Selection",
                    "Please draw a rectangle on the image first.");
            return;
        }

        double scaleX = imageView.getBoundsInLocal().getWidth() / labelImage.getWidth();
        double scaleY = imageView.getBoundsInLocal().getHeight() / labelImage.getHeight();
        double scale = Math.min(scaleX, scaleY);

        int imgX = (int) Math.max(0, Math.min(selectionStartX, selectionEndX) / scale);
        int imgY = (int) Math.max(0, Math.min(selectionStartY, selectionEndY) / scale);
        int imgW = (int) Math.min(labelImage.getWidth() - imgX, Math.abs(selectionEndX - selectionStartX) / scale);
        int imgH = (int) Math.min(labelImage.getHeight() - imgY, Math.abs(selectionEndY - selectionStartY) / scale);

        if (imgW < 5 || imgH < 5) {
            Dialogs.showWarningNotification("Selection Too Small",
                    "Please draw a larger selection area.");
            return;
        }

        logger.info("Scanning region: x={}, y={}, w={}, h={}", imgX, imgY, imgW, imgH);

        BufferedImage regionImage = labelImage.getSubimage(imgX, imgY, imgW, imgH);

        if (invertCheckBox.isSelected()) {
            regionImage = invertImage(regionImage);
        }

        progressIndicator.setVisible(true);

        OCRConfiguration config = OCRConfiguration.builder()
                .pageSegMode(OCRConfiguration.PageSegMode.SPARSE_TEXT)
                .language(OCRPreferences.getLanguage())
                .minConfidence(0.1)
                .autoRotate(OCRPreferences.isAutoRotate())
                .detectOrientation(OCRPreferences.isDetectOrientation())
                .enhanceContrast(thresholdCheckBox.isSelected())
                .enablePreprocessing(true)
                .build();

        final int offsetX = imgX;
        final int offsetY = imgY;

        OCRController.getInstance().performOCRAsync(regionImage, config)
                .thenAccept(result -> Platform.runLater(() -> {
                    progressIndicator.setVisible(false);

                    if (result.getBlockCount() == 0) {
                        Dialogs.showInfoNotification("Region Scan Complete",
                                "No text detected in the selected region.\n\n" +
                                "Try:\n" +
                                "- Selecting a tighter area around the text\n" +
                                "- Toggling the Invert checkbox\n" +
                                "- Making sure the text is clearly visible");
                    } else {
                        addRegionResults(result, offsetX, offsetY);
                        Dialogs.showInfoNotification("Region Scan Complete",
                                String.format("Found %d text blocks in the selected region.",
                                        result.getBlockCount()));
                    }

                    selectRegionButton.setSelected(false);
                    regionSelectionMode = false;
                    hasSelection = false;
                    drawBoundingBoxes();
                    updateScanRegionButton();
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        progressIndicator.setVisible(false);
                        Dialogs.showErrorMessage("Region Scan Failed", ex.getMessage());
                    });
                    return null;
                });
    }

    private void addRegionResults(OCRResult result, int offsetX, int offsetY) {
        String prefix = OCRPreferences.getMetadataPrefix();
        int startIndex = fieldEntries.size();

        for (TextBlock block : result.getTextBlocks()) {
            if (block.getType() == TextBlock.BlockType.LINE && !block.isEmpty()) {
                String suggestedKey = prefix + "region_" + startIndex;

                BoundingBox originalBox = block.getBoundingBox();
                BoundingBox adjustedBox = null;
                if (originalBox != null) {
                    adjustedBox = new BoundingBox(
                            originalBox.getX() + offsetX,
                            originalBox.getY() + offsetY,
                            originalBox.getWidth(),
                            originalBox.getHeight()
                    );
                }

                OCRFieldEntry entry = new OCRFieldEntry(
                        block.getText(),
                        suggestedKey,
                        block.getConfidence(),
                        adjustedBox
                );
                fieldEntries.add(entry);
                startIndex++;
            }
        }

        // If no lines, try words
        if (startIndex == fieldEntries.size()) {
            for (TextBlock block : result.getTextBlocks()) {
                if (block.getType() == TextBlock.BlockType.WORD && !block.isEmpty()) {
                    String suggestedKey = prefix + "region_" + startIndex;

                    BoundingBox originalBox = block.getBoundingBox();
                    BoundingBox adjustedBox = null;
                    if (originalBox != null) {
                        adjustedBox = new BoundingBox(
                                originalBox.getX() + offsetX,
                                originalBox.getY() + offsetY,
                                originalBox.getWidth(),
                                originalBox.getHeight()
                        );
                    }

                    OCRFieldEntry entry = new OCRFieldEntry(
                            block.getText(),
                            suggestedKey,
                            block.getConfidence(),
                            adjustedBox
                    );
                    fieldEntries.add(entry);
                    startIndex++;
                }
            }
        }

        updateMetadataPreview();
        drawBoundingBoxes();
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

    /**
     * Saves current field entries as a template with bounding box positions.
     */
    private void saveTemplate() {
        if (fieldEntries.isEmpty()) {
            Dialogs.showWarningNotification("No Fields",
                    "Run OCR first to detect fields before saving a template.");
            return;
        }

        if (labelImage == null) {
            Dialogs.showWarningNotification("No Label Image",
                    "A label image is required to save bounding box positions.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save OCR Template");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("OCR Template (*.json)", "*.json"));
        chooser.setInitialFileName("ocr_template.json");

        File file = chooser.showSaveDialog(stage);
        if (file == null) return;

        // Create template from current fields
        String templateName = file.getName().replace(".json", "");
        OCRTemplate template = new OCRTemplate(templateName);

        // Build current OCR configuration
        PSMOption selectedPSM = psmCombo.getValue();
        OCRConfiguration config = OCRConfiguration.builder()
                .pageSegMode(selectedPSM != null ? selectedPSM.getMode() : OCRConfiguration.PageSegMode.AUTO)
                .language(OCRPreferences.getLanguage())
                .minConfidence(confSlider.getValue() / 100.0)
                .enhanceContrast(thresholdCheckBox.isSelected())
                .build();
        template.setConfiguration(config);

        // Add field mappings with normalized bounding boxes
        int imgWidth = labelImage.getWidth();
        int imgHeight = labelImage.getHeight();

        for (int i = 0; i < fieldEntries.size(); i++) {
            OCRFieldEntry entry = fieldEntries.get(i);
            String key = entry.getMetadataKey();
            String text = entry.getText();
            BoundingBox bbox = entry.getBoundingBox();

            OCRTemplate.FieldMapping mapping;
            if (bbox != null) {
                // Normalize coordinates to 0-1 range
                double normX = bbox.getX() / (double) imgWidth;
                double normY = bbox.getY() / (double) imgHeight;
                double normW = bbox.getWidth() / (double) imgWidth;
                double normH = bbox.getHeight() / (double) imgHeight;

                mapping = new OCRTemplate.FieldMapping(i, key, text, normX, normY, normW, normH);
            } else {
                mapping = new OCRTemplate.FieldMapping(i, key, text);
            }
            template.addFieldMapping(mapping);
        }

        template.setUseFixedPositions(true);
        template.setDilationFactor(1.2); // 20% dilation

        try {
            template.saveToFile(file);
            currentTemplate = template;
            useFixedPositionsCheckBox.setDisable(false);
            useFixedPositionsCheckBox.setSelected(true);

            Dialogs.showInfoNotification("Template Saved",
                    String.format("Saved template '%s' with %d field positions.",
                            templateName, template.getFieldMappings().size()));
        } catch (IOException e) {
            logger.error("Error saving template", e);
            Dialogs.showErrorMessage("Save Error", "Failed to save template: " + e.getMessage());
        }
    }

    /**
     * Loads a template from file.
     */
    private void loadTemplate() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load OCR Template");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("OCR Template (*.json)", "*.json"));

        File file = chooser.showOpenDialog(stage);
        if (file == null) return;

        try {
            currentTemplate = OCRTemplate.loadFromFile(file);

            // Update UI to show template info
            useFixedPositionsCheckBox.setDisable(!currentTemplate.hasBoundingBoxData());
            useFixedPositionsCheckBox.setSelected(currentTemplate.hasBoundingBoxData());

            // Show template fields as a preview
            fieldEntries.clear();
            String prefix = OCRPreferences.getMetadataPrefix();
            for (OCRTemplate.FieldMapping mapping : currentTemplate.getFieldMappings()) {
                OCRFieldEntry entry = new OCRFieldEntry(
                        mapping.getExampleText() != null ? mapping.getExampleText() : "",
                        mapping.getMetadataKey() != null ? mapping.getMetadataKey() : prefix + "field_" + mapping.getFieldIndex(),
                        1.0f,
                        null // Will be populated when applying template
                );
                fieldEntries.add(entry);
            }
            updateMetadataPreview();

            Dialogs.showInfoNotification("Template Loaded",
                    String.format("Loaded template '%s' with %d fields.%s",
                            currentTemplate.getName(),
                            currentTemplate.getFieldMappings().size(),
                            currentTemplate.hasBoundingBoxData() ? "\nFixed positions available." : ""));
        } catch (IOException e) {
            logger.error("Error loading template", e);
            Dialogs.showErrorMessage("Load Error", "Failed to load template: " + e.getMessage());
        }
    }

    /**
     * Applies the loaded template to extract text from fixed positions.
     */
    private void applyTemplateToCurrentImage() {
        if (currentTemplate == null) {
            Dialogs.showWarningNotification("No Template",
                    "Please load a template first.");
            return;
        }

        if (labelImage == null) {
            Dialogs.showWarningNotification("No Label Image",
                    "Please select an image with a label first.");
            return;
        }

        if (!currentTemplate.hasBoundingBoxData()) {
            Dialogs.showWarningNotification("No Position Data",
                    "The loaded template does not contain bounding box positions.\n" +
                    "Use regular OCR instead, or load a different template.");
            return;
        }

        progressIndicator.setVisible(true);
        fieldEntries.clear();

        int imgWidth = labelImage.getWidth();
        int imgHeight = labelImage.getHeight();
        double dilation = currentTemplate.getDilationFactor();

        // Build OCR config - use very low confidence for fixed positions
        OCRConfiguration config = OCRConfiguration.builder()
                .pageSegMode(OCRConfiguration.PageSegMode.SINGLE_BLOCK)
                .language(OCRPreferences.getLanguage())
                .minConfidence(0.1) // Very low threshold for fixed positions
                .enhanceContrast(thresholdCheckBox.isSelected())
                .build();

        // Process each field mapping
        java.util.List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();

        for (OCRTemplate.FieldMapping mapping : currentTemplate.getFieldMappings()) {
            if (!mapping.isEnabled() || !mapping.hasBoundingBox()) continue;

            int[] box = mapping.getScaledBoundingBox(imgWidth, imgHeight, dilation);
            if (box == null || box[2] < 5 || box[3] < 5) continue;

            // Extract region
            BufferedImage regionImage;
            try {
                regionImage = labelImage.getSubimage(box[0], box[1], box[2], box[3]);
            } catch (Exception e) {
                logger.warn("Failed to extract region for field {}: {}", mapping.getFieldIndex(), e.getMessage());
                continue;
            }

            // Apply inversion if needed
            if (invertCheckBox.isSelected()) {
                regionImage = invertImage(regionImage);
            }

            final BufferedImage finalRegion = regionImage;
            final int fieldIndex = mapping.getFieldIndex();
            final String metadataKey = mapping.getMetadataKey();
            final int[] finalBox = box;

            java.util.concurrent.CompletableFuture<Void> future = OCRController.getInstance()
                    .performOCRAsync(finalRegion, config)
                    .thenAccept(result -> {
                        String extractedText = "";
                        if (result.hasText()) {
                            // Get all text from the region
                            extractedText = result.getTextBlocks().stream()
                                    .map(TextBlock::getText)
                                    .filter(t -> t != null && !t.isEmpty())
                                    .reduce((a, b) -> a + " " + b)
                                    .orElse("");
                        }

                        final String text = extractedText.trim();
                        Platform.runLater(() -> {
                            BoundingBox bbox = new BoundingBox(finalBox[0], finalBox[1], finalBox[2], finalBox[3]);
                            OCRFieldEntry entry = new OCRFieldEntry(text, metadataKey, 1.0f, bbox);
                            fieldEntries.add(entry);
                        });
                    });

            futures.add(future);
        }

        // Wait for all extractions to complete
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                .thenRun(() -> Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    // Sort by field index
                    fieldEntries.sort((a, b) -> {
                        int idxA = Integer.parseInt(a.getMetadataKey().replaceAll("\\D+", ""));
                        int idxB = Integer.parseInt(b.getMetadataKey().replaceAll("\\D+", ""));
                        return Integer.compare(idxA, idxB);
                    });
                    updateMetadataPreview();
                    drawBoundingBoxes();
                    Dialogs.showInfoNotification("Template Applied",
                            String.format("Extracted text from %d fixed positions.", fieldEntries.size()));
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        progressIndicator.setVisible(false);
                        Dialogs.showErrorMessage("Template Apply Failed", ex.getMessage());
                    });
                    return null;
                });
    }

    private void applyMetadata() {
        if (selectedEntry == null) {
            Dialogs.showWarningNotification("No Image Selected",
                    "Please select an image from the project list.");
            return;
        }

        if (labelImage == null) {
            Dialogs.showWarningNotification("No Label Image",
                    "The selected image has no label. Cannot apply OCR metadata.");
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

        int count = OCRMetadataManager.setMetadataBatch(selectedEntry, metadata, project);

        if (count > 0) {
            // Add workflow step if this is the currently open image
            addWorkflowStep(fieldMappings);

            Dialogs.showInfoNotification("Metadata Applied",
                    String.format("Applied %d metadata fields to: %s", count, selectedEntry.getImageName()));

            // Don't close - allow user to continue with other images
        } else {
            Dialogs.showErrorMessage("Apply Failed",
                    "Failed to apply metadata. Check the log for details.");
        }
    }

    private void addWorkflowStep(Map<Integer, String> fieldMappings) {
        // Get ImageData for the selected entry to add workflow step
        ImageData<?> imageData = null;

        // First check if selected entry is the currently open image
        ImageData<?> currentImageData = qupath.getImageData();
        if (currentImageData != null && selectedEntry != null) {
            try {
                var currentServer = currentImageData.getServer();
                if (currentServer != null) {
                    var currentUris = currentServer.getURIs();
                    var entryUris = selectedEntry.getURIs();
                    String currentUri = currentUris.isEmpty() ? null : currentUris.iterator().next().toString();
                    String entryUri = entryUris.isEmpty() ? null : entryUris.iterator().next().toString();

                    if (currentUri != null && currentUri.equals(entryUri)) {
                        // Use the already-open ImageData
                        imageData = currentImageData;
                    }
                }
            } catch (Exception e) {
                logger.debug("Error comparing URIs: {}", e.getMessage());
            }
        }

        // If not the current image, load ImageData from the entry
        if (imageData == null && selectedEntry != null) {
            try {
                imageData = selectedEntry.readImageData();
            } catch (Exception e) {
                logger.warn("Could not read ImageData for workflow step: {}", e.getMessage());
                return;
            }
        }

        if (imageData == null) {
            logger.debug("No ImageData available for workflow step");
            return;
        }

        PSMOption selectedPSM = psmCombo.getValue();
        boolean invert = invertCheckBox.isSelected();
        boolean enhance = thresholdCheckBox.isSelected();
        double confidence = confSlider.getValue() / 100.0;

        StringBuilder script = new StringBuilder();
        script.append("// OCR for Labels - Auto-generated script\n");
        script.append("// Run this script to reproduce OCR results on similar images\n");
        script.append("import qupath.ext.ocr4labels.OCR4Labels\n\n");

        script.append("if (!OCR4Labels.hasLabelImage()) {\n");
        script.append("    println \"No label image available\"\n");
        script.append("    return\n");
        script.append("}\n\n");

        script.append("def results = OCR4Labels.builder()\n");

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

        if (enhance) {
            script.append("    .enhance()\n");
        } else {
            script.append("    .noEnhance()\n");
        }

        if (invert) {
            script.append("    .invert()\n");
        }

        script.append("    .minConfidence(").append(String.format("%.2f", confidence)).append(")\n");
        script.append("    .run()\n\n");

        script.append("// Apply detected text to metadata fields\n");
        for (Map.Entry<Integer, String> entry : fieldMappings.entrySet()) {
            int idx = entry.getKey();
            String key = entry.getValue();
            script.append("if (results.size() > ").append(idx).append(") {\n");
            script.append("    OCR4Labels.setMetadataValue(\"").append(key).append("\", results[").append(idx).append("])\n");
            script.append("}\n");
        }

        script.append("\nprintln \"Applied \" + results.size() + \" OCR fields\"\n");

        try {
            imageData.getHistoryWorkflow().addStep(
                    new DefaultScriptableWorkflowStep(
                            "OCR Label Recognition",
                            script.toString()
                    )
            );
            logger.info("Added OCR workflow step for: {}", selectedEntry.getImageName());

            // Save the ImageData if it's not the currently open image
            if (imageData != currentImageData) {
                // Use helper method to handle generic type safely
                saveImageDataToEntry(selectedEntry, imageData);
                logger.debug("Saved ImageData with workflow step");
            }
        } catch (Exception e) {
            logger.warn("Failed to add workflow step: {}", e.getMessage());
        }
    }

    /**
     * Helper method to save ImageData to a project entry, handling generic type casting.
     * This is safe because the ImageData was read from the same entry.
     */
    @SuppressWarnings("unchecked")
    private <T> void saveImageDataToEntry(ProjectImageEntry<T> entry, ImageData<?> imageData) throws Exception {
        entry.saveImageData((ImageData<T>) imageData);
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
