package com.robotsn;

import static org.junit.jupiter.api.Assertions.*;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class LabelTest {
  @Test
  void printedLabelQrDecodesWithQuietZone() throws Exception {
    String sn = "LBR-2609-P-0001", url = "http://192.168.110.8:8082/r/" + sn;
    var img = ImageIO.read(new ByteArrayInputStream(LabelApi.label(sn, "Valenbot-V1.0", url)));
    assertEquals(1800, img.getWidth());
    assertEquals(540, img.getHeight());
    var crop = img.getSubimage(1290, 30, 480, 480);
    assertEquals(
        url,
        new MultiFormatReader()
            .decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(crop))))
            .getText());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {
        "=SUM(1,2)",
        "+1",
        "-1",
        "@malicious",
        " =1",
        "\tvalue",
        "\rvalue",
        "\nvalue",
        "=1\n+1"
      })
  void spreadsheetFormulaCellsAreNeutralized(String value) {
    assertEquals(
        "\"safe\",\"'" + value.replace("\"", "\"\"") + "\"",
        LabelApi.csv(java.util.List.of("safe", value)));
  }

  @Test
  void quotesNullAndNonFormulaValues() {
    assertEquals(
        "\"a\"\"b\",\"\",\"0\",\"false\"",
        LabelApi.csv(java.util.Arrays.asList("a\"b", null, 0, false)));
  }
}
