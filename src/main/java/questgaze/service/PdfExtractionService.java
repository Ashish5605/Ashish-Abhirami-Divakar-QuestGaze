
package questgaze.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public class PdfExtractionService {

    private static final String TESSERACT_PATH =
            "C:\\Program Files\\Tesseract-OCR";

    // Prevent ImageIO from using disk-backed image cache streams.
    static {
        ImageIO.setUseCache(false);
    }

    public String extractText(Path pdfPath)
            throws IOException, TesseractException {

        StringBuilder result = new StringBuilder();

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {

            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);

            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(TESSERACT_PATH + "\\tessdata");
            tesseract.setLanguage("eng");
            tesseract.setPageSegMode(3);

            for (int page = 1;
                 page <= document.getNumberOfPages();
                 page++) {

                stripper.setStartPage(page);
                stripper.setEndPage(page);

                String pageText = stripper.getText(document).trim();

                result.append("\n\n===== PAGE ")
                        .append(page)
                        .append(" =====\n");

                if (pageText.replaceAll("\\s+", "").length() < 30) {

                    // Render the scanned PDF page.
                    BufferedImage original =
                            renderer.renderImageWithDPI(page - 1, 250);

                    // Convert to a standard 3-byte BGR image for OCR.
                    BufferedImage ocrImage = new BufferedImage(
                            original.getWidth(),
                            original.getHeight(),
                            BufferedImage.TYPE_3BYTE_BGR
                    );

                    Graphics2D graphics = ocrImage.createGraphics();

                    try {
                        graphics.drawImage(original, 0, 0, null);
                    } finally {
                        graphics.dispose();
                    }

                    // OCR the normalized image.
                    pageText = tesseract.doOCR(ocrImage).trim();

                    result.append("[Extracted using OCR]\n");

                } else {
                    result.append("[Extracted using PDF text]\n");
                }

                result.append(pageText).append("\n");
            }
        }

        return result.toString().trim();
    }
}
