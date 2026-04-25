package com.pos.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Cấu hình ứng dụng: mặc định lưu tại {@code C:\POS-App\settings.properties} (có thể ghi đè bằng {@code pos.settings.path}).
 */
public final class AppSettings {
   public static final String DEFAULT_SETTINGS_PATH = "C:\\POS-App\\settings.properties";
   private static final String KEY_PORT = "printer.port";
   private static final String KEY_PAPER = "printer.paper";
   private static final String KEY_NAME = "shop.name";
   private static final String KEY_ADDRESS = "shop.address";
   private static final String KEY_PHONE = "shop.phone";
   private final Path file;
   private final Properties props = new Properties();

   public AppSettings() {
      this(resolvePath());
   }

   public AppSettings(Path file) {
      this.file = file;
   }

   private static Path resolvePath() {
      String override = System.getProperty("pos.settings.path", System.getenv("POS_SETTINGS_PATH"));
      if (override != null && !override.isBlank()) {
         return Paths.get(override.trim());
      }
      return Paths.get(DEFAULT_SETTINGS_PATH);
   }

   public void load() throws IOException {
      this.props.clear();
      if (Files.isRegularFile(this.file)) {
         try (InputStream in = Files.newInputStream(this.file)) {
            this.props.load(in);
         }
      }
   }

   public void save() throws IOException {
      Path d = this.file.getParent();
      if (d != null) {
         Files.createDirectories(d);
      }
      try (OutputStream out = Files.newOutputStream(this.file)) {
         this.props.store(
            out,
            "POS - Cau hinh cua hang va may in. Khong sửa encoding file."
         );
      }
   }

   public String getPrinterPort() {
      return n(this.props.getProperty(KEY_PORT, "").trim());
   }

   public void setPrinterPort(String port) {
      this.props.setProperty(KEY_PORT, n(port));
   }

   public String getPaper() {
      String p = n(this.props.getProperty(KEY_PAPER, "80mm"));
      if (!"58mm".equals(p) && !"80mm".equals(p)) {
         return "80mm";
      }
      return p;
   }

   public void setPaper(String paper) {
      this.props.setProperty(KEY_PAPER, "58mm".equals(paper) ? "58mm" : "80mm");
   }

   public int getMaxLineChars() {
      return "58mm".equals(this.getPaper()) ? 32 : 48;
   }

   public String getShopName() {
      return n(this.props.getProperty(KEY_NAME, "MIXUE"));
   }

   public void setShopName(String s) {
      this.props.setProperty(KEY_NAME, n(s));
   }

   public String getShopAddress() {
      return n(this.props.getProperty(KEY_ADDRESS, ""));
   }

   public void setShopAddress(String s) {
      this.props.setProperty(KEY_ADDRESS, n(s));
   }

   public String getShopPhone() {
      return n(this.props.getProperty(KEY_PHONE, ""));
   }

   public void setShopPhone(String s) {
      this.props.setProperty(KEY_PHONE, n(s));
   }

   public boolean isPrinterPortConfigured() {
      return !this.getPrinterPort().isBlank();
   }

   public Path getFilePath() {
      return this.file;
   }

   private static String n(String s) {
      return s == null ? "" : s;
   }
}
