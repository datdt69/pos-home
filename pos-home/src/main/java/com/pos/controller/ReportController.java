package com.pos.controller;

import com.pos.model.Order;
import com.pos.model.OrderItem;
import com.pos.repository.OrderItemRepository;
import com.pos.repository.OrderRepository;
import com.pos.repository.ReportRepository;
import com.pos.util.MoneyFormat;
import com.pos.util.UiAlerts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class ReportController {

   private static final int PAGE_SIZE = 20;
   private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
   private static final DateTimeFormatter DFILE = DateTimeFormatter.ofPattern("ddMMyyyy_HHmmss");
   private static final DateTimeFormatter PICK = DateTimeFormatter.ofPattern("dd/MM/yyyy");
   @FXML
   private ToggleButton tDay;
   @FXML
   private ToggleButton tWeek;
   @FXML
   private ToggleButton tMonth;
   @FXML
   private DatePicker dpRef;
   @FXML
   private Label lblPickHint;
   @FXML
   private ComboBox<String> comboMonth;
   @FXML
   private Label lblRangeHint;
   @FXML
   private Button btnView;
   @FXML
   private Button btnExport;
   @FXML
   private Label lblStatRevenue;
   @FXML
   private Label lblRevenueTrend;
   @FXML
   private Label lblStatOrders;
   @FXML
   private Label lblStatAvg;
   @FXML
   private Label lblStatBest;
   @FXML
   private TableView<ReportOrderRow> tableOrders;
   @FXML
   private TableColumn<ReportOrderRow, Integer> colStt;
   @FXML
   private TableColumn<ReportOrderRow, String> colONum;
   @FXML
   private TableColumn<ReportOrderRow, String> colOTime;
   @FXML
   private TableColumn<ReportOrderRow, String> colOTot;
   @FXML
   private Label lblPage;
   @FXML
   private Label lblOrderTotalSum;
   @FXML
   private TableView<ProductRow> tableProducts;
   @FXML
   private TableColumn<ProductRow, Integer> colPStt;
   @FXML
   private TableColumn<ProductRow, String> colPName;
   @FXML
   private TableColumn<ProductRow, Integer> colPQty;
   @FXML
   private TableColumn<ProductRow, String> colPRev;
   @FXML
   private Button btnPrev;
   @FXML
   private Button btnNext;

   private final OrderRepository orderRepository = new OrderRepository();
   private final ReportRepository reportRepository = new ReportRepository();
   private final OrderItemRepository orderItemRepository = new OrderItemRepository();
   private final ToggleGroup rangeGroup = new ToggleGroup();
   private List<Order> allPaidThisRange = new ArrayList<>();
   private List<ReportRepository.ProductSalesRow> allProductRows = new ArrayList<>();
   private int currentPage = 0;
   private double lastRevenue;
   private int lastOrderCount;
   private LocalDateTime lastFrom;
   private LocalDateTime lastTo;
   private String lastFilterLabel = "";

   @FXML
   private void initialize() {
      this.tDay.setToggleGroup(this.rangeGroup);
      this.tWeek.setToggleGroup(this.rangeGroup);
      this.tMonth.setToggleGroup(this.rangeGroup);
      this.tDay.setSelected(true);
      for (ToggleButton tb : new ToggleButton[]{this.tDay, this.tWeek, this.tMonth}) {
         if (!tb.getStyleClass().contains("chip")) {
            tb.getStyleClass().add("chip");
         }
      }
      this.applyChipSelectionStyles();
      this.rangeGroup.selectedToggleProperty().addListener((o, a, t) -> this.applyChipSelectionStyles());
      this.dpRef.setEditable(false);
      this.dpRef.setValue(this.vnToday());
      this.setupDatePickerWindow();
      this.comboMonth.getItems().setAll("Tháng này", "Tháng trước", "Hai tháng trước");
      this.comboMonth.getSelectionModel().select(0);
      this.applyFilterModeUi();
      this.dpRef.valueProperty().addListener((o, a, b) -> this.loadReport());
      this.comboMonth.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> this.loadReport());
      this.colStt.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getStt()));
      this.colStt.setStyle("-fx-alignment: CENTER");
      this.colONum.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(((ReportOrderRow) p.getValue()).getNumber()));
      this.colOTime.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(((ReportOrderRow) p.getValue()).getTime()));
      this.colOTot.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(((ReportOrderRow) p.getValue()).getTotal()));
      this.colOTot.setStyle("-fx-alignment: CENTER-RIGHT");
      this.tableOrders.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
      this.tableOrders.setRowFactory(
         tv -> {
            TableRow<ReportOrderRow> row = new TableRow<>();
            row.setOnMouseClicked(
               ev -> {
                  if (ev.getClickCount() == 1 && !row.isEmpty() && row.getItem() != null) {
                     this.openOrderDetail(row.getItem().orderId);
                  }
               }
            );
            return row;
         }
      );
      this.colPStt.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().stt));
      this.colPStt.setStyle("-fx-alignment: CENTER");
      this.colPName.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().name));
      this.colPQty.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().quantity));
      this.colPQty.setStyle("-fx-alignment: CENTER");
      this.colPRev.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().revenue));
      this.colPRev.setStyle("-fx-alignment: CENTER-RIGHT");
      this.tableProducts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
      Platform.runLater(this::loadReport);
   }

   private void applyChipSelectionStyles() {
      for (ToggleButton tbx : new ToggleButton[]{this.tDay, this.tWeek, this.tMonth}) {
         if (Boolean.TRUE.equals(tbx.isSelected())) {
            if (!tbx.getStyleClass().contains("chip-on")) {
               tbx.getStyleClass().add("chip-on");
            }
         } else {
            tbx.getStyleClass().remove("chip-on");
         }
      }
   }

   private LocalDate vnToday() {
      return LocalDate.now(OrderRepository.VIETNAM);
   }

   private LocalDate oldestDataDay() {
      return this.vnToday().minusMonths(3L);
   }

   private LocalDate clampToRetention(LocalDate d) {
      LocalDate minD = this.oldestDataDay();
      LocalDate maxD = this.vnToday();
      if (d.isBefore(minD)) {
         return minD;
      }
      return d.isAfter(maxD) ? maxD : d;
   }

   private void setupDatePickerWindow() {
      this.dpRef
         .setDayCellFactory(
            p -> {
               return new DateCell() {
                  @Override
                  public void updateItem(LocalDate item, boolean empty) {
                     super.updateItem(item, empty);
                     if (empty || item == null) {
                        this.setDisable(true);
                     } else {
                        LocalDate minD = LocalDate.now(OrderRepository.VIETNAM).minusMonths(3L);
                        LocalDate maxD = LocalDate.now(OrderRepository.VIETNAM);
                        this.setDisable(item.isBefore(minD) || item.isAfter(maxD));
                     }
                  }
               };
            }
         );
   }

   private void applyFilterModeUi() {
      Toggle sel = this.rangeGroup.getSelectedToggle();
      boolean monthMode = sel == this.tMonth;
      this.dpRef.setVisible(!monthMode);
      this.dpRef.setManaged(!monthMode);
      this.lblPickHint.setVisible(!monthMode);
      this.lblPickHint.setManaged(!monthMode);
      this.comboMonth.setVisible(monthMode);
      this.comboMonth.setManaged(monthMode);
      if (sel == this.tDay) {
         this.lblPickHint.setText("Chọn ngày (tối đa 3 tháng dữ liệu gần nhất)");
         this.lblRangeHint
            .setText("Một ngày dương lịch; chỉ chọn từ mốc còn dữ liệu (3 tháng gần nhất, đồng bộ khi dọn DB). So sánh với ngày hôm trước.");
      } else if (sel == this.tWeek) {
         this.lblPickHint.setText("Chọn 1 ngày trong tuần (T2–CN, theo lịch ISO)");
         this.lblRangeHint
            .setText("Tuần dương lịch thứ Hai–Chủ nhật chứa ngày bạn chọn; không cần chọn năm. So sánh với tuần trước.");
      } else {
         this.lblRangeHint
            .setText("Tháng dương lịch: Tháng này, tháng trước, hoặc hai tháng trước (tối đa 3 tháng, không cần năm). So sánh với tháng trước tương ứng.");
      }
   }

   @FXML
   private void onRangeChange() {
      this.applyFilterModeUi();
      this.loadReport();
   }

   @FXML
   private void onViewReport() {
      this.loadReport();
   }

   @FXML
   private void onPagePrev() {
      if (this.currentPage > 0) {
         this.currentPage--;
         this.showPage();
      }
   }

   @FXML
   private void onPageNext() {
      int maxP = (int) Math.ceil((double) this.allPaidThisRange.size() / 20.0) - 1;
      if (this.currentPage < maxP) {
         this.currentPage++;
         this.showPage();
      }
   }

   private void showPage() {
      int total = this.allPaidThisRange.size();
      int pages = total == 0 ? 1 : (int) Math.ceil((double) total / 20.0);
      this.currentPage = Math.max(0, Math.min(this.currentPage, pages - 1));
      int fromIdx = this.currentPage * 20;
      int toIdx = Math.min(fromIdx + 20, total);
      List<ReportOrderRow> sub = new ArrayList<>();
      for (int i = fromIdx; i < toIdx; i++) {
         Order o = this.allPaidThisRange.get(i);
         int stt = i + 1;
         String num = o.getOrderNumber() == null ? "—" : o.getOrderNumber();
         String paidStr = o.getPaidAt() == null ? "—" : o.getPaidAt().format(ReportController.DT);
         sub.add(new ReportOrderRow(o.getId(), stt, num, paidStr, MoneyFormat.vnd(o.getTotal())));
      }
      this.tableOrders.setItems(FXCollections.observableArrayList(sub));
      this.lblPage.setText("Trang " + (this.currentPage + 1) + " / " + pages);
      this.btnPrev.setDisable(this.currentPage <= 0);
      this.btnNext.setDisable(this.currentPage >= pages - 1);
   }

   private void openOrderDetail(int orderId) {
      try {
         List<OrderItem> items = this.orderItemRepository.findByOrderIdWithNames(orderId);
         VBox v = new VBox(6);
         v.setPadding(new Insets(12));
         for (OrderItem oi : items) {
            v.getChildren()
               .add(
                  new Label(oi.getMenuName() + " × " + oi.getQuantity() + " — " + MoneyFormat.vnd(oi.getLineTotal()))
               );
         }
         if (items.isEmpty()) {
            v.getChildren().add(new Label("Không có dòng món."));
         }
         Dialog<Void> d = new Dialog<>();
         d.setTitle("Chi tiết đơn hàng");
         d.getDialogPane().setContent(v);
         d.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
         d.showAndWait();
      } catch (Exception e) {
         UiAlerts.error("Chi tiết đơn", e.getMessage());
      }
   }

   private void loadReport() {
      try {
         LocalDateTime from;
         LocalDateTime to;
         LocalDateTime prevFrom;
         LocalDateTime prevTo;
         Toggle sel = this.rangeGroup.getSelectedToggle();
         LocalDate today = this.vnToday();
         LocalDate oldest = this.oldestDataDay();
         if (sel == this.tMonth) {
            int idx = this.comboMonth.getSelectionModel().getSelectedIndex();
            if (idx < 0) {
               idx = 0;
            }
            YearMonth ym = YearMonth.from(today).minusMonths(idx);
            LocalDate fromD = ym.atDay(1);
            if (fromD.isBefore(oldest)) {
               fromD = oldest;
            }
            LocalDate toEx = idx == 0 ? today.plusDays(1L) : ym.plusMonths(1L).atDay(1);
            from = fromD.atStartOfDay();
            to = toEx.atStartOfDay();
            YearMonth prevYm = ym.minusMonths(1L);
            LocalDate prevFromD = prevYm.atDay(1);
            if (prevFromD.isBefore(oldest)) {
               prevFromD = oldest;
            }
            LocalDate prevToEx = ym.atDay(1);
            prevFrom = prevFromD.atStartOfDay();
            prevTo = prevToEx.atStartOfDay();
            if (idx == 0) {
               this.lastFilterLabel = "Tháng này: từ " + ym.atDay(1).format(ReportController.PICK) + " → " + today.format(ReportController.PICK);
            } else if (idx == 1) {
               this.lastFilterLabel = "Tháng trước: " + ym.getMonthValue() + "/" + ym.getYear();
            } else {
               this.lastFilterLabel = "Hai tháng trước: " + ym.getMonthValue() + "/" + ym.getYear();
            }
         } else {
            LocalDate d = this.dpRef.getValue() == null ? today : this.dpRef.getValue();
            d = this.clampToRetention(d);
            if (this.dpRef.getValue() == null || !d.equals(this.dpRef.getValue())) {
               this.dpRef.setValue(d);
            }
            if (sel == this.tDay) {
               from = d.atStartOfDay();
               to = d.plusDays(1L).atStartOfDay();
               LocalDate prevD = d.minusDays(1L);
               if (prevD.isBefore(oldest)) {
                  prevFrom = d.atStartOfDay();
                  prevTo = d.atStartOfDay();
               } else {
                  prevFrom = prevD.atStartOfDay();
                  prevTo = d.atStartOfDay();
               }
               this.lastFilterLabel = "Ngày " + d.format(ReportController.PICK);
            } else {
               LocalDate wStart = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
               LocalDate wEndEx = wStart.plusWeeks(1L);
               LocalDate startIncl = wStart.isBefore(oldest) ? oldest : wStart;
               LocalDate toExclusive = wEndEx.isAfter(today.plusDays(1L)) ? today.plusDays(1L) : wEndEx;
               from = startIncl.atStartOfDay();
               to = toExclusive.atStartOfDay();
               LocalDate prevWStart = wStart.minusWeeks(1L);
               LocalDate pStart = prevWStart.isBefore(oldest) ? oldest : prevWStart;
               prevFrom = pStart.atStartOfDay();
               prevTo = wStart.atStartOfDay();
               LocalDate lastIncl = toExclusive.minusDays(1L);
               this.lastFilterLabel = "Tuần: " + startIncl.format(ReportController.PICK) + " → " + lastIncl.format(ReportController.PICK) + " (T2–CN)";
            }
         }
         this.lastFrom = from;
         this.lastTo = to;
         double rev = this.orderRepository.sumRevenueBetween(from, to);
         int n = this.orderRepository.countPaidBetween(from, to);
         this.lastRevenue = rev;
         this.lastOrderCount = n;
         this.lblStatRevenue.setText(MoneyFormat.vnd(rev));
         this.lblStatOrders.setText(n + " đơn");
         this.lblStatAvg.setText(
            n == 0 ? "—" : MoneyFormat.vnd(rev / n)
         );
         double prevRev = this.orderRepository.sumRevenueBetween(prevFrom, prevTo);
         this.lblRevenueTrend.getStyleClass().removeAll("stat-trend-up", "stat-trend-down");
         if (prevRev <= 0.0) {
            this.lblRevenueTrend.setText("Không có số liệu kỳ trước để so sánh");
            this.lblRevenueTrend.getStyleClass().add("stat-trend-up");
         } else {
            double pct = (rev - prevRev) / prevRev * 100.0;
            if (pct >= 0.0) {
               this.lblRevenueTrend.setText(String.format("▲ +%.0f%% so với cùng kỳ trước", pct));
               this.lblRevenueTrend.getStyleClass().add("stat-trend-up");
            } else {
               this.lblRevenueTrend.setText(String.format("▼ %.0f%% so với cùng kỳ trước", pct));
               this.lblRevenueTrend.getStyleClass().add("stat-trend-down");
            }
         }
         this.allProductRows = this.reportRepository.allProductSalesBetween(from, to);
         Optional<ReportRepository.ProductSalesRow> best = this.allProductRows.stream().filter(r -> r.quantity() > 0).findFirst();
         this.lblStatBest
            .setText(
               best.isEmpty() ? "—" : (best.get().name() + " (" + best.get().quantity() + " phần, " + MoneyFormat.vnd(best.get().revenue()) + ")")
            );
         this.allPaidThisRange = this.orderRepository.listPaidBetween(from, to);
         this.currentPage = 0;
         double sumAll = 0.0;
         for (Order o : this.allPaidThisRange) {
            sumAll += o.getTotal();
         }
         this.lblOrderTotalSum.setText("Tổng cộng trong kỳ: " + MoneyFormat.vnd(sumAll));
         this.showPage();
         this.refreshProductTable();
      } catch (Exception e) {
         UiAlerts.error("Báo cáo", e.getMessage());
      }
   }

   private void refreshProductTable() {
      ArrayList<ProductRow> list = new ArrayList<>();
      int n = 1;
      for (ReportRepository.ProductSalesRow r : this.allProductRows) {
         if (r.quantity() > 0 || r.revenue() > 0.0) {
            list.add(new ProductRow(n++, r.name(), r.quantity(), MoneyFormat.vnd(r.revenue())));
         }
      }
      this.tableProducts.setItems(FXCollections.observableArrayList(list));
   }

   @FXML
   private void onExport() {
      try {
         StringBuilder b = new StringBuilder();
         b.append("===================================\nBÁO CÁO DOANH THU\n");
         b.append("Kỳ: ").append(this.lastFilterLabel).append("\n");
         b.append("===================================\n");
         b.append("Tổng doanh thu     : ")
            .append(MoneyFormat.vnd(this.lastRevenue));
         b
            .append("\nSố đơn             : ")
            .append(this.lastOrderCount)
            .append(" đơn\n");
         b.append("Trung bình / đơn   : ")
            .append(
               this.lastOrderCount == 0
                  ? "—"
                  : MoneyFormat.vnd(this.lastRevenue / this.lastOrderCount)
            )
            .append("\n\n");
         b.append("SẢN PHẨM ĐÃ BÁN (trong kỳ, theo doanh thu giảm dần):\n");
         int i = 1;
         for (ReportRepository.ProductSalesRow p : this.allProductRows) {
            if (p.quantity() > 0 || p.revenue() > 0.0) {
               b.append(i++)
                  .append(". ")
                  .append(p.name())
                  .append("  —  ")
                  .append(p.quantity())
                  .append(" phần  —  ")
                  .append(MoneyFormat.vnd(p.revenue()))
                  .append("\n");
            }
         }
         b.append("\nDANH SÁCH ĐƠN:\n");
         for (Order o : this.allPaidThisRange) {
            String t = o.getPaidAt() == null ? "—" : o.getPaidAt().toLocalTime().toString().substring(0, 5);
            b.append(o.getOrderNumber() == null ? "—" : o.getOrderNumber())
               .append(" | ")
               .append(t)
               .append(" | ")
               .append(MoneyFormat.vnd(o.getTotal()))
               .append("\n");
         }
         b.append("===================================\n");
         String desk = System.getProperty("user.home") + java.io.File.separator + "Desktop";
         String name = "BaoCao_" + LocalDateTime.now().format(ReportController.DFILE) + ".txt";
         Path path = Path.of(desk, name);
         Files.writeString(path, b.toString(), StandardCharsets.UTF_8);
         UiAlerts.info("Xuất báo cáo", "Đã xuất báo cáo ra Desktop:\n" + name);
      } catch (IOException e) {
         UiAlerts.error("Xuất file", e.getMessage());
      }
   }

   public static class ProductRow {
      public final int stt;
      public final String name;
      public final int quantity;
      public final String revenue;

      public ProductRow(int stt, String name, int quantity, String revenue) {
         this.stt = stt;
         this.name = name;
         this.quantity = quantity;
         this.revenue = revenue;
      }
   }

   public static class ReportOrderRow {
      public final int orderId;
      public final int stt;
      private final String number;
      private final String time;
      private final String total;

      public ReportOrderRow(int orderId, int stt, String number, String time, String total) {
         this.orderId = orderId;
         this.stt = stt;
         this.number = number;
         this.time = time;
         this.total = total;
      }

      public int getStt() {
         return this.stt;
      }

      public String getNumber() {
         return this.number;
      }

      public String getTime() {
         return this.time;
      }

      public String getTotal() {
         return this.total;
      }
   }
}
