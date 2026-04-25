package com.pos;

import com.pos.repository.OrderRepository;
import com.pos.util.DBConnection;
import com.pos.util.UiAlerts;
import java.sql.SQLException;
import java.net.URL;
import java.util.Locale;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class Main extends Application {
   public void start(Stage stage) {
      try {
         DBConnection.getInstance();
         /* Mỗi lần mở app: xóa đơn đã thanh toán (PAID) có paid_at cũ hơn 3 tháng — giảm dung lượng DB. */
         try {
            int purged = new OrderRepository().purgePaidOrdersOlderThanMonths(3);
            if (purged > 0) {
               System.out.println("POS: đã dọn " + purged + " đơn đã thanh toán cũ hơn 3 tháng");
            }
         } catch (SQLException e) {
            e.printStackTrace();
         }
         URL url = Main.class.getResource("/com/pos/main.fxml");
         if (url == null) {
            throw new IllegalStateException("Không tìm thấy main.fxml");
         }

         FXMLLoader loader = new FXMLLoader(url);
         Parent root = (Parent)loader.load();
         Rectangle2D b = Screen.getPrimary().getVisualBounds();
         double sw = b.getWidth();
         double sh = b.getHeight();
         Scene scene = new Scene(root, sw, sh);
         URL css = Main.class.getResource("/com/pos/styles.css");
         if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
         }

         stage.setTitle("POS — Hộ kinh doanh gia đình");
         stage.setX(b.getMinX());
         stage.setY(b.getMinY());
         stage.setWidth(sw);
         stage.setHeight(sh);
         stage.setMinWidth(800.0);
         stage.setMinHeight(600.0);
         stage.setScene(scene);
         stage.setMaximized(true);
         stage.show();
      } catch (Exception var12) {
         var12.printStackTrace();
         UiAlerts.error("Lỗi khởi động", "Không thể mở ứng dụng: " + var12.getMessage());
      }
   }

   public void stop() {
      try {
         DBConnection.getInstance().close();
      } catch (Exception var2) {
         var2.printStackTrace();
      }
   }

   public static void main(String[] args) {
      Locale.setDefault(Locale.forLanguageTag("vi-VN"));
      Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
         e.printStackTrace();
         Platform.runLater(() -> UiAlerts.error("Lỗi", "Có lỗi: " + e.getMessage()));
      });
      launch(args);
   }
}
