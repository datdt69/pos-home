package com.pos.controller;

import com.pos.util.UiAlerts;
import java.net.URL;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class MainController {
   @FXML
   private StackPane contentStack;
   @FXML
   private Button btnOrders;
   @FXML
   private Button btnMenu;
   @FXML
   private Button btnReport;
   @FXML
   private Button btnSettings;
   @FXML
   private Label navClock;
   @FXML
   private Label navDate;
   private Node viewOrders;
   private Node viewMenu;
   private Node viewReport;
   private Node viewSettings;

   @FXML
   private void initialize() {
      List<String> loadErrors = new ArrayList<>();
      this.viewOrders = this.loadFxml("/com/pos/order.fxml", loadErrors);
      this.viewMenu = this.loadFxml("/com/pos/menu.fxml", loadErrors);
      this.viewReport = this.loadFxml("/com/pos/report.fxml", loadErrors);
      this.viewSettings = this.loadFxml("/com/pos/settings.fxml", loadErrors);

      for (Node v : new Node[]{this.viewOrders, this.viewMenu, this.viewReport, this.viewSettings}) {
         if (v instanceof Region r) {
            r.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            StackPane.setAlignment(r, Pos.TOP_LEFT);
         }
      }

      if (!loadErrors.isEmpty()) {
         StringBuilder msg = new StringBuilder();

         for (String line : loadErrors) {
            if (msg.length() > 0) {
               msg.append("\n—\n");
            }

            msg.append(line);
         }

         msg.append("\n\n(Đã build lại chưa? Cập nhật pos-app.jar cùng thư mục jfx\\ và lib\\ vào C:\\POS-App)");
         UiAlerts.error("Lỗi tải màn hình", msg.toString());
      }

      if (this.viewOrders != null) {
         this.contentStack.getChildren().setAll(new Node[]{this.viewOrders});
         this.setActiveNav(this.btnOrders);
      } else if (this.viewMenu != null) {
         this.contentStack.getChildren().setAll(new Node[]{this.viewMenu});
         this.setActiveNav(this.btnMenu);
      } else if (this.viewReport != null) {
         this.contentStack.getChildren().setAll(new Node[]{this.viewReport});
         this.setActiveNav(this.btnReport);
      } else if (this.viewSettings != null) {
         this.contentStack.getChildren().setAll(new Node[]{this.viewSettings});
         this.setActiveNav(this.btnSettings);
      } else {
         Label err = new Label(
            "Không tải được giao diện. Chạy: mvn -DskipTests package rồi cập nhật pos-app.jar cùng thư mục jfx\\ và lib\\ (xem chay_pos.log tại thư mục chạy)."
         );
         err.setWrapText(true);
         err.getStyleClass().add("order-section-title");
         this.contentStack.getChildren().setAll(new Node[]{err});
         this.setActiveNav(this.btnOrders);
      }

      this.startHeaderClock();
   }

   private void startHeaderClock() {
      if (this.navClock != null && this.navDate != null) {
         Locale vi = Locale.forLanguageTag("vi-VN");
         DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
         DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", vi);
         Runnable tick = () -> {
            ZonedDateTime n = ZonedDateTime.now(ZoneId.systemDefault());
            this.navClock.setText(n.format(timeFmt));
            this.navDate.setText(n.format(dateFmt));
         };
         tick.run();
         Timeline t = new Timeline(new KeyFrame[]{new KeyFrame(Duration.seconds(1.0), e -> tick.run(), new KeyValue[0])});
         t.setCycleCount(-1);
         t.play();
      }
   }

   @FXML
   private void onOrders() {
      if (this.viewOrders != null) {
         this.contentStack.getChildren().setAll(new Node[]{this.viewOrders});
         this.setActiveNav(this.btnOrders);
      }
   }

   @FXML
   private void onMenu() {
      if (this.viewMenu != null) {
         this.contentStack.getChildren().setAll(new Node[]{this.viewMenu});
         this.setActiveNav(this.btnMenu);
      }
   }

   @FXML
   private void onReport() {
      if (this.viewReport != null) {
         this.contentStack.getChildren().setAll(new Node[]{this.viewReport});
         this.setActiveNav(this.btnReport);
      } else {
         UiAlerts.error(
            "Báo cáo",
            "Không tải được màn hình Báo cáo. Khởi động lại app; nếu vẫn lỗi, chạy mvn -DskipTests package và cập nhật bản build."
         );
      }
   }

   @FXML
   private void onSettings() {
      if (this.viewSettings != null) {
         this.contentStack.getChildren().setAll(new Node[]{this.viewSettings});
         this.setActiveNav(this.btnSettings);
      }
   }

   private void setActiveNav(Button active) {
      for (Button b : new Button[]{this.btnOrders, this.btnMenu, this.btnReport, this.btnSettings}) {
         b.getStyleClass().remove("active");
      }

      active.getStyleClass().add("active");
   }

   private Node loadFxml(String path, List<String> errors) {
      try {
         URL url = this.getClass().getResource(path);
         if (url == null) {
            errors.add(path + " — không tìm thấy file trong JAR / classpath.");
            return null;
         } else {
            FXMLLoader loader = new FXMLLoader(url);
            return (Node)loader.load();
         }
      } catch (Exception var7) {
         var7.printStackTrace();
         String detail = path + " — ";

         for (Throwable t = var7; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m != null && !m.isBlank()) {
               detail = detail + m;
               break;
            }
         }

         if (detail.endsWith(" — ")) {
            detail = detail + var7.toString();
         }

         errors.add(detail);
         return null;
      }
   }
}
