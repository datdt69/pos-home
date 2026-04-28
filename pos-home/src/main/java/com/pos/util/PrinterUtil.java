package com.pos.util;

import com.fazecast.jSerialComm.SerialPort;
import com.pos.model.Order;
import com.pos.model.OrderItem;
import com.pos.repository.OrderRepository;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * In nhiệt ESC/POS qua cổng COM (jSerialComm). Văn bản: thử windows-1258, sau đó x-Windows-50225, CP874; cuối cùng ISO-8859-1.
 */
public final class PrinterUtil {
   public static final int TIMEOUT_MS = 3000;
   public static final DateTimeFormatter RECEIPT_DF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));
   /** Thứ tự ưu tiên khi tự tìm máy in (nhiều model dùng 115200, không phải 9600). */
   private static final int[] DETECT_BAUDS = {115200, 9600, 19200, 38400, 57600, 230400};
   private static final String[] XPRINTER_HINTS = {"xprinter", "x-printer", "xp-", "xp58", "xp80", "xp-58", "xp-80"};
   private static volatile int lastDetectedBaud = 9600;
   private static final List<Charset> ENCODING_TRY = new ArrayList<>();

   static {
      for (String c : new String[]{"windows-1258", "x-Windows-50225", "CP874"}) {
         try {
            if (Charset.isSupported(c)) {
               ENCODING_TRY.add(Charset.forName(c));
            }
         } catch (Exception ignored) {
         }
      }
   }

   private PrinterUtil() {
   }

   public static List<String> getAvailablePorts() {
      SerialPort[] ports = SerialPort.getCommPorts();
      if (ports == null || ports.length == 0) {
         return Collections.emptyList();
      }
      return Stream.of(ports)
         .sorted(Comparator.comparingInt(PrinterUtil::portScore).reversed())
         .map(SerialPort::getSystemPortName)
         .collect(Collectors.toList());
   }

   public static List<String> getAvailableWindowsPrinters() {
      return Printer.getAllPrinters()
         .stream()
         .map(Printer::getName)
         .filter(n -> !isVirtualWindowsPrinterName(n))
         .sorted(String.CASE_INSENSITIVE_ORDER)
         .collect(Collectors.toList());
   }

   public static void printTestWindows(String printerName, String paper) throws IOException {
      String body = "TEST MAY IN A4 - OK\n" +
         "Thoi gian: " + java.time.LocalDateTime.now().format(RECEIPT_DF) + "\n" +
         "May in: " + nvl(printerName, "(mac dinh)") + "\n" +
         "Kho giay: " + nvl(paper, "80mm") + "\n";
      printTextWindows(printerName, body, paper);
   }

   public static String detectPrinterPort() {
      for (String name : getAvailablePorts()) {
         for (int baud : DETECT_BAUDS) {
            if (testPortWithBaud(name, baud)) {
               lastDetectedBaud = baud;
               return name;
            }
         }
      }
      return null;
   }

   /** Baud vừa tìm được ở lần gọi {@link #detectPrinterPort()} gần nhất (cùng tốc nếu không tìm thấy). */
   public static int getLastDetectedBaud() {
      return lastDetectedBaud;
   }

   private static boolean testPortWithBaud(String systemPortName, int baud) {
      try {
         // init + 1 line de tranh truong hop ghi "ao" vao cong khong phai may in
         writeToPort(systemPortName, new byte[]{0x1B, 0x40, 0x0A}, TIMEOUT_MS, baud);
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   public static void printTest(String port) throws IOException {
      printTest(port, 115200);
   }

   public static void printTest(String port, int baud) throws IOException {
      if (port == null || port.isBlank()) {
         throw new IOException("Chua chon cong in.");
      }
      ByteBuffer buf = ByteBuffer.allocate(2048);
      putEscAt(buf);
      putAlign(buf, 1);
      putBold(buf, true);
      putTextNl(buf, "TEST IN HOA DON - OK");
      putBold(buf, false);
      putAlign(buf, 0);
      putTextNl(buf, "------------------------");
      putFeed(buf, 3);
      putPartialCut(buf);
      writeToPort(port, toByteArray(buf), TIMEOUT_MS, baud);
   }

   public static void printReceipt(
      Order order,
      List<OrderItem> items,
      String port,
      int baudRate,
      int maxLineChars,
      String shopName,
      String address,
      String phone
   ) throws IOException {
      if (port == null || port.isBlank()) {
         throw new IOException("Chưa chọn cổng máy in.");
      }
      if (maxLineChars < 24) {
         maxLineChars = 32;
      }
      ByteBuffer buf = ByteBuffer.allocate(16384);
      putEscAt(buf);
      putAlign(buf, 1);
      putBold(buf, true);
      putDoubleSize(buf, true);
      putTextNl(buf, nvl(shopName, "CUA HANG"));
      putDoubleSize(buf, false);
      putBold(buf, false);
      if (address != null && !address.isBlank()) {
         putTextNl(buf, address);
      }
      if (phone != null && !phone.isBlank()) {
         putTextNl(buf, "SĐT: " + phone);
      }
      putTextNl(buf, repeat('=', maxLineChars));
      putAlign(buf, 0);
      putTextNl(buf, "Đơn số : " + (order.getOrderNumber() == null ? "—" : order.getOrderNumber()));
      String t = "—";
      if (order.getPaidAt() != null) {
         t = order.getPaidAt().atZone(OrderRepository.VIETNAM).format(PrinterUtil.RECEIPT_DF);
      }
      putTextNl(buf, "Ngày   : " + t);
      putTextNl(buf, repeat('─', maxLineChars));
      putTextNl(buf, fitHeaderLine(maxLineChars));
      putTextNl(buf, repeat('─', maxLineChars));
      int i = 1;
      for (OrderItem oi : items) {
         long lineTot = (long) Math.round(oi.getLineTotal());
         putTextNl(
            buf,
            formatItemLine(
               i,
               oi.getMenuName() == null ? "Món" : oi.getMenuName(),
               oi.getQuantity(),
               vndNumber(lineTot),
               maxLineChars
            )
         );
         i++;
      }
      putTextNl(buf, repeat('─', maxLineChars));
      putAlign(buf, 0);
      putBold(buf, true);
      putTextNl(
         buf,
         "TỔNG CỘNG: " + vndNumber((long) Math.round(order.getTotal())) + " VND"
      );
      putBold(buf, false);
      putTextNl(buf, repeat('=', maxLineChars));
      putAlign(buf, 1);
      putTextNl(buf, "Cảm ơn quý khách!");
      putTextNl(buf, "Hẹn gặp lại quý khách!");
      putTextNl(buf, repeat('=', maxLineChars));
      putAlign(buf, 0);
      putFeed(buf, 3);
      putPartialCut(buf);
      writeToPort(port, toByteArray(buf), TIMEOUT_MS, baudRate);
   }

   public static void printReceiptWindowsA4(
      Order order,
      List<OrderItem> items,
      String printerName,
      String paper,
      String shopName,
      String address,
      String phone
   ) throws IOException {
      StringBuilder s = new StringBuilder(2048);
      s.append(nvl(shopName, "CUA HANG")).append('\n');
      if (address != null && !address.isBlank()) {
         s.append(address).append('\n');
      }
      if (phone != null && !phone.isBlank()) {
         s.append("SDT: ").append(phone).append('\n');
      }
      s.append("====================================").append('\n');
      s.append("Don so : ").append(order.getOrderNumber() == null ? "-" : order.getOrderNumber()).append('\n');
      String t = "-";
      if (order.getPaidAt() != null) {
         t = order.getPaidAt().atZone(OrderRepository.VIETNAM).format(PrinterUtil.RECEIPT_DF);
      }
      s.append("Ngay   : ").append(t).append('\n');
      s.append("------------------------------------").append('\n');
      int idx = 1;
      for (OrderItem oi : items) {
         long lineTot = (long)Math.round(oi.getLineTotal());
         s.append(idx).append(". ").append(nvl(oi.getMenuName(), "Mon")).append('\n');
         s.append("   x").append(oi.getQuantity()).append("    ").append(vndNumber(lineTot)).append(" VND").append('\n');
         idx++;
      }
      s.append("------------------------------------").append('\n');
      s.append("TONG CONG: ").append(vndNumber((long)Math.round(order.getTotal()))).append(" VND").append('\n');
      s.append("====================================").append('\n');
      s.append("Cam on quy khach!").append('\n');
      printTextWindows(printerName, s.toString(), paper);
   }

   private static void printTextWindows(String printerName, String content, String paperSetting) throws IOException {
      Printer printer = null;
      if (printerName != null && !printerName.isBlank()) {
         if (isVirtualWindowsPrinterName(printerName)) {
            throw new IOException("Dang chon may in ao (" + printerName + "). Hay chon may in giay A4 that.");
         }
         for (Printer p : Printer.getAllPrinters()) {
            if (p.getName().equalsIgnoreCase(printerName.trim())) {
               printer = p;
               break;
            }
         }
         if (printer == null) {
            throw new IOException("Khong tim thay may in Windows: " + printerName);
         }
      }

      PrinterJob job = PrinterJob.createPrinterJob(printer);
      if (job == null) {
         throw new IOException("Khong tao duoc PrinterJob.");
      }
      if (printer == null) {
         printer = job.getPrinter();
      }
      if (printer == null) {
         throw new IOException("Khong tim thay may in mac dinh cua Windows.");
      }
      if (isVirtualWindowsPrinterName(printer.getName())) {
         // Neu default la printer ao (Print to PDF), thu tim may in vat ly dau tien.
         Printer fallback = findFirstPhysicalWindowsPrinter();
         if (fallback == null) {
            throw new IOException("May in mac dinh la may in ao (" + printer.getName() + "). Hay chon may in A4 that trong Cai dat.");
         }
         printer = fallback;
         PrinterJob fallbackJob = PrinterJob.createPrinterJob(printer);
         if (fallbackJob == null) {
            throw new IOException("Khong tao duoc PrinterJob voi may in: " + printer.getName());
         }
         job = fallbackJob;
      }

      Paper paper = chooseWindowsPaper(printer, paperSetting);
      double margin = paper == Paper.A4 ? 24 : 6;
      PageLayout layout = printer.createPageLayout(paper, PageOrientation.PORTRAIT, margin, margin, margin, margin);
      job.getJobSettings().setPageLayout(layout);

      Text text = new Text(content == null ? "" : content);
      text.setWrappingWidth(Math.max(180, layout.getPrintableWidth()));
      text.setFont(Font.font("Consolas", paper == Paper.A4 ? 11 : 10));

      boolean ok = job.printPage(layout, text);
      if (!ok) {
         job.cancelJob();
         throw new IOException("Gui lenh in Windows that bai.");
      }
      if (!job.endJob()) {
         throw new IOException("Khong ket thuc duoc job in Windows.");
      }
   }

   private static Paper chooseWindowsPaper(Printer printer, String paperSetting) {
      if (printer == null) {
         return Paper.A4;
      }
      Set<Paper> supported = printer.getPrinterAttributes().getSupportedPapers();
      if (supported == null || supported.isEmpty()) {
         return Paper.A4;
      }

      boolean paper58 = "58mm".equalsIgnoreCase(nvl(paperSetting, ""));
      Paper defaultPaper = printer.getPrinterAttributes().getDefaultPaper();
      if (defaultPaper != null && matchesReceiptPaper(defaultPaper, paper58)) {
         return defaultPaper;
      }

      for (Paper p : supported) {
         if (matchesReceiptPaper(p, paper58)) {
            return p;
         }
      }
      if (defaultPaper != null && isReceiptLikePaper(defaultPaper)) {
         return defaultPaper;
      }
      for (Paper p : supported) {
         if (isReceiptLikePaper(p)) {
            return p;
         }
      }
      return defaultPaper != null ? defaultPaper : Paper.A4;
   }

   private static boolean matchesReceiptPaper(Paper paper, boolean paper58) {
      String n = paper.getName() == null ? "" : paper.getName().toLowerCase(Locale.ROOT);
      double mm = Math.min(paper.getWidth(), paper.getHeight()) / 2.834645669;
      if (paper58) {
         return n.contains("58") || n.contains("57") || n.contains("58mm") || (mm >= 54 && mm <= 60);
      }
      return n.contains("80")
         || n.contains("79")
         || n.contains("72")
         || n.contains("76")
         || n.contains("roll")
         || n.contains("receipt")
         || (mm >= 68 && mm <= 83);
   }

   private static boolean isReceiptLikePaper(Paper paper) {
      String n = paper.getName() == null ? "" : paper.getName().toLowerCase(Locale.ROOT);
      double w = Math.min(paper.getWidth(), paper.getHeight()) / 2.834645669;
      double h = Math.max(paper.getWidth(), paper.getHeight()) / 2.834645669;
      return n.contains("roll") || n.contains("receipt") || (w <= 85 && h >= 150);
   }

   private static Printer findFirstPhysicalWindowsPrinter() {
      for (Printer p : Printer.getAllPrinters()) {
         if (!isVirtualWindowsPrinterName(p.getName())) {
            return p;
         }
      }
      return null;
   }

   private static boolean isVirtualWindowsPrinterName(String name) {
      if (name == null) {
         return true;
      }
      String n = name.toLowerCase(Locale.ROOT);
      return n.contains("pdf")
         || n.contains("xps")
         || n.contains("onenote")
         || n.contains("fax")
         || n.contains("microsoft print to pdf")
         || n.contains("microsoft xps");
   }

   private static void putEscAt(ByteBuffer buf) {
      buf.put((byte)0x1B);
      buf.put((byte)0x40);
   }

   private static void putAlign(ByteBuffer buf, int a) {
      if (a < 0) {
         a = 0;
      }
      if (a > 2) {
         a = 2;
      }
      buf.put((byte)0x1B);
      buf.put((byte)0x61);
      buf.put((byte)(a & 0xFF));
   }

   private static void putBold(ByteBuffer buf, boolean on) {
      buf.put((byte)0x1B);
      buf.put((byte)0x45);
      buf.put(on ? (byte)1 : (byte)0);
   }

   private static void putDoubleSize(ByteBuffer buf, boolean on) {
      buf.put((byte)0x1B);
      buf.put((byte)0x21);
      buf.put(on ? (byte)0x11 : (byte)0x00);
   }

   private static void putFeed(ByteBuffer buf, int n) {
      int k = Math.max(0, Math.min(255, n));
      buf.put((byte)0x1B);
      buf.put((byte)0x64);
      buf.put((byte)k);
   }

   private static void putPartialCut(ByteBuffer buf) {
      buf.put((byte)0x1D);
      buf.put((byte)0x56);
      buf.put((byte)0x42);
      buf.put((byte)0x00);
   }

   private static void putTextNl(ByteBuffer buf, String line) {
      appendEncodedText(buf, line);
      buf.put((byte)0x0A);
   }

   private static void appendEncodedText(ByteBuffer buf, String line) {
      if (line == null) {
         line = "";
      }
      for (Charset enc : ENCODING_TRY) {
         try {
            CharsetEncoder enco = enc.newEncoder();
            enco.onMalformedInput(CodingErrorAction.REPLACE);
            enco.onUnmappableCharacter(CodingErrorAction.REPLACE);
            buf.put(enco.encode(CharBuffer.wrap(line)));
            return;
         } catch (Exception e) {
            // thử bảng mã kế
         }
      }
      buf.put(line.getBytes(StandardCharsets.ISO_8859_1));
   }

   private static String fitHeaderLine(int w) {
      String a = "STT Tên món      SL  Tiền";
      if (a.length() >= w) {
         return a.substring(0, w);
      } else {
         return a;
      }
   }

   private static String formatItemLine(int stt, String name, int qty, String money, int w) {
      int moneyW = 9;
      int qtyW = 3;
      int g = 1;
      int sttW = 2;
      int nameW = w - sttW - g - qtyW - 1 - moneyW - 1;
      if (nameW < 4) {
         nameW = 4;
      }
      String st = String.valueOf(stt);
      if (st.length() > sttW) {
         st = st.substring(0, sttW);
      }
      String n = trunc(nvl(name, ""), nameW);
      String q = String.valueOf(qty);
      if (q.length() > qtyW) {
         q = q.substring(0, qtyW);
      }
      String m = money;
      if (m.length() > moneyW) {
         m = m.substring(0, moneyW);
      }
      return padRight(st, sttW) + " " + padRight(n, nameW) + " " + padLeft(q, qtyW) + " " + padLeft(m, moneyW);
   }

   private static String padLeft(String s, int w) {
      if (s.length() >= w) {
         return s;
      } else {
         return " ".repeat(w - s.length()) + s;
      }
   }

   private static String padRight(String s, int w) {
      if (s.length() >= w) {
         return s;
      } else {
         return s + " ".repeat(w - s.length());
      }
   }

   private static String trunc(String s, int max) {
      if (s.length() <= max) {
         return s;
      } else {
         return max > 1 ? s.substring(0, max - 1) + "…" : s;
      }
   }

   private static String vndNumber(long n) {
      return String.format(Locale.US, "%,d", n).replace(',', ',');
   }

   private static String repeat(char c, int w) {
      StringBuilder b = new StringBuilder(w);
      for (int i = 0; i < w; i++) {
         b.append(c);
      }
      return b.toString();
   }

   private static String nvl(String s, String d) {
      return s != null && !s.isBlank() ? s : d;
   }

   private static byte[] toByteArray(ByteBuffer buf) {
      byte[] a = new byte[buf.position()];
      buf.rewind();
      buf.get(a);
      return a;
   }

   private static void writeToPort(String systemPortName, byte[] data, int timeoutMs, int baud) throws IOException {
      if (data == null || data.length == 0) {
         return;
      }
      if (systemPortName == null || systemPortName.isBlank()) {
         throw new IOException("Cổng COM trống.");
      }
      if (baud < 1200) {
         baud = 115200;
      }

      // Win7/POS: uu tien ghi truc tiep vao thiet bi COM (giong copy /b ... COMx)
      // Neu that bai moi quay lai jSerialComm.
      IOException rawErr = null;
      try {
         writeDirectCom(systemPortName, data, baud);
         return;
      } catch (IOException e) {
         rawErr = e;
      }
      SerialPort p = SerialPort.getCommPort(systemPortName);
      p.setBaudRate(baud);
      p.setNumDataBits(8);
      p.setNumStopBits(SerialPort.ONE_STOP_BIT);
      p.setParity(SerialPort.NO_PARITY);
      p.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 0, timeoutMs);
      if (!p.openPort(timeoutMs)) {
         throw new IOException("Không mở được cổng: " + systemPortName);
      }
      try {
         p.clearRTS();
         p.clearDTR();
         try {
            Thread.sleep(10L);
         } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
         }
         p.setDTR();
         p.setRTS();
         try {
            Thread.sleep(50L);
         } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
         }
         p.flushIOBuffers();
         int off = 0;
         while (off < data.length) {
            int w = p.writeBytes(data, data.length - off, off);
            if (w <= 0) {
               throw new IOException("Ghi cổng thất bại: " + systemPortName);
            }
            off += w;
         }
         if (off < data.length) {
            throw new IOException("Ghi cổng thất bại: " + systemPortName);
         }
         p.flushIOBuffers();
         try {
            Thread.sleep(80L);
         } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
         }
      } finally {
         p.closePort();
      }
   }

   private static void writeDirectCom(String systemPortName, byte[] data, int baud) throws IOException {
      String port = systemPortName.trim().toUpperCase(Locale.ROOT);
      if (!port.startsWith("COM")) {
         throw new IOException("Ten cong khong hop le: " + systemPortName);
      }
      // Co gang set mode truoc khi ghi (bo qua neu mode khong co/that bai)
      try {
         Process p = new ProcessBuilder(
            "cmd",
            "/c",
            "mode " + port + ": BAUD=" + baud + " PARITY=n DATA=8 STOP=1"
         ).redirectErrorStream(true).start();
         p.waitFor();
      } catch (Exception ignore) {
      }

      String dev = "\\\\.\\" + port;
      try (FileOutputStream out = new FileOutputStream(dev)) {
         out.write(data);
         out.flush();
      }
   }

   private static int portScore(SerialPort p) {
      String n = (p.getSystemPortName() + " " + p.getDescriptivePortName() + " " + p.getPortDescription()).toLowerCase(Locale.ROOT);
      int s = 0;
      if (n.contains("usb")) s += 5;
      if (n.contains("serial")) s += 4;
      if (n.contains("ch340") || n.contains("cp210") || n.contains("ftdi") || n.contains("prolific")) s += 6;
      if (n.contains("printer") || n.contains("pos")) s += 3;
      if (n.contains("thermal") || n.contains("receipt") || n.contains("esc/pos") || n.contains("escpos")) s += 5;
      for (String hint : XPRINTER_HINTS) {
         if (n.contains(hint)) {
            s += 12;
            break;
         }
      }
      if (n.startsWith("com1") || n.startsWith("com2")) s -= 2;
      return s;
   }
}
