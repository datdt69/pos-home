package com.pos.util;

import com.fazecast.jSerialComm.SerialPort;
import com.pos.model.Order;
import com.pos.model.OrderItem;
import com.pos.repository.OrderRepository;
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
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * In nhiệt ESC/POS qua cổng COM (jSerialComm). Văn bản: thử windows-1258, sau đó x-Windows-50225, CP874; cuối cùng ISO-8859-1.
 */
public final class PrinterUtil {
   public static final int TIMEOUT_MS = 3000;
   public static final DateTimeFormatter RECEIPT_DF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));
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
      return Stream.of(ports).map(SerialPort::getSystemPortName).collect(Collectors.toList());
   }

   public static String detectPrinterPort() {
      for (String name : getAvailablePorts()) {
         if (testPort(name)) {
            return name;
         }
      }
      return null;
   }

   private static boolean testPort(String systemPortName) {
      try {
         writeToPort(systemPortName, new byte[]{0x1B, 0x40}, TIMEOUT_MS);
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   public static void printTest(String port) throws IOException {
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
      writeToPort(port, toByteArray(buf), TIMEOUT_MS);
   }

   public static void printReceipt(
      Order order,
      List<OrderItem> items,
      String port,
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
      writeToPort(port, toByteArray(buf), TIMEOUT_MS);
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

   private static void writeToPort(String systemPortName, byte[] data, int timeoutMs) throws IOException {
      if (data == null || data.length == 0) {
         return;
      } else {
         SerialPort p = SerialPort.getCommPort(systemPortName);
         p.setBaudRate(9600);
         p.setNumDataBits(8);
         p.setNumStopBits(SerialPort.ONE_STOP_BIT);
         p.setParity(SerialPort.NO_PARITY);
         p.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, timeoutMs);
         if (!p.openPort(timeoutMs)) {
            throw new IOException("Không mở được cổng: " + systemPortName);
         } else {
            try {
               int w = p.writeBytes(data, data.length);
               if (w < 0) {
                  throw new IOException("Ghi cổng thất bại: " + systemPortName);
               }
            } finally {
               p.closePort();
            }
         }
      }
   }
}
