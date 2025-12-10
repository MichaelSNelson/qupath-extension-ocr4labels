# QuPath Extension: OCR for Labels

A QuPath extension that performs OCR (Optical Character Recognition) on slide label images and allows users to create metadata fields from detected text.

## Features

- Access label images from whole slide image files
- Perform OCR using Tesseract (via Tess4J)
- Interactive GUI to review and label detected text regions
- Create custom metadata fields from OCR results
- Apply OCR configuration across all images in a project
- Support for rotated labels with automatic orientation detection

## Requirements

- QuPath 0.6.0 or later
- Java 21+

## Installation

1. Download the latest release JAR file
2. Drag and drop into QuPath, or copy to your QuPath extensions folder

## Usage

1. Open an image with a label in QuPath
2. Go to **Extensions > OCR for Labels > Run OCR on Label**
3. Review detected text blocks and assign metadata keys
4. Click **Apply** to create metadata entries

For batch processing:
1. Open a project with multiple images
2. Go to **Extensions > OCR for Labels > Run OCR on Project...**
3. Configure settings and apply to all images

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
