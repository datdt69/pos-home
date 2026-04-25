package com.pos.util;

import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.stage.StageStyle;

public final class UiAlerts {
   private UiAlerts() {
   }

   public static void error(String title, String msg) {
      show(AlertType.ERROR, title, msg);
   }

   public static void info(String title, String msg) {
      show(AlertType.INFORMATION, title, msg);
   }

   public static void warn(String title, String msg) {
      show(AlertType.WARNING, title, msg);
   }

   public static boolean confirm(String title, String msg) {
      ButtonType co = new ButtonType("Có", ButtonData.YES);
      ButtonType khong = new ButtonType("Không", ButtonData.NO);
      Alert a = new Alert(AlertType.CONFIRMATION, msg, new ButtonType[]{co, khong});
      a.setTitle(title);
      a.setHeaderText(null);
      style(a);
      Optional<ButtonType> r = a.showAndWait();
      return r.isPresent() && r.get() == co;
   }

   public static boolean confirmPrintReceipt() {
      ButtonType in = new ButtonType("In hóa đơn", ButtonData.YES);
      ButtonType boQua = new ButtonType("Bỏ qua", ButtonData.NO);
      Alert a = new Alert(AlertType.CONFIRMATION, "In hóa đơn không?", new ButtonType[]{in, boQua});
      a.setTitle("Thanh toán");
      a.setHeaderText(null);
      style(a);
      Optional<ButtonType> r = a.showAndWait();
      return r.isPresent() && r.get() == in;
   }

   public static boolean confirmRetryPrint() {
      ButtonType thuLai = new ButtonType("Thử lại", ButtonData.YES);
      ButtonType boQua = new ButtonType("Bỏ qua", ButtonData.CANCEL_CLOSE);
      Alert a = new Alert(
         AlertType.ERROR, "Không thể in hóa đơn.\nKiểm tra máy in và thử lại.", new ButtonType[]{thuLai, boQua}
      );
      a.setTitle("In hóa đơn");
      a.setHeaderText(null);
      style(a);
      Optional<ButtonType> r = a.showAndWait();
      return r.isPresent() && r.get() == thuLai;
   }

   private static void show(AlertType type, String title, String msg) {
      Alert a = new Alert(type, msg, new ButtonType[]{ButtonType.OK});
      a.setTitle(title);
      a.setHeaderText(null);
      style(a);
      a.showAndWait();
   }

   private static void style(Alert a) {
      a.initStyle(StageStyle.UTILITY);
   }
}
