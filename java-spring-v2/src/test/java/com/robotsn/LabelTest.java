package com.robotsn;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
class LabelTest {
 @Test void printedLabelQrDecodesWithQuietZone()throws Exception{
   String sn="LBR-2609-P-0001",url="http://192.168.110.8:8082/r/"+sn;
   var img=ImageIO.read(new ByteArrayInputStream(LabelApi.label(sn,"Valenbot-V1.0",url)));
   assertEquals(1800,img.getWidth());assertEquals(540,img.getHeight());
   var crop=img.getSubimage(1290,30,480,480);
   assertEquals(url,new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(crop)))).getText());
 }
 @Test void spreadsheetFormulaCellsAreNeutralized(){assertTrue(LabelApi.csv(java.util.List.of("=SUM(1,2)","@malicious","safe")).startsWith("\"'=SUM"));}
}
