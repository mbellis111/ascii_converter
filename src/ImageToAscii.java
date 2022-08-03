import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

import org.imgscalr.Scalr;

@SuppressWarnings("unused")
public class ImageToAscii {

	// how many pixels to group when assigning data
	private static final int DETAIL_HIGH = 1;
	private static final int DETAIL_MEDIUM = 4;
	private static final int DETAIL_LOW = 10;

	// how large the picture should be, before being reduced by detail
	private static final Dimension SIZE_LARGE = new Dimension(1024, 768);
	private static final Dimension SIZE_MEDIUM = new Dimension(640, 480);
	private static final Dimension SIZE_SMALL = new Dimension(320, 240);
	private static final Dimension SIZE_TINY = new Dimension(100, 70);

	// the ascii scales to use in the image
	// copied from http://paulbourke.net/dataformats/asciiart/
	private static final String SCALE_SHORT = " .:-=+*#%@";
	private static final String SCALE_LONG = " .'`^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@$";

	// font size for output image
	private static final int FONT_SIZE = 12;

	// ***************************************
	// what to actually run the program on
	// ***************************************
	private static final String ASCII_SCALE = SCALE_SHORT;
	private static final Dimension TARGET_SIZE = SIZE_SMALL;
	private static final int BLOCK_SIZE = DETAIL_HIGH;

	// calculate the block size using the font ratio of 7x15, or roughly vertical x 2
	private static final int BLOCK_WIDTH = BLOCK_SIZE;
	private static final int BLOCK_HEIGHT = BLOCK_SIZE * 2;

	/**
	 * Run the program
	 * 
	 * @param args - command line arguments
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		String inputPath = "C:/Users/mbell/Desktop/test5.jpg";

		Path pathObj = Paths.get(inputPath);

		String outputPathTxt = inputPath.replace(pathObj.getFileName().toString(), "ascii.txt");
		String outputPathPic = inputPath.replace(pathObj.getFileName().toString(), "ascii.jpg");
		String outputPathHtml = inputPath.replace(pathObj.getFileName().toString(), "ascii.html");

		BufferedImage image = ImageToAscii.uploadImage(pathObj.toString());

		BufferedImage newImage = resizeImageAndGreyscale(image);

		char[][] asciiImage = createAsciiImage(newImage);

		// print out the new image to a file
		String contents = imageToString(asciiImage);
		// ImageToAscii.writeToFile(outputPath, contents);
		// System.out.println("Wrote file to " + outputPath);

		// save as an html file
		String htmlFileContents = createHTMLFileFromText(contents);
		writeToFile(outputPathHtml, htmlFileContents);
		System.out.println("Wrote file to " + outputPathHtml);

		// save to an image file
		saveTextAsImage(asciiImage, outputPathPic);
		System.out.println("Wrote file to " + outputPathPic);
	}

	private static void writeToFile(String fileName, String contents) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
		writer.write(contents);
		writer.close();
	}

	private static String imageToString(char[][] image) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < image.length; i++) {
			for (int k = 0; k < image[i].length; k++) {
				sb.append(image[i][k]);
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	/**
	 * Resizes the input image to the specified size
	 * 
	 * @param image
	 * @return
	 */
	private static BufferedImage resizeImageAndGreyscale(BufferedImage image) {
		// resize image to match the closest dimensions keeping aspect ratio

		// resize picture slightly so the block sizes divide cleanly
		int h = (int) TARGET_SIZE.getHeight();
		int w = (int) TARGET_SIZE.getWidth();
		int hdiff = h % BLOCK_HEIGHT;
		int wdiff = w % BLOCK_WIDTH;
		int newHeight = hdiff == 0 ? h : h + (BLOCK_HEIGHT - hdiff);
		int newWidth = wdiff == 0 ? w : w + (BLOCK_WIDTH - wdiff);

		System.out.println("Original image is " + image.getWidth() + "x" + image.getHeight());

		// resize the image & convert it to grayscale
		BufferedImage resizedImage = Scalr.resize(image, newWidth, newHeight, Scalr.OP_GRAYSCALE);
		System.out.println("Resized image to " + resizedImage.getWidth() + "x" + resizedImage.getHeight());
		return resizedImage;
	}

