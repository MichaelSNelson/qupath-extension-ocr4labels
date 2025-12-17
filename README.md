# QuPath Extension: OCR for Labels

A QuPath extension that performs OCR (Optical Character Recognition) on slide label images and allows users to create metadata fields from detected text. Supports single-image processing, batch operations across entire projects, template-based workflows, and intelligent text correction using vocabulary matching.

## Features

- **Label Image Access**: Extract and display label images from whole slide image files
- **Tesseract OCR Integration**: High-quality text recognition via Tess4J wrapper
- **Interactive GUI**: Review, edit, and assign detected text to metadata fields
- **Project Navigation**: Browse all project images without closing the dialog
- **Batch Processing**: Apply OCR templates across entire projects
- **Template System**: Save and reuse field positions and metadata key assignments
- **Text Filtering**: Clean up OCR results with one-click character filters
- **Vocabulary Matching**: Correct OCR errors by matching against known valid values
- **Rotated Label Support**: Automatic orientation detection for sideways or upside-down labels

## Requirements

- QuPath 0.6.0 or later
- Java 21+
- Tesseract language data files (see [Setup](#setup))

## Installation

1. Download the latest release JAR file
2. Drag and drop into QuPath, or copy to your QuPath extensions folder
3. Restart QuPath

## Setup

Before using OCR, you need to download Tesseract language data files:

1. Go to **Extensions > OCR for Labels > OCR Settings...**
2. In the **Required Downloads** section:
   - Click **eng.traineddata** to download the English language file (~4 MB)
   - Optionally click **osd.traineddata** for orientation detection (~10 MB)
3. Set the **Tessdata Path** to the folder containing the downloaded files
4. Click **OK** to save settings

## Usage

### Single Image OCR

1. Open an image with a label in QuPath
2. Go to **Extensions > OCR for Labels > Run OCR on Label**
3. The OCR Dialog opens showing all project images on the left
4. Select an image from the list (or use the currently open image)
5. Click **Run OCR** to detect text on the label
6. Review detected text blocks in the table:
   - Edit the **Text** column to correct OCR mistakes
   - Edit the **Metadata Key** column to set field names
7. Click **Apply** to save metadata to the selected image

### Batch Processing

1. Open a project with multiple images
2. Go to **Extensions > OCR for Labels > Run OCR on Project...**
3. Create or load a template:
   - Click **Create from Current Image** to use the single-image dialog
   - Or click **Load Template...** to use a previously saved template
4. Review field mappings in the template table
5. Click **Process All** to run OCR on all images
6. Review and edit results in the results table
7. Click **Apply Metadata** to save to all images

---

## OCR Dialog Reference

### Toolbar Controls

| Control | Description |
|---------|-------------|
| **Run OCR** | Performs OCR on the current label image |
| **Mode** | Page segmentation mode - controls how Tesseract analyzes the image layout |
| **Min Conf** | Minimum confidence threshold (0-100%) - text below this is filtered out |
| **Invert** | Inverts image colors - use for labels with light text on dark backgrounds |
| **Enhance** | Improves image contrast before OCR - recommended for faded labels |
| **Select Region** | Toggle to draw a rectangle and scan only that area |
| **Scan Region** | Runs OCR on the selected region only |

### Page Segmentation Modes

| Mode | Best For |
|------|----------|
| **Auto** | General purpose, lets Tesseract decide |
| **Auto + Orientation** | Auto mode with rotation detection |
| **Single Block** | Labels with one block of text |
| **Single Line** | Single-line text like serial numbers |
| **Single Word** | Individual words or short codes |
| **Sparse Text** | Labels with scattered text at various positions (recommended) |
| **Sparse + Orientation** | Sparse text with rotation detection |

### Image Panel

| Control | Description |
|---------|-------------|
| **Fit** | Scales image to fit the panel |
| **100%** | Shows image at actual pixel size |
| **Mouse Drag** | When "Select Region" is active, drag to draw selection rectangle |

### Detected Fields Table

| Column | Description |
|--------|-------------|
| **Text** | The detected text (editable - click to correct OCR errors) |
| **Metadata Key** | The metadata field name (editable - set your custom key names) |
| **Conf** | OCR confidence percentage |

### Field Buttons

| Button | Description |
|--------|-------------|
| **Add Field** | Manually add a new empty field row |
| **Remove** | Delete the selected field row |
| **Clear All** | Remove all detected fields |

### Text Filter Bar

Quick-access buttons to clean up detected text:

| Button | Name | Action |
|--------|------|--------|
| `abcABC` | Letters Only | Removes numbers, symbols, whitespace |
| `123` | Numbers Only | Keeps only digits 0-9 |
| `aA1` | Alphanumeric | Keeps letters and numbers |
| `-_.` | Filename Safe | Keeps characters valid in filenames |
| `&*!` | Standard Chars | Removes unusual/control characters |
| `_ _` | No Whitespace | Replaces spaces with underscores |

Each filter button has a tooltip showing the exact regex pattern used.

### Vocabulary Matching

Correct OCR errors by matching against a list of known valid values:

| Control | Description |
|---------|-------------|
| **Load List...** | Load a vocabulary file (CSV, TSV, or TXT) |
| **?** | Help tooltip explaining vocabulary matching |
| **OCR weights** | Toggle OCR-aware character weighting (see below) |
| **Match** | Apply vocabulary matching to all fields |
| *(N entries)* | Shows number of loaded vocabulary items |

#### Vocabulary File Format

The vocabulary loader accepts:
- **CSV files**: Uses first column, handles quoted values
- **TSV files**: Uses first column (tab-separated)
- **TXT files**: One value per line

Header rows are automatically skipped if they contain keywords like: sample, name, id, code, label, value, specimen, patient, slide, case, date.

#### OCR Weights Toggle

**OFF (default)**: Standard matching - all character substitutions have equal cost.
- Best for scientific sample names where letter/number mixtures are intentional
- `PBS_O1` and `PBS_01` are treated as significantly different

**ON**: OCR-weighted matching - common OCR confusions have reduced penalty:
- `0` ↔ `O` (zero vs letter O): 0.3 cost
- `1` ↔ `l` ↔ `I` (one vs L vs I): 0.3 cost
- `5` ↔ `S`, `8` ↔ `B`, `2` ↔ `Z`: 0.5 cost
- Best for natural text where OCR errors are likely mistakes

### Template Bar

| Control | Description |
|---------|-------------|
| **Save Template...** | Save field positions and metadata keys to JSON file |
| **Load Template...** | Load a previously saved template |
| **Use Fixed Positions** | When checked, uses template bounding boxes instead of running OCR |
| **Apply Template** | Extracts text from fixed positions (with 20% dilation for tolerance) |

Templates are saved as JSON files and can be shared between users or sessions.

### Bottom Controls

| Control | Description |
|---------|-------------|
| **Applying to: [name]** | Shows which image will receive the metadata |
| **Apply** | Saves the metadata to the selected project image |
| **Cancel** | Closes the dialog without saving |

---

## Batch OCR Dialog Reference

### Header Section

Displays information about the current project:
- Number of images with labels found
- Total images in project
- Step-by-step workflow instructions

### Template Section

| Control | Description |
|---------|-------------|
| **Create from Current Image** | Opens single-image OCR dialog to create a template |
| **Load Template...** | Load template from JSON file |
| **Save Template...** | Save current field mappings to JSON file |

#### Template Table Columns

| Column | Description |
|--------|-------------|
| **Use** | Checkbox to enable/disable each field |
| **Field #** | Sequential field number |
| **Metadata Key** | The metadata field name |
| **Example Text** | Sample text from when template was created |

### Results Section

After clicking **Process All**, results appear in the table:

| Column | Description |
|--------|-------------|
| **Image Name** | Project image filename |
| **Status** | Processing status: Pending, Processing..., Done, Error, Applied |
| **[Field columns]** | One editable column per enabled template field |

Results table columns are editable - click any cell to correct values before applying.

### Filter Bar

Same text filters and vocabulary matching as the single-image dialog:
- Character filter buttons
- **Load List...** / **?** / **OCR weights** / **Match All**

The **Match All** button applies vocabulary matching across ALL processed images at once.

### Progress Section

| Control | Description |
|---------|-------------|
| **Progress Bar** | Visual progress during batch processing |
| **Status Label** | Real-time status updates (e.g., "Processing 5 of 20: image.svs") |

### Bottom Controls

| Control | Description |
|---------|-------------|
| **Process All** | Run OCR on all images using current template |
| **Apply Metadata** | Save metadata to all successfully processed images |
| **Cancel** | Close dialog and cancel any running processing |

---

## OCR Settings Dialog Reference

Access via **Extensions > OCR for Labels > OCR Settings...**

### Required Downloads

| Link | Description |
|------|-------------|
| **eng.traineddata** | English language data (~4 MB) - Required |
| **osd.traineddata** | Orientation/script detection (~10 MB) - Optional but recommended |
| **Browse all languages** | Link to Tesseract tessdata repository |

Status indicators show `[Installed]` or `[Not found]` for each file.

### Tessdata Location

| Setting | Description |
|---------|-------------|
| **Tessdata Path** | Folder containing .traineddata files |
| **Browse** | Opens folder chooser |
| **Language** | Language code (e.g., `eng`, `deu`, `fra`, `chi_sim`) |
| **Label Keywords** | Comma-separated keywords to identify label images in metadata |

### Text Detection Settings

| Setting | Description |
|---------|-------------|
| **Detection Mode** | Default page segmentation mode |
| **Confidence Threshold** | Default minimum confidence (0-100%) |

### Image Enhancement

| Setting | Description |
|---------|-------------|
| **Detect Text Orientation** | Enable automatic rotation detection |
| **Auto-Rotate** | Automatically correct rotated text |
| **Enhance Contrast** | Improve visibility for faded labels |
| **Auto-Run OCR** | Automatically run OCR when switching images in the dialog |

### QuPath Metadata

| Setting | Description |
|---------|-------------|
| **Key Prefix** | Text prepended to all metadata field names (e.g., `ocr_`) |

### Other Controls

| Control | Description |
|---------|-------------|
| **Reset to Defaults** | Restore all settings to original values |

---

## Workflow Examples

### Example 1: Basic Single-Image OCR

1. Open a slide with a label
2. **Extensions > OCR for Labels > Run OCR on Label**
3. Click **Run OCR**
4. Edit metadata keys: `PBS_B_010` → Key: `Sample_ID`
5. Click **Apply**
6. Check metadata in QuPath's image properties

### Example 2: Batch Processing with Template

1. Open a project with 50+ slides
2. **Extensions > OCR for Labels > Run OCR on Project...**
3. Click **Create from Current Image**
4. In the OCR dialog, run OCR and set metadata keys
5. Click **Save Template...** → save as `lab_template.json`
6. Close OCR dialog
7. Click **Load Template...** → select `lab_template.json`
8. Click **Process All** (wait for completion)
9. Review results, edit any errors
10. Click **Apply Metadata**

### Example 3: Vocabulary Matching for Sample Names

You have a spreadsheet of expected sample names and OCR sometimes misreads them:

1. Export sample names to `samples.txt`:
   ```
   PBS_001
   PBS_002
   Sample_A1
   Sample_B2
   ```
2. In OCR dialog, run OCR on label
3. Click **Load List...** → select `samples.txt`
4. Status shows "(4 entries)"
5. If OCR detected `PBS_0O1`, click **Match**
6. Value corrects to `PBS_001`

For natural text (not scientific codes), enable **OCR weights** checkbox before matching.

---

## Troubleshooting

### No text detected

- Try different **Mode** settings (Sparse Text often works best)
- Lower the **Min Conf** slider
- Enable **Enhance** for faded labels
- Check **Invert** for light-on-dark text

### Rotated or upside-down text

- Download `osd.traineddata` in Settings
- Enable **Detect Text Orientation** in Settings
- Use **Auto + Orientation** or **Sparse + Orientation** mode

### Wrong characters detected

- Use text filters to clean up results
- Load a vocabulary file and use **Match** to correct
- Try enabling/disabling **OCR weights** depending on your text type

### Template not matching new images

- Ensure labels are consistently positioned
- The 20% dilation helps with slight variations
- For very different layouts, create a new template

---

## Building from Source

```bash
./gradlew build
```

The extension JAR will be created in `build/libs/`.

## License

MIT License - see [LICENSE](LICENSE) for details.

## Acknowledgments

- [Tesseract OCR](https://github.com/tesseract-ocr/tesseract) - Open source OCR engine
- [Tess4J](https://github.com/nguyenq/tess4j) - Java JNA wrapper for Tesseract
- [QuPath](https://qupath.github.io/) - Open source software for bioimage analysis
