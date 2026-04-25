package com.pos.repository;

import com.pos.model.MenuItem;
import com.pos.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MenuRepository {
   public List<MenuItem> findAll() throws SQLException {
      ArrayList<MenuItem> list = new ArrayList<>();
      Connection conn = DBConnection.getInstance().getConnection();
      String sql = "SELECT id, name, price, category_id, is_available FROM menu_item ORDER BY name";

      try (
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery();
      ) {
         while (rs.next()) {
            list.add(mapRow(rs));
         }
      }

      return list;
   }

   public List<MenuItem> findByCategoryId(Integer categoryId) throws SQLException {
      ArrayList<MenuItem> list = new ArrayList<>();
      Connection conn = DBConnection.getInstance().getConnection();
      String sql = categoryId == null
         ? "SELECT id, name, price, category_id, is_available FROM menu_item ORDER BY name"
         : "SELECT id, name, price, category_id, is_available FROM menu_item WHERE category_id = ? ORDER BY name";

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
         if (categoryId != null) {
            ps.setInt(1, categoryId);
         }

         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               list.add(mapRow(rs));
            }
         }
      }

      return list;
   }

   public List<MenuItem> searchByName(String query, Integer categoryId) throws SQLException {
      ArrayList<MenuItem> list = new ArrayList<>();
      Connection conn = DBConnection.getInstance().getConnection();
      String like = query != null && !query.isBlank() ? "%" + query.trim() + "%" : null;
      StringBuilder sb = new StringBuilder("SELECT id, name, price, category_id, is_available FROM menu_item WHERE 1=1 ");
      if (like != null) {
         sb.append("AND name LIKE ? ");
      }

      if (categoryId != null) {
         sb.append("AND category_id = ? ");
      }

      sb.append("ORDER BY name");

      try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
         int i = 1;
         if (like != null) {
            ps.setString(i++, like);
         }

         if (categoryId != null) {
            ps.setInt(i, categoryId);
         }

         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               list.add(mapRow(rs));
            }
         }
      }

      return list;
   }

   public Optional<MenuItem> findById(int id) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();

      try (PreparedStatement ps = conn.prepareStatement("SELECT id, name, price, category_id, is_available FROM menu_item WHERE id = ?")) {
         ps.setInt(1, id);

         try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
               return Optional.of(mapRow(rs));
            }
         }
      }

      return Optional.empty();
   }

   public int insert(String name, double price, Integer categoryId, boolean available) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();
      String sql = "INSERT INTO menu_item (name, price, category_id, is_available) VALUES (?,?,?,?)";

      try (PreparedStatement ps = conn.prepareStatement(sql, 1)) {
         ps.setString(1, name);
         ps.setDouble(2, price);
         if (categoryId == null) {
            ps.setNull(3, 4);
         } else {
            ps.setInt(3, categoryId);
         }

         ps.setInt(4, available ? 1 : 0);
         ps.executeUpdate();

         try (ResultSet g = ps.getGeneratedKeys()) {
            if (g.next()) {
               return g.getInt(1);
            }
         }
      }

      throw new SQLException("Không tạo được món");
   }

   public void update(int id, String name, double price, Integer categoryId, boolean available) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();
      String sql = "UPDATE menu_item SET name = ?, price = ?, category_id = ?, is_available = ? WHERE id = ?";

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
         ps.setString(1, name);
         ps.setDouble(2, price);
         if (categoryId == null) {
            ps.setNull(3, 4);
         } else {
            ps.setInt(3, categoryId);
         }

         ps.setInt(4, available ? 1 : 0);
         ps.setInt(5, id);
         ps.executeUpdate();
      }
   }

   public void setAvailable(int id, boolean available) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();

      try (PreparedStatement ps = conn.prepareStatement("UPDATE menu_item SET is_available = ? WHERE id = ?")) {
         ps.setInt(1, available ? 1 : 0);
         ps.setInt(2, id);
         ps.executeUpdate();
      }
   }

   public void deleteById(int id) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();

      try (PreparedStatement ps = conn.prepareStatement("DELETE FROM menu_item WHERE id = ?")) {
         ps.setInt(1, id);
         ps.executeUpdate();
      }
   }

   public boolean isReferencedInOpenOrders(int menuItemId) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();
      String sql = "SELECT 1 FROM order_item oi\nJOIN orders o ON o.id = oi.order_id\nWHERE oi.menu_item_id = ? AND o.status = 'OPEN'\nLIMIT 1\n";

      boolean var6;
      try (PreparedStatement ps = conn.prepareStatement(sql)) {
         ps.setInt(1, menuItemId);

         try (ResultSet rs = ps.executeQuery()) {
            var6 = rs.next();
         }
      }

      return var6;
   }

   public boolean categoryHasItems(int categoryId) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();

      boolean var5;
      try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM menu_item WHERE category_id = ? LIMIT 1")) {
         ps.setInt(1, categoryId);

         try (ResultSet rs = ps.executeQuery()) {
            var5 = rs.next();
         }
      }

      return var5;
   }

   private static MenuItem mapRow(ResultSet rs) throws SQLException {
      int cat = rs.getInt(4);
      return new MenuItem(rs.getInt(1), rs.getString(2), rs.getDouble(3), rs.wasNull() ? null : cat, rs.getInt(5) == 1);
   }
}
