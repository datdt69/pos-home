package com.pos.repository;

import com.pos.model.Order;
import com.pos.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OrderRepository {
   private static final String OPEN = "OPEN";
   public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

   public List<Order> findOpenOrders() throws SQLException {
      ArrayList<Order> list = new ArrayList<>();
      Connection conn = DBConnection.getInstance().getConnection();
      String sql = "SELECT id, order_number, status, created_at, paid_at, total\nFROM orders WHERE status = 'OPEN' ORDER BY id\n";

      try (
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery();
      ) {
         while (rs.next()) {
            list.add(mapOrder(rs));
         }
      }

      return list;
   }

   public Optional<Order> findById(int id) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();

      try (PreparedStatement ps = conn.prepareStatement("SELECT id, order_number, status, created_at, paid_at, total FROM orders WHERE id = ?")) {
         ps.setInt(1, id);

         try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
               return Optional.of(mapOrder(rs));
            }
         }
      }

      return Optional.empty();
   }

   public int createOpenOrder() throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();
      conn.setAutoCommit(false);

      int var26;
      try {
         int id;
         try (PreparedStatement ps = conn.prepareStatement("INSERT INTO orders (order_number, status, total) VALUES (?, ?, 0)", 1)) {
            ps.setString(1, "");
            ps.setString(2, "OPEN");
            ps.executeUpdate();

            try (ResultSet g = ps.getGeneratedKeys()) {
               if (!g.next()) {
                  throw new SQLException("Không tạo được đơn");
               }

               id = g.getInt(1);
            }
         }

         String num = "Đơn " + id;

         try (PreparedStatement ps = conn.prepareStatement("UPDATE orders SET order_number = ? WHERE id = ?")) {
            ps.setString(1, num);
            ps.setInt(2, id);
            ps.executeUpdate();
         }

         conn.commit();
         var26 = id;
      } catch (SQLException var22) {
         conn.rollback();
         throw var22;
      } finally {
         conn.setAutoCommit(true);
      }

      return var26;
   }

   public void recalculateTotal(int orderId) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();
      String sql = "UPDATE orders SET total = (\n    SELECT COALESCE(SUM(quantity * price_at_order), 0) FROM order_item WHERE order_id = ?\n) WHERE id = ?\n";

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
         ps.setInt(1, orderId);
         ps.setInt(2, orderId);
         ps.executeUpdate();
      }
   }

   public void markPaid(int orderId) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();
      this.recalculateTotal(orderId);
      String paidLocal = toSqlite(LocalDateTime.now(VIETNAM));
      String sql = "UPDATE orders\nSET status = 'PAID', paid_at = ?\nWHERE id = ? AND status = 'OPEN'\n";

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
         ps.setString(1, paidLocal);
         ps.setInt(2, orderId);
         if (ps.executeUpdate() == 0) {
            throw new SQLException("Không cập nhật được trạng thái thanh toán (đơn không còn OPEN hoặc không tồn tại)");
         }
      }
   }

   public void deleteOrderCascade(int orderId) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();

      try (
         PreparedStatement ps1 = conn.prepareStatement("DELETE FROM order_item WHERE order_id = ?");
         PreparedStatement ps2 = conn.prepareStatement("DELETE FROM orders WHERE id = ?");
      ) {
         ps1.setInt(1, orderId);
         ps1.executeUpdate();
         ps2.setInt(1, orderId);
         ps2.executeUpdate();
      }
   }

   /**
    * Xóa đơn đã thanh toán (và dòng order_item) có {@code paid_at} trước mốc, để giảm dung lượng DB.
    *
    * @return số đơn đã xóa
    */
   public int purgePaidOrdersBefore(LocalDateTime paidBeforeExclusive) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();
      String c = toSqlite(paidBeforeExclusive);
      conn.setAutoCommit(false);
      int deleted;
      try {
         try (PreparedStatement ps1 = conn.prepareStatement(
            "DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE status = 'PAID' AND paid_at IS NOT NULL AND paid_at < ?)"
         )) {
            ps1.setString(1, c);
            ps1.executeUpdate();
         }
         try (PreparedStatement ps2 = conn.prepareStatement(
            "DELETE FROM orders WHERE status = 'PAID' AND paid_at IS NOT NULL AND paid_at < ?"
         )) {
            ps2.setString(1, c);
            deleted = ps2.executeUpdate();
         }
         conn.commit();
      } catch (SQLException e) {
         conn.rollback();
         throw e;
      } finally {
         conn.setAutoCommit(true);
      }
      return deleted;
   }

   /** Cắt dữ liệu đã thanh toán cũ hơn {@code months} tháng (mốc: 0h ngày, múi giờ VN). */
   public int purgePaidOrdersOlderThanMonths(int months) throws SQLException {
      if (months < 1) {
         return 0;
      }
      LocalDate today = LocalDate.now(VIETNAM);
      LocalDateTime cutoff = today.minusMonths(months).atStartOfDay();
      return this.purgePaidOrdersBefore(cutoff);
   }

   public int countPaidBetween(LocalDateTime fromExclusive, LocalDateTime toExclusive) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();
      String sql = "SELECT COUNT(*) FROM orders\nWHERE status = 'PAID' AND paid_at IS NOT NULL\nAND paid_at >= ? AND paid_at < ?\n";

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
         ps.setString(1, toSqlite(fromExclusive));
         ps.setString(2, toSqlite(toExclusive));

         try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
               return rs.getInt(1);
            }
         }
      }

      return 0;
   }

   public double sumRevenueBetween(LocalDateTime fromExclusive, LocalDateTime toExclusive) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();
      String sql = "SELECT COALESCE(SUM(total), 0) FROM orders\nWHERE status = 'PAID' AND paid_at IS NOT NULL\nAND paid_at >= ? AND paid_at < ?\n";

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
         ps.setString(1, toSqlite(fromExclusive));
         ps.setString(2, toSqlite(toExclusive));

         try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
               return rs.getDouble(1);
            }
         }
      }

      return 0.0;
   }

   public List<Order> listPaidBetween(LocalDateTime fromExclusive, LocalDateTime toExclusive) throws SQLException {
      ArrayList<Order> list = new ArrayList<>();
      Connection conn = DBConnection.getInstance().getConnection();
      String sql = "SELECT id, order_number, status, created_at, paid_at, total\nFROM orders\nWHERE status = 'PAID' AND paid_at IS NOT NULL\nAND paid_at >= ? AND paid_at < ?\nORDER BY paid_at DESC, id DESC\n";

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
         ps.setString(1, toSqlite(fromExclusive));
         ps.setString(2, toSqlite(toExclusive));

         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               list.add(mapOrder(rs));
            }
         }
      }

      return list;
   }

   public static String toSqlite(LocalDateTime dt) {
      return dt == null ? null : dt.toString().replace('T', ' ');
   }

   private static LocalDateTime toLocalDateTime(String s) {
      if (s != null && !s.isEmpty()) {
         String norm = s.trim();
         if (norm.contains(" ") && !norm.contains("T")) {
            norm = norm.replace(" ", "T");
         }

         if (norm.length() == 10) {
            norm = norm + "T00:00:00";
         }

         if (norm.length() > 19) {
            norm = norm.substring(0, 19);
         }

         return LocalDateTime.parse(norm);
      } else {
         return null;
      }
   }

   private static Order mapOrder(ResultSet rs) throws SQLException {
      Order o = new Order();
      o.setId(rs.getInt(1));
      o.setOrderNumber(rs.getString(2));
      o.setStatus(rs.getString(3));
      o.setCreatedAt(toLocalDateTime(rs.getString(4)));
      String p = rs.getString(5);
      o.setPaidAt(p == null ? null : toLocalDateTime(p));
      o.setTotal(rs.getDouble(6));
      return o;
   }
}
