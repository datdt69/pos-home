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
      conn.setAutoCommit(false);

      try (Statement st = conn.createStatement()) {
         st.executeUpdate("INSERT INTO category (name) VALUES ('Đồ ăn')");
         st.executeUpdate("INSERT INTO category (name) VALUES ('Đồ uống')");
         st.executeUpdate("INSERT INTO category (name) VALUES ('Tráng miệng')");
      }

      int catAn = 1;
      int catUong = 2;
      int catMiet = 3;
      String insertMenu = "INSERT INTO menu_item (name, price, category_id, is_available) VALUES (?,?,?,?)\n";

      try (PreparedStatement ps = conn.prepareStatement(insertMenu)) {
         this.addItem(ps, "Phở bò", 45000.0, catAn, 1);
         this.addItem(ps, "Bún chả Hà Nội", 55000.0, catAn, 1);
         this.addItem(ps, "Cơm tấm sườn", 50000.0, catAn, 1);
         this.addItem(ps, "Gỏi cuốn", 35000.0, catAn, 1);
         this.addItem(ps, "Bánh mì thịt", 25000.0, catAn, 1);
         this.addItem(ps, "Trà đá", 5000.0, catUong, 1);
         this.addItem(ps, "Cà phê sữa đá", 18000.0, catUong, 1);
         this.addItem(ps, "Nước mía", 12000.0, catUong, 1);
         this.addItem(ps, "Trà chanh", 15000.0, catUong, 1);
         this.addItem(ps, "Chè khúc bạch", 22000.0, catMiet, 1);
         ps.executeBatch();
      }

      conn.commit();
      conn.setAutoCommit(true);
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
