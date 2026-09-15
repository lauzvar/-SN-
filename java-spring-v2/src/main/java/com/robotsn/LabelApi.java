package com.robotsn;
import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api")
public class LabelApi {
 final Store s;final RobotApi robots;final AdminApi admin;final String publicUrl;
 public LabelApi(Store s,RobotApi robots,AdminApi admin,@Value("${valenbot.public-url}")String url){this.s=s;this.robots=robots;this.admin=admin;var uri=URI.create(url);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("Invalid public URL");publicUrl=url.replaceAll("/+$","");}
 public String url(String sn){return publicUrl+"/r/"+sn;}
 @GetMapping("/robots/{sn}/label-info") Object info(@PathVariable String sn){String current=s.robot(sn,false).get("sn").toString();return Map.of("sn",current,"url",url(current),"title","Valenbot 人形陪伴机器人","size","100 × 30 mm");}
 static BufferedImage qrImage(String content,int pixels)throws Exception{return MatrixToImageWriter.toBufferedImage(new MultiFormatWriter().encode(content,BarcodeFormat.QR_CODE,pixels,pixels,Map.of(EncodeHintType.CHARACTER_SET,"UTF-8",EncodeHintType.MARGIN,4,EncodeHintType.ERROR_CORRECTION,com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M)));}
 static void fit(Graphics2D g,String text,int size,int x,int y,int width,boolean bold){Font f=new Font("Microsoft YaHei",bold?Font.BOLD:Font.PLAIN,size);while(g.getFontMetrics(f).stringWidth(text)>width&&size>12)f=f.deriveFont((float)--size);g.setFont(f);g.drawString(text,x,y);}
 static byte[] label(String sn,String model,String url)throws Exception{BufferedImage img=new BufferedImage(1800,540,BufferedImage.TYPE_INT_RGB);Graphics2D g=img.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,img.getWidth(),img.getHeight());g.setColor(Color.BLACK);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);fit(g,"Valenbot 人形陪伴机器人",92,60,155,1160,true);fit(g,model==null||model.isBlank()?"型号：未填写":model,80,60,292,1160,false);fit(g,sn,83,60,430,1160,false);g.drawImage(qrImage(url,480),1290,30,null);g.dispose();var out=new ByteArrayOutputStream();ImageIO.write(img,"png",out);return out.toByteArray();}
 @GetMapping(value="/robots/{sn}/label.png",produces="image/png") ResponseEntity<byte[]> label(@PathVariable String sn)throws Exception{var r=s.robot(sn,false);sn=r.get("sn").toString();var f=s.map(s.visible(r).get("fields"));s.audit("robot",sn,"LABEL","qr",null,url(sn),"生成 SN 二维码标签");return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+sn+"-label.png\"").cacheControl(CacheControl.noStore()).body(label(sn,Objects.toString(f.get("model"),""),url(sn)));}
 @GetMapping(value="/robots/{sn}/qr.png",produces="image/png") byte[] qr(@PathVariable String sn)throws Exception{sn=s.robot(sn,false).get("sn").toString();var out=new ByteArrayOutputStream();ImageIO.write(qrImage(url(sn),640),"png",out);return out.toByteArray();}
 @GetMapping("/export/robots") ResponseEntity<byte[]> export(){var definitions=s.definitions(!s.role().equals("USER"));var header=new ArrayList<Object>();definitions.forEach(d->header.add(d.get("label")));var lines=new ArrayList<String>();lines.add(csv(header));for(var row:s.db.queryForList("select * from robot where not deleted order by sn")){if(!s.allowed(row))continue;var f=s.map(s.visible(row).get("fields"));var values=new ArrayList<Object>();definitions.forEach(d->values.add(f.get(d.get("key"))));lines.add(csv(values));}s.audit("export","robots","EXPORT","fields",null,header,"按绑定和字段定义导出");return ResponseEntity.ok().contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"Valenbot-robots.csv\"").cacheControl(CacheControl.noStore()).body(("\ufeff"+String.join("\r\n",lines)).getBytes(StandardCharsets.UTF_8));}
 static String csv(java.util.List<Object> values){return values.stream().map(v->{String t=v==null?"":v.toString();if(t.stripLeading().matches("^[=+@\\-\\t\\r].*"))t="'"+t;return "\""+t.replace("\"","\"\"")+"\"";}).collect(java.util.stream.Collectors.joining(","));}
 @GetMapping("/export/{kind}") ResponseEntity<byte[]> exportOther(@PathVariable String kind,@RequestParam(required=false)Long tableId){s.technical();Object content=switch(kind){case "table"->{if(tableId==null)Store.fail(400,"缺少业务表编号");yield admin.tableData(tableId);}case "audit"->admin.audit();case "sources"->admin.sources();case "users"->admin.users();case "logins"->admin.logins();case "sn"->robots.sns();case "details"->{var out=new ArrayList<Object>();for(var r:s.db.queryForList("select * from robot where not deleted order by sn"))if(s.allowed(r))out.add(robots.detail(r.get("sn").toString()));yield out;}default->throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"未知导出类型");};s.audit("export",kind,"EXPORT","*",null,tableId,"按账号权限导出业务记录");return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"Valenbot-"+kind+".json\"").cacheControl(CacheControl.noStore()).body(s.encode(content).getBytes(StandardCharsets.UTF_8));}
}
