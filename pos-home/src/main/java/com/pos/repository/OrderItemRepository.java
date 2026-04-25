package com.pos.repository;

import com.pos.model.OrderItem;
import com.pos.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OrderItemRepository {
   public List<OrderItem> findByOrderIdWithNames(int orderId) throws SQLException {
      ArrayList<OrderItem> list = new ArrayList<>();
      Connection conn = DBConnection.getInstance().getConnection();
      String sql = "SELECT oi.id, oi.order_id, oi.menu_item_id, oi.quantity, oi.price_at_order, m.name\nFROM order_item oi\nJOIN menu_item m ON m.id = oi.menu_item_id\nWHERE oi.order_id = ?\nORDER BY oi.id\n";

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
         ps.setInt(1, orderId);

         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               OrderItem oi = new OrderItem();
               oi.setId(rs.getInt(1));
               oi.setOrderId(rs.getInt(2));
               oi.setMenuItemId(rs.getInt(3));
               oi.setQuantity(rs.getInt(4));
               oi.setPriceAtOrder(rs.getDouble(5));
               oi.setMenuName(rs.getString(6));
               list.add(oi);
            }
         }
      }

      return list;
   }

   public void addOrIncrement(int orderId, int menuItemId, double price) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();
      String find = "SELECT id, quantity FROM order_item\nWHERE order_id = ? AND menu_item_id = ?\n";

      try (PreparedStatement ps = conn.prepareStatement(find)) {
         ps.setInt(1, orderId);
         ps.setInt(2, menuItemId);

         try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
               int oiId = rs.getInt(1);
               int q = rs.getInt(2) + 1;

               try (PreparedStatement up = conn.prepareStatement("UPDATE order_item SET quantity = ? WHERE id = ?")) {
                  up.setInt(1, q);
                  up.setInt(2, oiId);
                  up.executeUpdate();
                  return;
               }
            }
         }
      }

      try (PreparedStatement ins = conn.prepareStatement("INSERT INTO order_item (order_id, menu_item_id, quantity, price_at_order) VALUES (?,?,1,?)")) {
         ins.setInt(1, orderId);
         ins.setInt(2, menuItemId);
         ins.setDouble(3, price);
         ins.executeUpdate();
      }
   }

   public void updateQuantity(int orderItemId, int quantity) throws SQLException {
      if (quantity >= 1) {
         Connection conn = DBConnection.getInstance().getConnection();

         try (PreparedStatement ps = conn.prepareStatement("UPDATE order_item SET quantity = ? WHERE id = ?")) {
            ps.setInt(1, quantity);
            ps.setInt(2, orderItemId);
            ps.executeUpdate();
         }
      }
   }

   public void deleteById(int orderItemId) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();

      try (PreparedStatement ps = conn.prepareStatement("DELETE FROM order_item WHERE id = ?")) {
         ps.setInt(1, orderItemId);
         ps.executeUpdate();
      }
   }

   public int countByOrderId(int orderId) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();

      try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM order_item WHERE order_id = ?")) {
         ps.setInt(1, orderId);

         try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
               return rs.getInt(1);
            }
         }
      }

      return 0;
   }

   public Optional<Integer> getOrderIdForItem(int orderItemId) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();

      try (PreparedStatement ps = conn.prepareStatement("SELECT order_id FROM order_item WHERE id = ?")) {
         ps.setInt(1, orderItemId);

         try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
               return Optional.of(rs.getInt(1));
            }
         }
      }

      return Optional.empty();
   }
}
