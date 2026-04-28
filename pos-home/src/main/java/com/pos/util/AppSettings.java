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
   public static final String PRINT_MODE_THERMAL_COM = "THERMAL_COM";
   public static final String PRINT_MODE_A4_WINDOWS = "A4_WINDOWS";
   private static final String KEY_PORT = "printer.port";
   /** Tốc độ nối tiếp. Nhiều máy in nhiệt mặc định 115200 thay vì 9600. */
   private static final String KEY_BAUD = "printer.baud";
   private static final String KEY_PAPER = "printer.paper";
   private static final String KEY_PRINT_MODE = "printer.mode";
   private static final String KEY_WINDOWS_PRINTER_NAME = "printer.windows.name";
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
      return n(this.props.getProperty(KEY_PORT, "COM1").trim());
   }

   public void setPrinterPort(String port) {
      this.props.setProperty(KEY_PORT, n(port));
   }

   public int getPrinterBaudRate() {
      String s = n(this.props.getProperty(KEY_BAUD, "115200")).trim();
      try {
         int b = Integer.parseInt(s);
         if (b < 1200 || b > 2000000) {
            return 115200;
         }
         return b;
      } catch (NumberFormatException e) {
         return 115200;
      }
   }

   public void setPrinterBaudRate(int baud) {
      if (baud < 1200 || baud > 2000000) {
         baud = 115200;
      }
      this.props.setProperty(KEY_BAUD, Integer.toString(baud));
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
      String p = this.getPrinterPort();
      return p != null && !p.isBlank();
   }

   public String getPrintMode() {
      String m = n(this.props.getProperty(KEY_PRINT_MODE, PRINT_MODE_THERMAL_COM)).trim();
      if (!PRINT_MODE_A4_WINDOWS.equals(m) && !PRINT_MODE_THERMAL_COM.equals(m)) {
         return PRINT_MODE_THERMAL_COM;
      }
      return m;
   }

   public void setPrintMode(String mode) {
      String m = n(mode).trim();
      if (!PRINT_MODE_A4_WINDOWS.equals(m)) {
         m = PRINT_MODE_THERMAL_COM;
      }
      this.props.setProperty(KEY_PRINT_MODE, m);
   }

   public String getWindowsPrinterName() {
      return n(this.props.getProperty(KEY_WINDOWS_PRINTER_NAME, "")).trim();
   }

   public void setWindowsPrinterName(String name) {
      this.props.setProperty(KEY_WINDOWS_PRINTER_NAME, n(name).trim());
   }

   public Path getFilePath() {
      return this.file;
   }

   private static String n(String s) {
      return s == null ? "" : s;
   }
}
