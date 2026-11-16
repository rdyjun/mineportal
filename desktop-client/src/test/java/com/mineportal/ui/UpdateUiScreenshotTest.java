package com.mineportal.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mineportal.BuildInfo;
import com.mineportal.update.AppVersion;
import com.mineportal.update.DesktopSettings;
import com.mineportal.update.GithubReleaseClient;
import com.mineportal.update.InstallPlatform;
import com.mineportal.update.UpdateCheck;
import com.mineportal.update.UpdateChecker;
import com.mineportal.update.UpdateOffer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Headless render of the banner and about window. Skipped unless {@code MINEPORTAL_SCREENSHOT=1}.
 * The banner uses a sample newer version because the published GitHub release can be older than
 * this build; the about window shows the result of a real check.
 */
class UpdateUiScreenshotTest {

  @Test
  void renderBannerAndAbout(@TempDir Path dir) throws Exception {
    assumeTrue("1".equals(System.getenv("MINEPORTAL_SCREENSHOT")));
    System.setProperty("prism.order", "sw");
    System.setProperty("prism.lcdtext", "false");
    startToolkit();
    Platform.setImplicitExit(false);

    AtomicReference<Throwable> failure = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);
    Platform.runLater(
        () -> {
          try {
            render(dir);
          } catch (Throwable ex) {
            failure.set(ex);
          } finally {
            done.countDown();
          }
        });
    assertTrue(done.await(40, TimeUnit.SECONDS), "screenshot render timed out");
    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
    copyScreenshots(dir);
    assertAccent(dir.resolve("update-banner.png"), true);
    assertAccent(dir.resolve("about-window.png"), false);
    Platform.exit();
  }

  private static void copyScreenshots(Path dir) throws IOException {
    String output = System.getenv("MINEPORTAL_SCREENSHOT_DIR");
    if (output == null || output.isBlank()) {
      return;
    }
    Path dest = Path.of(output);
    Files.createDirectories(dest);
    for (String name : new String[] {"update-banner.png", "about-window.png"}) {
      Files.copy(dir.resolve(name), dest.resolve(name), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void render(Path dir) throws Exception {
    Font cjk = loadCjk();
    BorderPane content = new BorderPane();
    content.setStyle("-fx-background-color: #0a0a0d;");
    content.setPrefSize(1280, 820);
    Stage stage = new Stage();
    stage.setTitle("마인포탈 · MinePortal");
    DesktopSettings settings = DesktopSettings.load(dir.resolve("desktop-settings.properties"));
    UpdateChecker checker =
        new UpdateChecker(
            AppVersion.parse(BuildInfo.VERSION),
            GithubReleaseClient.create(BuildInfo.GITHUB_REPO, BuildInfo.VERSION),
            InstallPlatform.detect(),
            BuildInfo.GITHUB_REPO);
    DesktopChrome chrome =
        new DesktopChrome(content, stage, url -> {}, checker, settings, UiText.forLanguage("ko"));
    Scene scene = new Scene(chrome.root(), 1280, 820);
    applyFont(scene, cjk);
    stage.setScene(scene);
    stage.show();

    AppVersion current = AppVersion.parse(BuildInfo.VERSION).orElseThrow();
    chrome.apply(
        UpdateCheck.available(
            new UpdateOffer(
                current,
                AppVersion.parse("1.2.0").orElseThrow(),
                "https://github.com/rdyjun/mineportal/releases/tag/1.2.0",
                "https://github.com/rdyjun/mineportal/releases/download/1.2.0/MinePortal-win.zip")),
        false);
    chrome.settleForScreenshot();
    write(scene.snapshot(null), dir.resolve("update-banner.png"));

    chrome.apply(checker.check(), true);
    chrome.openAboutForScreenshot();
    Scene about = chrome.aboutSceneForScreenshot();
    applyFont(about, cjk);
    write(about.snapshot(null), dir.resolve("about-window.png"));
    stage.close();
  }

  private static void applyFont(Scene scene, Font font) {
    if (font == null || scene == null) {
      return;
    }
    String style = scene.getRoot().getStyle() == null ? "" : scene.getRoot().getStyle();
    scene.getRoot().setStyle(style + " -fx-font-family: \"" + font.getFamily() + "\";");
  }

  private static Font loadCjk() {
    Path file = Path.of("/usr/share/fonts/truetype/wqy/wqy-microhei.ttc");
    if (!Files.isRegularFile(file)) {
      return null;
    }
    try (InputStream in = Files.newInputStream(file)) {
      return Font.loadFont(in, 14);
    } catch (IOException ex) {
      return null;
    }
  }

  private static void startToolkit() throws Exception {
    try {
      CountDownLatch started = new CountDownLatch(1);
      Platform.startup(started::countDown);
      assertTrue(started.await(20, TimeUnit.SECONDS));
    } catch (IllegalStateException alreadyStarted) {
      // Another test already brought the toolkit up.
    }
  }

  private static void write(WritableImage image, Path file) throws IOException {
    int width = (int) image.getWidth();
    int height = (int) image.getHeight();
    int[] pixels = new int[width * height];
    image
        .getPixelReader()
        .getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
    BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    buffered.setRGB(0, 0, width, height, pixels, 0, width);
    Files.createDirectories(file.getParent());
    ImageIO.write(buffered, "png", file.toFile());
  }

  private static void assertAccent(Path file, boolean lowerRight) throws IOException {
    BufferedImage image = ImageIO.read(file.toFile());
    assertTrue(image.getWidth() > 400 && image.getHeight() > 200, file::toString);
    int x0 = lowerRight ? Math.max(0, image.getWidth() - 560) : 0;
    int y0 = lowerRight ? Math.max(0, image.getHeight() - 240) : 0;
    int purple = 0;
    for (int y = y0; y < image.getHeight(); y++) {
      for (int x = x0; x < image.getWidth(); x++) {
        int argb = image.getRGB(x, y);
        int red = (argb >> 16) & 0xff;
        int green = (argb >> 8) & 0xff;
        int blue = argb & 0xff;
        if (red > 120 && green < 170 && blue > 180) {
          purple++;
        }
      }
    }
    assertTrue(purple > 30, file + " purple pixels=" + purple);
  }
}
