package com.pos.repository;

import com.pos.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReportRepository {
   public List<ReportRepository.ProductSalesRow> allProductSalesBetween(LocalDateTime fromExclusive, LocalDateTime toExclusive) throws SQLException {
      ArrayList<ReportRepository.ProductSalesRow> list = new ArrayList<>();
      Connection conn = DBConnection.getInstance().getConnection();
      String sql = "SELECT m.name,\n"
         + "       COALESCE(SUM(CASE WHEN o.id IS NOT NULL THEN oi.quantity ELSE 0 END), 0) AS qty,\n"
         + "       COALESCE(SUM(CASE WHEN o.id IS NOT NULL THEN oi.quantity * oi.price_at_order ELSE 0 END), 0) AS rev\n"
         + "FROM menu_item m\n"
         + "LEFT JOIN order_item oi ON oi.menu_item_id = m.id\n"
         + "LEFT JOIN orders o ON o.id = oi.order_id\n"
         + "  AND o.status = 'PAID' AND o.paid_at IS NOT NULL\n"
         + "  AND o.paid_at >= ? AND o.paid_at < ?\n"
         + "GROUP BY m.id, m.name\n"
         + "ORDER BY rev DESC, qty DESC, m.name\n";

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
         ps.setString(1, OrderRepository.toSqlite(fromExclusive));
         ps.setString(2, OrderRepository.toSqlite(toExclusive));

         try (ResultSet rs = ps.executeQuery()) {
            int r = 1;

            while (rs.next()) {
               list.add(new ReportRepository.ProductSalesRow(r++, rs.getString(1), rs.getInt(2), rs.getDouble(3)));
            }
         }
      }

      return list;
   }

   public static record ProductSalesRow(int rank, String name, int quantity, double revenue) {
   }
}