	/**
	 * Calculates the average brightness of each pixel in the image
	 * 
	 * @param image - the image (or subimage) to calculate the brightness
	 * @return - the brightness of a scaole of 0 - 255
	 */
	private static int calculateAverageBrightness(BufferedImage image) {
		double total = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {

				int clr = image.getRGB(x, y);
				// int alpha = (clr >> 24) & 0xff;
				int red = (clr >> 16) & 0xff;
				int green = (clr >> 8) & 0xff;
				int blue = (clr >> 0) & 0xff;

				// calculate luminance (aka brightness)
				double luminance = (0.2126 * red + 0.7152 * green + 0.0722 * blue);
				total += luminance;
			}
		}
		double average = total / (image.getHeight() * image.getWidth());
		return (int) average;
	}

	/**
	 * Convert the brightness to an ascii character.
	 * 
	 * Scales the original range into the smaller scale for the possible ascii characters.
	 * 
	 * @param brightness - value from 0 - 255
	 * @return - the ascii character representing the brightness level
	 */
	private static char convertBrightnessToCharacter(int brightness) {
		int newMax = ASCII_SCALE.length();
		int oldMax = 255;
		int newMin = 0;
		int oldMin = 0;

		int oldRange = (oldMax - oldMin);
		int newRange = (newMax - newMin);
		int newValue = (((brightness - oldMin) * newRange) / oldRange) + newMin;

		// fix out of bounds
		if (newValue == newMax) {
			newValue--;
		}

		return ASCII_SCALE.charAt(newValue);
	}

	/**
	 * Converts an image file into an ascii art representation.
	 * 
	 * Splits the image into sections defined by the block size.
	 * 
	 * Calculates a character to use for each block based on the average brightness
	 * 
	 * @param image - the image to convert to ascii art
	 * @return - a matrix of characters representing the image as ascii art
	 * @throws IOException
	 */
	private static char[][] createAsciiImage(BufferedImage image) throws IOException {
		// this should divide evenly thanks to earlier resizing
		int blocksTall = image.getHeight() / BLOCK_HEIGHT;
		int blocksWide = image.getWidth() / BLOCK_WIDTH;

		System.out.println("Array size: " + blocksWide + "x" + blocksTall);

		char[][] asciiImage = new char[blocksTall][blocksWide];

		for (int y = 0; y < blocksTall; y++) {
			for (int x = 0; x < blocksWide; x++) {
				BufferedImage subImage = image.getSubimage(x * BLOCK_WIDTH, y * BLOCK_HEIGHT, BLOCK_WIDTH, BLOCK_HEIGHT);
				int averageBrightness = calculateAverageBrightness(subImage);
				char asciiCharacter = convertBrightnessToCharacter(averageBrightness);
				asciiImage[y][x] = asciiCharacter;
			}
		}
		return asciiImage;
	}

	/**
	 * Loads an image from a file path
	 * 
	 * @param path - the path to the image file
	 * @return - the image object
	 * @throws IOException
	 */
	private static BufferedImage uploadImage(String path) throws IOException {
		File image = new File(path);
		BufferedImage img = ImageIO.read(image);
		return img;
	}

	/**
	 * Create an HTML file containing the content string
	 * 
	 * @param contents - the ascii art string
	 * @return - the text of the new html file
	 */
	private static String createHTMLFileFromText(String contents) {
		// load existing HTML document to text
		String fileContent = null;
		try {
			byte[] bytes = Files.readAllBytes(Paths.get("html_template.html"));
			fileContent = new String(bytes);
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (fileContent == null) {
			return null;
		}

		// replace the placeholder text in the div with the ascii art
		String htmlFileContents = fileContent.replace("PLACEHOLDER", contents);
		return htmlFileContents;
	}

	private static void saveTextAsImage(char[][] data, String imageFilePath) {

		// create small temporary image to calculate font sizes
		BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = img.createGraphics();

		// create the font to use for the image, which is monospaced
		Font font = new Font("DejaVu Sans Mono", Font.PLAIN, FONT_SIZE);
		// Font font = new Font("Consolas", Font.PLAIN, FONT_SIZE);
		graphics.setFont(font);

		// calculate the size of the image we will need
		FontMetrics metrics = graphics.getFontMetrics(font);
		int fontHeight = metrics.getHeight();
		// a monospaced font will use the same width for all characters
		int fontWidth = metrics.charWidth('.');

		System.out.println("Font size: " + fontWidth + "x" + fontHeight);

		int height = (data.length + 1) * fontHeight;
		int width = (data[0].length + 1) * fontWidth;

		System.out.println("Creating image of " + width + "x" + height);

		graphics.dispose();

		img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		graphics = img.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
		graphics.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
		graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		graphics.setFont(font);
		graphics.setColor(Color.WHITE);

		// create a blank square to draw on
		graphics.setBackground(Color.BLACK);
		graphics.clearRect(0, 0, width, height);

		int nextLinePosition = fontHeight;
		for (int r = 0; r < data.length; r++) {
			char[] row = data[r];
			String line = new String(row);
			graphics.drawString(line, 0, nextLinePosition);
			nextLinePosition += fontHeight;
		}

		graphics.dispose();
		try {
			ImageIO.write(img, "jpg", new File(imageFilePath));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
