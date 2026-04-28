package com.pos.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DBConnection {
   private static volatile DBConnection instance;
   private final String jdbcUrl = resolveJdbcUrl();
   private Connection connection = this.openConnection();

   private DBConnection() throws SQLException {
      this.initSchema();
   }

   public static DBConnection getInstance() throws SQLException {
      DBConnection local = instance;
      if (local == null) {
         synchronized (DBConnection.class) {
            local = instance;
            if (local == null) {
               local = new DBConnection();
               instance = local;
            }
         }
      }

      return local;
   }

   private static String resolveJdbcUrl() {
      String override = System.getProperty("pos.db.url");
      if (override != null && !override.isBlank()) {
         return override.trim();
      } else {
         String path = System.getProperty("pos.db.path");
         return path != null && !path.isBlank() ? "jdbc:sqlite:" + path.trim() : "jdbc:sqlite:pos.db";
      }
   }

   private Connection openConnection() throws SQLException {
      try {
         Class.forName("org.sqlite.JDBC");
      } catch (ClassNotFoundException var2) {
         throw new SQLException("Không tải được driver SQLite", var2);
      }

      return DriverManager.getConnection(this.jdbcUrl);
   }

   public synchronized Connection getConnection() throws SQLException {
      if (this.connection == null || this.connection.isClosed()) {
         this.connection = this.openConnection();
      }

      return this.connection;
   }

   public synchronized void close() {
      if (this.connection != null) {
         try {
            this.connection.close();
         } catch (SQLException var5) {
         } finally {
            this.connection = null;
         }
      }
   }

   private void initSchema() throws SQLException {
      try (Statement st = this.getConnection().createStatement()) {
         st.execute("PRAGMA foreign_keys = ON");
         st.executeUpdate("CREATE TABLE IF NOT EXISTS category (\n    id INTEGER PRIMARY KEY AUTOINCREMENT,\n    name TEXT NOT NULL\n)");
         st.executeUpdate(
            "CREATE TABLE IF NOT EXISTS menu_item (\n    id INTEGER PRIMARY KEY AUTOINCREMENT,\n    name TEXT NOT NULL,\n    price REAL NOT NULL,\n    category_id INTEGER,\n    is_available INTEGER DEFAULT 1,\n    FOREIGN KEY (category_id) REFERENCES category(id)\n)"
         );
         st.executeUpdate(
            "CREATE TABLE IF NOT EXISTS orders (\n    id INTEGER PRIMARY KEY AUTOINCREMENT,\n    order_number TEXT,\n    status TEXT,\n    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,\n    paid_at DATETIME,\n    total REAL DEFAULT 0\n)"
         );
         st.executeUpdate(
            "CREATE TABLE IF NOT EXISTS order_item (\n    id INTEGER PRIMARY KEY AUTOINCREMENT,\n    order_id INTEGER NOT NULL,\n    menu_item_id INTEGER NOT NULL,\n    quantity INTEGER DEFAULT 1,\n    price_at_order REAL,\n    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,\n    FOREIGN KEY (menu_item_id) REFERENCES menu_item(id)\n)"
         );
      }

      this.migratePaidAtUtcToVietnamIfNeeded();
      this.migrateCatalogToMixueIfNeeded();

      try {
         if (this.isEmpty("category")) {
            this.seedData();
         }
      } catch (SQLException var5) {
         throw var5;
      }
   }

   private void migratePaidAtUtcToVietnamIfNeeded() throws SQLException {
      try (
         Statement st = this.getConnection().createStatement();
         ResultSet rs = st.executeQuery("PRAGMA user_version");
      ) {
         if (rs.next() && rs.getInt(1) >= 1) {
            return;
         }
      }

      try (Statement stx = this.getConnection().createStatement()) {
         stx.executeUpdate("UPDATE orders\nSET paid_at = datetime(paid_at, '+7 hours')\nWHERE paid_at IS NOT NULL\n");
         stx.executeUpdate("PRAGMA user_version = 1");
      }
   }

   /**
    * One-time catalog reset:
    * - wipe old demo data
    * - import MIXUE menu from current shop board
    */
   private void migrateCatalogToMixueIfNeeded() throws SQLException {
      try (
         Statement st = this.getConnection().createStatement();
         ResultSet rs = st.executeQuery("PRAGMA user_version");
      ) {
         if (rs.next() && rs.getInt(1) >= 3) {
            return;
         }
      }

      Connection conn = this.getConnection();
      conn.setAutoCommit(false);
      try (Statement st = conn.createStatement()) {
         st.executeUpdate("DELETE FROM order_item");
         st.executeUpdate("DELETE FROM orders");
         st.executeUpdate("DELETE FROM menu_item");
         st.executeUpdate("DELETE FROM category");
      }
      this.seedData();
      try (Statement st2 = conn.createStatement()) {
         st2.executeUpdate("PRAGMA user_version = 3");
      }
      conn.commit();
      conn.setAutoCommit(true);
   }

   private boolean isEmpty(String table) throws SQLException {
      try (
         PreparedStatement ps = this.getConnection().prepareStatement("SELECT COUNT(*) FROM " + table);
         ResultSet rs = ps.executeQuery();
      ) {
         if (rs.next()) {
            return rs.getInt(1) == 0;
         }
      }

      return true;
   }

   private void seedData() throws SQLException {
      Connection conn = this.getConnection();
      boolean ac = conn.getAutoCommit();
      if (ac) {
         conn.setAutoCommit(false);
      }

      try {
         int catTraSua = this.insertCategory(conn, "Trà sữa");
         int catTraHoaQua = this.insertCategory(conn, "Trà hoa quả");
         int catKem = this.insertCategory(conn, "Kem");
         int catDoAnVat = this.insertCategory(conn, "Đồ ăn vặt");
         int catMyCay = this.insertCategory(conn, "Mỳ cay");
         int catGaRan = this.insertCategory(conn, "Đùi gà sốt");

         String insertMenu = "INSERT INTO menu_item (name, price, category_id, is_available) VALUES (?,?,?,?)\n";
         try (PreparedStatement ps = conn.prepareStatement(insertMenu)) {
            this.addItem(ps, "Sữa tươi trân châu đường đen", 20000.0, catTraSua, 1);
            this.addItem(ps, "Trà sữa trân châu đường đen", 25000.0, catTraSua, 1);
            this.addItem(ps, "Trà sữa 3Q", 25000.0, catTraSua, 1);
            this.addItem(ps, "Trà sữa bá vương", 30000.0, catTraSua, 1);
            this.addItem(ps, "Trà sữa socola", 25000.0, catTraSua, 1);
            this.addItem(ps, "Trà sữa thái đỏ", 25000.0, catTraSua, 1);
            this.addItem(ps, "Trà sữa ô long", 25000.0, catTraSua, 1);
            this.addItem(ps, "Trà sữa chanh dây", 25000.0, catTraSua, 1);
            this.addItem(ps, "Sữa thạch kiwi kiwi", 22000.0, catTraSua, 1);
            this.addItem(ps, "Trà ô long kiwi", 20000.0, catTraHoaQua, 1);
            this.addItem(ps, "Trà mâm xôi", 22000.0, catTraHoaQua, 1);
            this.addItem(ps, "Trà đào bốn mùa", 22000.0, catTraHoaQua, 1);
            this.addItem(ps, "Nước chanh tươi lạnh", 15000.0, catTraHoaQua, 1);
            this.addItem(ps, "Trà chanh lá lô hội", 17000.0, catTraHoaQua, 1);
            this.addItem(ps, "Trà đào tứ kỳ xuân", 20000.0, catTraHoaQua, 1);
            this.addItem(ps, "Trà ô long bốn mùa", 15000.0, catTraHoaQua, 1);
            this.addItem(ps, "Đường cam lá lô hội", 30000.0, catTraHoaQua, 1);
            this.addItem(ps, "Hồng trà mật ong", 15000.0, catTraHoaQua, 1);
            this.addItem(ps, "Hồng trà chanh", 15000.0, catTraHoaQua, 1);
            this.addItem(ps, "Kem ốc quế", 10000.0, catKem, 1);
            this.addItem(ps, "Super sundae trân châu đường đen", 25000.0, catKem, 1);
            this.addItem(ps, "Super sundae xoài", 25000.0, catKem, 1);
            this.addItem(ps, "Super sundae socola", 25000.0, catKem, 1);
            this.addItem(ps, "Super sundae dâu tây", 25000.0, catKem, 1);
            this.addItem(ps, "Super sundae mâm xôi", 25000.0, catKem, 1);
            this.addItem(ps, "Trà kem nho", 25000.0, catKem, 1);
            this.addItem(ps, "Trà kem mâm xôi", 25000.0, catKem, 1);
            this.addItem(ps, "Xúc xích chiên", 10000.0, catDoAnVat, 1);
            this.addItem(ps, "Lạp xưởng nướng", 15000.0, catDoAnVat, 1);
            this.addItem(ps, "Xiên thập cẩm cá viên", 20000.0, catDoAnVat, 1);
            this.addItem(ps, "Khoai tây chiên lắc phô mai", 30000.0, catDoAnVat, 1);
            this.addItem(ps, "Xúc xích cá viên", 35000.0, catDoAnVat, 1);
            this.addItem(ps, "Mỳ cay kim chi thập cẩm cá viên", 45000.0, catDoAnVat, 1);
            this.addItem(ps, "Mỳ cay hải sản", 45000.0, catMyCay, 1);
            this.addItem(ps, "Mỳ cay kim chi bò", 40000.0, catMyCay, 1);
            this.addItem(ps, "Mỳ cay kim chi", 35000.0, catMyCay, 1);
            this.addItem(ps, "Mỳ cay kim chi thập cẩm", 45000.0, catMyCay, 1);
            this.addItem(ps, "Mỳ cay kim chi cá viên", 35000.0, catMyCay, 1);
            this.addItem(ps, "Đùi gà rán", 30000.0, catGaRan, 1);
            this.addItem(ps, "Đùi gà sốt cay", 35000.0, catGaRan, 1);
            this.addItem(ps, "Đùi gà sốt gà ngọt", 35000.0, catGaRan, 1);
            this.addItem(ps, "Đùi gà sốt kem hành", 35000.0, catGaRan, 1);
            this.addItem(ps, "Đùi gà sốt phô mai", 35000.0, catGaRan, 1);
            ps.executeBatch();
         }
         conn.commit();
      } catch (SQLException e) {
         conn.rollback();
         throw e;
      } finally {
         conn.setAutoCommit(ac);
      }
   }

   private int insertCategory(Connection conn, String name) throws SQLException {
      try (PreparedStatement ps = conn.prepareStatement("INSERT INTO category (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
         ps.setString(1, name);
         ps.executeUpdate();
         try (ResultSet rs = ps.getGeneratedKeys()) {
            if (rs.next()) {
               return rs.getInt(1);
            }
         }
      }
      throw new SQLException("Khong tao duoc category: " + name);
   }

   private void addItem(PreparedStatement ps, String name, double price, int cat, int av) throws SQLException {
      ps.setString(1, name);
      ps.setDouble(2, price);
      ps.setInt(3, cat);
      ps.setInt(4, av);
      ps.addBatch();
   }

   public String getJdbcUrl() {
      return this.jdbcUrl;
   }
}
