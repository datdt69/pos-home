package com.pos.repository;

import com.pos.model.Category;
import com.pos.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CategoryRepository {
   public List<Category> findAll() throws SQLException {
      ArrayList<Category> list = new ArrayList<>();
      Connection conn = DBConnection.getInstance().getConnection();

      try (
         PreparedStatement ps = conn.prepareStatement("SELECT id, name FROM category ORDER BY name");
         ResultSet rs = ps.executeQuery();
      ) {
         while (rs.next()) {
            list.add(new Category(rs.getInt(1), rs.getString(2)));
         }
      }

      return list;
   }

   public Optional<Category> findById(int id) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();

      try (PreparedStatement ps = conn.prepareStatement("SELECT id, name FROM category WHERE id = ?")) {
         ps.setInt(1, id);

         try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
               return Optional.of(new Category(rs.getInt(1), rs.getString(2)));
            }
         }
      }

      return Optional.empty();
   }

   public int insert(String name) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();

      try (PreparedStatement ps = conn.prepareStatement("INSERT INTO category (name) VALUES (?)", 1)) {
         ps.setString(1, name);
         ps.executeUpdate();

         try (ResultSet g = ps.getGeneratedKeys()) {
            if (g.next()) {
               return g.getInt(1);
            }
         }
      }

      throw new SQLException("Không tạo được category");
   }

   public void update(int id, String name) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();

      try (PreparedStatement ps = conn.prepareStatement("UPDATE category SET name = ? WHERE id = ?")) {
         ps.setString(1, name);
         ps.setInt(2, id);
         ps.executeUpdate();
      }
   }

   public void delete(int id) throws SQLException {
      Connection conn = DBConnection.getInstance().getConnection();

      try (PreparedStatement ps = conn.prepareStatement("DELETE FROM category WHERE id = ?")) {
         ps.setInt(1, id);
         ps.executeUpdate();
      }
   }
}
