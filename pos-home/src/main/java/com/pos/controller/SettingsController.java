package com.pos.controller;

import com.pos.util.AppSettings;
import com.pos.util.PrinterUtil;
import com.pos.util.UiAlerts;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Cài đặt in nhiệt và thông tin cửa hàng, lưu {@link AppSettings#DEFAULT_SETTINGS_PATH} (có thể ghi đè bởi {@code pos.settings.path}).
 */
public class SettingsController {
   @FXML
   private ComboBox<String> cmbPort;
   @FXML
   private ComboBox<String> cmbBaud;
   @FXML
   private ComboBox<String> cmbPaper;
   @FXML
   private TextField txtShopName;
   @FXML
   private TextField txtAddress;
   @FXML
   private TextField txtPhone;
   @FXML
   private Label lblSettingsPath;

   private final AppSettings settings = new AppSettings();

   @FXML
   private void initialize() {
      this.cmbPaper.setItems(
         javafx.collections.FXCollections.observableArrayList("58mm", "80mm")
      );
      this.cmbBaud.setItems(
         javafx.collections.FXCollections.observableArrayList(
            "9600",
            "19200",
            "38400",
            "57600",
            "115200",
            "230400"
         )
      );
      this.lblSettingsPath.setText("File: " + this.settings.getFilePath().toAbsolutePath());
      try {
         this.settings.load();
         this.txtShopName.setText(this.settings.getShopName());
         this.txtAddress.setText(this.settings.getShopAddress());
         this.txtPhone.setText(this.settings.getShopPhone());
         this.cmbPaper.getSelectionModel().select(this.settings.getPaper());
         this.selectBaud(this.settings.getPrinterBaudRate());
      } catch (Exception e) {
         UiAlerts.error("Cài đặt", e.getMessage());
      }
      this.onRefreshPorts();
      String s = this.settings.getPrinterPort();
      if (s != null && !s.isBlank() && this.cmbPort.getItems().contains(s)) {
         this.cmbPort.getSelectionModel().select(s);
      }
   }

   @FXML
   private void onRefreshPorts() {
      java.util.List<String> list = com.pos.util.PrinterUtil.getAvailablePorts();
      this.cmbPort.getItems().setAll(list);
   }

   @FXML
   private void onAutoDetect() {
      String p = com.pos.util.PrinterUtil.detectPrinterPort();
      if (p == null) {
         UiAlerts.warn("Máy in", "Không tìm thấy cổng in phù hợp (gửi lệnh init). Hãy chọn COM thủ công.");
      } else {
         this.onRefreshPorts();
         this.cmbPort.getSelectionModel().select(p);
         this.selectBaud(PrinterUtil.getLastDetectedBaud());
         UiAlerts.info("Máy in", "Gợi ý: " + p + " @ " + PrinterUtil.getLastDetectedBaud() + " baud. Bấm Lưu cài đặt.");
      }
   }

   @FXML
   private void onTestPrint() {
      String p = (String) this.cmbPort.getSelectionModel().getSelectedItem();
      if (p == null || p.isBlank()) {
         UiAlerts.warn("In thử", "Chọn cổng COM trước.");
      } else {
         try {
            com.pos.util.PrinterUtil.printTest(p, this.getSelectedBaud());
            UiAlerts.info("In thử", "Đã gửi bản in thử tới " + p + " @ " + this.getSelectedBaud());
         } catch (Exception e) {
            UiAlerts.error("In thử", e.getMessage());
         }
      }
   }

   @FXML
   private void onSave() {
      try {
         this.settings.setPrinterPort(this.cmbPort.getValue() == null ? "" : this.cmbPort.getValue());
         this.settings.setPrinterBaudRate(this.getSelectedBaud());
         this.settings.setPaper(this.cmbPaper.getValue() == null ? "80mm" : this.cmbPaper.getValue());
         this.settings.setShopName(this.t(this.txtShopName.getText()));
         this.settings.setShopAddress(this.t(this.txtAddress.getText()));
         this.settings.setShopPhone(this.t(this.txtPhone.getText()));
         this.settings.save();
         UiAlerts.info("Cài đặt", "Đã lưu tại " + this.settings.getFilePath().toAbsolutePath());
      } catch (Exception e) {
         UiAlerts.error("Cài đặt", e.getMessage());
      }
   }

   private String t(String s) {
      return s == null ? "" : s.trim();
   }

   private void selectBaud(int baud) {
      String k = String.valueOf(baud);
      if (this.cmbBaud.getItems().contains(k)) {
         this.cmbBaud.getSelectionModel().select(k);
      } else {
         this.cmbBaud.getItems().add(k);
         this.cmbBaud.getSelectionModel().select(k);
      }
   }

   private int getSelectedBaud() {
      String s = this.cmbBaud.getSelectionModel().getSelectedItem();
      if (s == null || s.isBlank()) {
         return 9600;
      }
      try {
         return Integer.parseInt(s.trim());
      } catch (NumberFormatException e) {
         return 9600;
      }
   }
}
