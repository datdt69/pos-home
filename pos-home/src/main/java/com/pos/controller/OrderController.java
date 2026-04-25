package com.pos.controller;

import com.pos.model.Category;
import com.pos.model.MenuItem;
import com.pos.model.Order;
import com.pos.model.OrderItem;
import com.pos.repository.CategoryRepository;
import com.pos.repository.MenuRepository;
import com.pos.repository.OrderItemRepository;
import com.pos.repository.OrderRepository;
import com.pos.util.AppSettings;
import com.pos.util.MoneyFormat;
import com.pos.util.PrinterUtil;
import com.pos.util.UiAlerts;
import javafx.beans.InvalidationListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class OrderController {
   @FXML
   private TabPane tabOpenOrders;
   @FXML
   private ListView<OrderItem> listItems;
   @FXML
   private VBox orderEmptyState;
   @FXML
   private Label lblOpenOrdersTitle;
   @FXML
   private Label lblOrderDetail;
   @FXML
   private Label lblDangGoi;
   @FXML
   private Label lblTotal;
   @FXML
   private TextField searchField;
   @FXML
   private FlowPane categoryChips;
   @FXML
   private GridPane menuGrid;
   @FXML
   private Button btnNewOrder;
   @FXML
   private Button btnPay;
   @FXML
   private Button btnCancel;
   @FXML
   private ScrollPane menuScroll;
   private final OrderRepository orderRepository = new OrderRepository();
   private final OrderItemRepository orderItemRepository = new OrderItemRepository();
   private final MenuRepository menuRepository = new MenuRepository();
   private final CategoryRepository categoryRepository = new CategoryRepository();
   private Integer selectedCategoryId;
   private Integer activeOrderId;
   private final ObservableList<OrderItem> lineItems = FXCollections.observableArrayList();
   private List<Category> categories = List.of();
   private final Map<Integer, String> categoryNameById = new HashMap<>();
   private boolean inEnsureBlock;

   @FXML
   private void initialize() {
      this.listItems.setItems(this.lineItems);
      this.listItems.setFixedCellSize(64.0);
      this.listItems.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
      this.listItems.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");
      this.listItems.setCellFactory(lv -> new OrderController.OrderLineListCell());
      if (this.menuScroll != null) {
         VBox.setVgrow(this.menuScroll, Priority.ALWAYS);
      }

      this.lineItems.addListener((InvalidationListener) obs -> this.syncEmptyState());
      this.syncEmptyState();
      this.menuGrid.getColumnConstraints().clear();

      for (int i = 0; i < 4; i++) {
         ColumnConstraints cc = new ColumnConstraints();
         cc.setPercentWidth(25.0);
         cc.setMinWidth(90.0);
         cc.setHgrow(Priority.ALWAYS);
         this.menuGrid.getColumnConstraints().add(cc);
      }

      try {
         this.loadCategories();
         this.rebuildCategoryNameMap();
         this.buildCategoryChips();
         this.tabOpenOrders.setTabMinWidth(140.0);
         this.tabOpenOrders.setTabMaxWidth(180.0);
         this.refreshOrderTabs(null);
         this.buildMenuGrid();
      } catch (Exception var3) {
         UiAlerts.error("Lỗi", var3.getMessage());
      }

      this.tabOpenOrders.addEventFilter(MouseEvent.MOUSE_RELEASED, ex -> {
         if (ex.getButton() == MouseButton.PRIMARY) {
            Platform.runLater(this::applyFromSelectedTab);
         }
      });
   }

   private void syncEmptyState() {
      boolean empty = this.lineItems.isEmpty();
      if (this.orderEmptyState != null) {
         this.orderEmptyState.setVisible(empty);
         this.orderEmptyState.setManaged(empty);
      }
   }

   private boolean orderIsPaidOrMissing() {
      if (this.activeOrderId == null) {
         return true;
      } else {
         try {
            return this.orderRepository.findById(this.activeOrderId).map(o -> "PAID".equals(o.getStatus())).orElse(true);
         } catch (Exception var2) {
            return true;
         }
      }
   }

   @FXML
   private void onNewOrder() {
      try {
         int id = this.orderRepository.createOpenOrder();
         this.refreshOrderTabs(id);
      } catch (Exception var2) {
         UiAlerts.error("Lỗi", "Không tạo đơn: " + var2.getMessage());
      }
   }

   @FXML
   private void onPay() {
      if (this.activeOrderId != null) {
         if (this.orderIsPaidOrMissing()) {
            UiAlerts.warn("Thanh toán", "Không thể thanh toán đơn này.");
         } else if (this.lineItems.isEmpty()) {
            UiAlerts.warn("Thanh toán", "Chưa có món trong đơn.");
         } else if (UiAlerts.confirm("Xác nhận", "Bạn chắc chắn thanh toán đơn này?")) {
            int closeId = this.activeOrderId;

            try {
               this.orderRepository.markPaid(closeId);
               AppSettings st = new AppSettings();

               try {
                  st.load();
               } catch (Exception se) {
                  // bỏ qua cấu hình, vẫn thanh toán
               }

               if (UiAlerts.confirmPrintReceipt()) {
                  if (!st.isPrinterPortConfigured()) {
                     UiAlerts.warn("In hóa đơn", "Chưa cấu hình cổng máy in. Vào mục Cài đặt → chọn COM và lưu.");
                  } else {
                     while (true) {
                        try {
                           Order paid = this.orderRepository.findById(closeId).orElseThrow();
                           List<OrderItem> lines = this.orderItemRepository.findByOrderIdWithNames(closeId);
                           PrinterUtil.printReceipt(
                              paid,
                              lines,
                              st.getPrinterPort(),
                              st.getMaxLineChars(),
                              st.getShopName(),
                              st.getShopAddress(),
                              st.getShopPhone()
                           );
                           break;
                        } catch (Exception ex) {
                           if (!UiAlerts.confirmRetryPrint()) {
                              break;
                           }
                        }
                     }
                  }
               }
               this.refreshOrderTabs(null);
            } catch (Exception var3) {
               UiAlerts.error("Lỗi", var3.getMessage());
            }
         }
      }
   }

   @FXML
   private void onCancelOrder() {
      if (this.activeOrderId != null) {
         if (this.isLoneEmptyOrder(this.activeOrderId)) {
            UiAlerts.warn("Hủy đơn", "Cần giữ đơn trống để bắt đầu order. Tạo đơn mới nếu muốn tách bàn khác.");
         } else {
            try {
               if (this.orderItemRepository.countByOrderId(this.activeOrderId) > 0
                  && !UiAlerts.confirm("Hủy đơn", "Bạn chắc chắn hủy đơn này? Dữ liệu sẽ bị xóa.")) {
                  return;
               }
            } catch (Exception var2) {
               UiAlerts.error("Lỗi", var2.getMessage());
               return;
            }

            this.deleteOrderByIdAndRefresh(this.activeOrderId);
         }
      }
   }

   private void deleteOrderByIdAndRefresh(int orderId) {
      try {
         this.orderRepository.deleteOrderCascade(orderId);
         this.refreshOrderTabs(null);
      } catch (Exception var3) {
         UiAlerts.error("Lỗi", var3.getMessage());
      }
   }

   private boolean isLoneEmptyOrder(int orderId) {
      try {
         List<Order> op = this.orderRepository.findOpenOrders();
         if (op.size() != 1) {
            return false;
         } else {
            Order o = op.get(0);
            if (o.getId() != orderId) {
               return false;
            } else {
               return this.orderItemRepository.countByOrderId(orderId) > 0 ? false : o.getTotal() <= 1.0E-4;
            }
         }
      } catch (Exception var4) {
         return false;
      }
   }

   @FXML
   private void onSearchChange() {
      this.buildMenuGrid();
   }

   private void onQtyDelta(OrderItem oi, int d) {
      if (!this.orderIsPaidOrMissing()) {
         int q = oi.getQuantity() + d;
         if (q < 1) {
            this.onDeleteLine(oi);
         } else {
            try {
               this.orderItemRepository.updateQuantity(oi.getId(), q);
               this.orderRepository.recalculateTotal(oi.getOrderId());
               this.loadOrderDetails(oi.getOrderId());
               this.refreshOrderTabs(null);
            } catch (Exception var5) {
               UiAlerts.error("Lỗi", var5.getMessage());
            }
         }
      }
   }

   private void onDeleteLine(OrderItem oi) {
      if (!this.orderIsPaidOrMissing()) {
         if (UiAlerts.confirm("Xóa món", "Bỏ món này khỏi đơn?")) {
            int oid = oi.getOrderId();

            try {
               this.orderItemRepository.deleteById(oi.getId());
               this.orderRepository.recalculateTotal(oid);
               this.loadOrderDetails(oid);
               this.refreshOrderTabs(null);
            } catch (Exception var4) {
               UiAlerts.error("Lỗi", var4.getMessage());
            }
         }
      }
   }

   private void loadCategories() throws Exception {
      this.categories = this.categoryRepository.findAll();
   }

   private void rebuildCategoryNameMap() {
      this.categoryNameById.clear();

      for (Category c : this.categories) {
         this.categoryNameById.put(c.getId(), c.getName());
      }
   }

   private void buildCategoryChips() {
      this.categoryChips.getChildren().clear();
      ToggleGroup group = new ToggleGroup();
      ToggleButton all = new ToggleButton("Tất cả");
      all.setToggleGroup(group);
      all.setSelected(true);
      all.getStyleClass().addAll(new String[]{"chip", "category-pill"});
      all.selectedProperty().addListener((o, a, s) -> {
         if (Boolean.TRUE.equals(s)) {
            all.getStyleClass().add("chip-on");
            this.selectedCategoryId = null;
            this.buildMenuGrid();
         } else {
            all.getStyleClass().remove("chip-on");
         }
      });
      if (all.isSelected()) {
         all.getStyleClass().add("chip-on");
      }

      this.categoryChips.getChildren().add(all);

      for (Category c : this.categories) {
         ToggleButton tb = new ToggleButton(c.getName());
         tb.setUserData(c.getId());
         tb.setToggleGroup(group);
         tb.getStyleClass().addAll(new String[]{"chip", "category-pill"});
         int cid = c.getId();
         tb.selectedProperty().addListener((o, a, s) -> {
            if (Boolean.TRUE.equals(s)) {
               for (Toggle x : group.getToggles()) {
                  if (x instanceof ToggleButton) {
                     ToggleButton tt = (ToggleButton) x;
                     tt.getStyleClass().remove("chip-on");
                  }
               }

               tb.getStyleClass().add("chip-on");
               this.selectedCategoryId = cid;
               this.buildMenuGrid();
            }
         });
         this.categoryChips.getChildren().add(tb);
      }
   }

   private void refreshOrderTabs(Integer preferOrderId) {
      int previously = preferOrderId != null ? preferOrderId : (this.activeOrderId != null ? this.activeOrderId : -1);
      SingleSelectionModel<Tab> sel = this.tabOpenOrders.getSelectionModel();

      try {
         List<Order> open = this.orderRepository.findOpenOrders();
         this.tabOpenOrders.getTabs().clear();

         for (Order o : open) {
            int cnt = this.orderItemRepository.countByOrderId(o.getId());
            Tab t = new Tab();
            t.setText(this.formatTabText(o.getOrderNumber(), o.getId(), cnt, o.getTotal()));
            t.getStyleClass().add("order-tab");
            t.setUserData(o.getId());
            t.setClosable(true);
            t.setOnCloseRequest(ev -> {
               ev.consume();
               int oid = (Integer)t.getUserData();
               if (this.isLoneEmptyOrder(oid)) {
                  UiAlerts.warn("Đóng đơn", "Cần giữ đơn trống để bắt đầu order. Tạo đơn mới nếu cần nhiều bàn.");
               } else {
                  try {
                     if (this.orderItemRepository.countByOrderId(oid) > 0 && !UiAlerts.confirm("Đóng đơn", "Hủy đơn này? Dữ liệu sẽ bị xóa.")) {
                        return;
                     }

                     this.orderRepository.deleteOrderCascade(oid);
                     this.refreshOrderTabs(null);
                  } catch (Exception var5) {
                     UiAlerts.error("Lỗi", var5.getMessage());
                  }
               }
            });
            t.selectedProperty().addListener((p, w, n) -> {
               if (Boolean.TRUE.equals(n)) {
                  this.onOrderTabSelected(t);
               }
            });
            this.tabOpenOrders.getTabs().add(t);
         }

         this.applyTabClosableRules();
         if (this.tabOpenOrders.getTabs().isEmpty() && !this.inEnsureBlock) {
            this.inEnsureBlock = true;

            try {
               int id = this.orderRepository.createOpenOrder();
               this.inEnsureBlock = false;
               this.refreshOrderTabs(id);
            } catch (Exception var9) {
               this.inEnsureBlock = false;
               UiAlerts.error("Lỗi tạo đơn", var9.getMessage());
            }

            return;
         }

         if (this.tabOpenOrders.getTabs().isEmpty()) {
            this.activeOrderId = null;
            this.lineItems.clear();
            this.lblTotal.setText("0 đ");
            this.updateActionButtons();
            this.updateOrderHeaderLabels();
         } else {
            Tab toSelect = null;

            for (Tab t : this.tabOpenOrders.getTabs()) {
               if (t.getUserData() != null && (Integer)t.getUserData() == previously) {
                  toSelect = t;
                  break;
               }
            }

            if (toSelect == null) {
               toSelect = (Tab)this.tabOpenOrders.getTabs().get(0);
            }

            Tab chosen = toSelect;
            sel.select(chosen);
            Platform.runLater(() -> this.onOrderTabSelected(chosen));
         }
      } catch (Exception var10) {
         UiAlerts.error("Lỗi tải đơn", var10.getMessage());
      }
   }

   private void applyTabClosableRules() {
      try {
         List<Order> op = this.orderRepository.findOpenOrders();
         if (op.size() != 1) {
            for (Tab t : this.tabOpenOrders.getTabs()) {
               t.setClosable(true);
            }

            return;
         }

         Order only = op.get(0);
         int cnt = this.orderItemRepository.countByOrderId(only.getId());
         boolean loneEmpty = cnt == 0 && only.getTotal() <= 1.0E-4;

         for (Tab t : this.tabOpenOrders.getTabs()) {
            Integer id = (Integer)t.getUserData();
            boolean isThatTab = id != null && id == only.getId();
            t.setClosable(!loneEmpty || !isThatTab);
         }
      } catch (Exception var9) {
         for (Tab t : this.tabOpenOrders.getTabs()) {
            t.setClosable(true);
         }
      }
   }

   private String formatTabText(String orderNum, int orderId, int lineCount, double total) {
      String n = orderNum != null && !orderNum.isBlank() ? orderNum : "Đơn " + orderId;
      return n + "  ·  " + lineCount + " món  ·  " + MoneyFormat.vnd(total);
   }

   private void updateOrderHeaderLabels() {
      if (this.lblOpenOrdersTitle != null) {
         this.lblOpenOrdersTitle.setText("Đơn hàng hiện tại · " + this.tabOpenOrders.getTabs().size() + " đơn mở");
      }

      if (this.lblOrderDetail != null) {
         if (this.activeOrderId == null) {
            this.lblOrderDetail.setText("Chưa chọn đơn");
            if (this.lblDangGoi != null) {
               this.lblDangGoi.setVisible(false);
               this.lblDangGoi.setManaged(false);
            }
         } else {
            try {
               Optional<Order> o = this.orderRepository.findById(this.activeOrderId);
               if (o.isEmpty()) {
                  this.lblOrderDetail.setText("—");
                  return;
               }

               Order ord = o.get();
               String num = ord.getOrderNumber();
               if (num == null || num.isBlank()) {
                  num = "Đơn " + ord.getId();
               }

               this.lblOrderDetail.setText(num);
               if (this.lblDangGoi != null) {
                  boolean show = "OPEN".equals(ord.getStatus());
                  this.lblDangGoi.setVisible(show);
                  this.lblDangGoi.setManaged(show);
               }
            } catch (Exception var5) {
            }
         }
      }
   }

   private void loadOrderDetails(int orderId) {
      try {
         Optional<Order> order = this.orderRepository.findById(orderId);
         if (order.isEmpty()) {
            this.lineItems.clear();
            this.lblTotal.setText("0 đ");
         } else if ("PAID".equals(order.get().getStatus())) {
            this.lineItems.clear();
            this.lblTotal.setText(MoneyFormat.vnd(order.get().getTotal()));
         } else {
            List<OrderItem> lines = this.orderItemRepository.findByOrderIdWithNames(orderId);
            this.lineItems.setAll(lines);
            this.lblTotal.setText(MoneyFormat.vnd(order.get().getTotal()));
         }
      } catch (Exception var7) {
         UiAlerts.error("Lỗi", var7.getMessage());
      } finally {
         this.updateActionButtons();
         this.updateOrderHeaderLabels();
      }
   }

   private void applyFromSelectedTab() {
      Tab t = (Tab)this.tabOpenOrders.getSelectionModel().getSelectedItem();
      if (t == null) {
         this.clearOrderViewState();
      } else {
         this.onOrderTabSelected(t);
      }
   }

   private void clearOrderViewState() {
      this.activeOrderId = null;
      this.lineItems.clear();
      this.lblTotal.setText("0 đ");
      this.updateActionButtons();
      this.updateOrderHeaderLabels();
   }

   private void onOrderTabSelected(Tab t) {
      if (t == null) {
         this.clearOrderViewState();
      } else {
         Integer id = (Integer)t.getUserData();
         this.activeOrderId = id;
         if (id == null) {
            this.lineItems.clear();
            this.lblTotal.setText("0 đ");
            this.updateActionButtons();
            this.updateOrderHeaderLabels();
         } else {
            this.loadOrderDetails(id);
         }
      }
   }

   private void updateActionButtons() {
      boolean on = this.activeOrderId != null && !this.orderIsPaidOrMissing();
      boolean loneEmpty = this.activeOrderId != null && this.isLoneEmptyOrder(this.activeOrderId);
      boolean canPay = on && !this.lineItems.isEmpty();
      if (canPay && this.activeOrderId != null) {
         try {
            double tot = this.orderRepository.findById(this.activeOrderId).map(Order::getTotal).orElse(0.0);
            if (tot <= 1.0E-4) {
               canPay = false;
            }
         } catch (Exception var6) {
            canPay = false;
         }
      }

      this.btnPay.setDisable(!canPay);
      this.btnCancel.setDisable(!on || loneEmpty);
   }

   private void buildMenuGrid() {
      this.menuGrid.getChildren().clear();
      String q = this.searchField.getText() == null ? "" : this.searchField.getText().trim();

      try {
         List<MenuItem> items;
         if (q.isEmpty()) {
            items = this.menuRepository.searchByName(null, this.selectedCategoryId);
         } else {
            items = this.menuRepository.searchByName(q, this.selectedCategoryId);
         }

         int col = 0;
         int row = 0;

         for (MenuItem m : items) {
            Node card = this.createMenuCard(m);
            this.menuGrid.add(card, col, row);
            if (++col >= 4) {
               col = 0;
               row++;
            }
         }
      } catch (Exception var8) {
         UiAlerts.error("Lỗi thực đơn", var8.getMessage());
      }
   }

   private Node createMenuCard(MenuItem m) {
      VBox box = new VBox(6.0);
      box.getStyleClass().add("menu-card");
      box.setMaxWidth(Double.MAX_VALUE);
      GridPane.setFillWidth(box, true);
      box.setMinHeight(100.0);
      Region thumb = new Region();
      thumb.setMinHeight(72.0);
      thumb.setPrefHeight(72.0);
      thumb.setMaxWidth(Double.MAX_VALUE);
      thumb.getStyleClass().add("menu-card-thumb");
      String cat = "—";
      if (m.getCategoryId() != null) {
         cat = this.categoryNameById.getOrDefault(m.getCategoryId(), "—");
      }

      Label name = new Label(m.getName());
      name.getStyleClass().add("menu-card-name");
      name.setWrapText(true);
      Label price = new Label(MoneyFormat.vnd(m.getPrice()));
      price.getStyleClass().add("menu-card-price");
      Label catL = new Label(cat);
      catL.getStyleClass().add("menu-card-cat");
      catL.setVisible(false);
      catL.setManaged(false);
      box.setOnMouseEntered(e -> {
         box.setScaleX(1.02);
         box.setScaleY(1.02);
      });
      box.setOnMouseExited(e -> {
         box.setScaleX(1.0);
         box.setScaleY(1.0);
      });
      if (!m.isAvailable()) {
         box.getStyleClass().add("unavailable");
         Label het = new Label("Hết món");
         het.getStyleClass().add("menu-card-het");
         box.getChildren().addAll(new Node[]{thumb, name, price, catL, het});
      } else {
         box.getChildren().addAll(new Node[]{thumb, name, price, catL});
         box.setOnMouseClicked(e -> this.onPickMenu(m, box));
      }

      return box;
   }

   private void onPickMenu(MenuItem m, VBox box) {
      if (this.activeOrderId == null) {
         UiAlerts.warn("Chọn đơn", "Hãy chọn hoặc tạo đơn trước khi thêm món.");
      } else if (!this.orderIsPaidOrMissing()) {
         if (m.isAvailable()) {
            int oid = this.activeOrderId;

            try {
               this.orderItemRepository.addOrIncrement(oid, m.getId(), m.getPrice());
               this.orderRepository.recalculateTotal(oid);
               this.loadOrderDetails(oid);
               this.refreshOrderTabs(null);
               box.getStyleClass().add("menu-card-picked");
               PauseTransition p = new PauseTransition(Duration.millis(180.0));
               p.setOnFinished(ex -> box.getStyleClass().remove("menu-card-picked"));
               p.play();
            } catch (Exception var5) {
               UiAlerts.error("Lỗi", var5.getMessage());
            }
         }
      }
   }

   private class OrderLineListCell extends ListCell<OrderItem> {
      private final HBox row = new HBox(8.0);
      private final Label name = new Label();
      private final Region sp = new Region();
      private final Button bMinus = new Button("−");
      private final Label qtyL = new Label();
      private final Button bPlus = new Button("+");
      private final Label priceL = new Label();
      private final Button bDel = new Button("×");

      OrderLineListCell() {
         HBox.setHgrow(this.sp, Priority.ALWAYS);
         this.name.setMaxWidth(Double.MAX_VALUE);
         HBox.setHgrow(this.name, Priority.ALWAYS);
         this.name.getStyleClass().add("order-line-name");
         this.qtyL.getStyleClass().add("order-line-qty-label");
         this.priceL.getStyleClass().add("order-line-price");
         this.priceL.setAlignment(Pos.CENTER_RIGHT);
         this.bMinus.getStyleClass().addAll(new String[]{"order-qty-btn"});
         this.bPlus.getStyleClass().addAll(new String[]{"order-qty-btn"});
         this.bDel.getStyleClass().addAll(new String[]{"order-line-del"});
         this.bDel.setMinSize(32.0, 32.0);
         this.bDel.setPrefSize(32.0, 32.0);
         this.bMinus.setOnAction(e -> {
            if (this.getItem() != null) {
               OrderController.this.onQtyDelta((OrderItem)this.getItem(), -1);
            }
         });
         this.bPlus.setOnAction(e -> {
            if (this.getItem() != null) {
               OrderController.this.onQtyDelta((OrderItem)this.getItem(), 1);
            }
         });
         this.bDel.setOnAction(e -> {
            if (this.getItem() != null) {
               OrderController.this.onDeleteLine((OrderItem)this.getItem());
            }
         });
         HBox qtyBox = new HBox(2.0, new Node[]{this.bMinus, this.qtyL, this.bPlus});
         qtyBox.setAlignment(Pos.CENTER);
         qtyBox.getStyleClass().add("order-qty-box");
         this.row.getChildren().addAll(new Node[]{this.name, this.sp, qtyBox, this.priceL, this.bDel});
         this.row.setAlignment(Pos.CENTER_LEFT);
         this.row.getStyleClass().add("order-line-card");
         this.row.setPadding(new Insets(0.0, 0.0, 0.0, 4.0));
      }

      protected void updateItem(OrderItem item, boolean empty) {
         super.updateItem(item, empty);
         if (!empty && item != null) {
            this.name.setText(item.getMenuName() != null ? item.getMenuName() : "—");
            this.qtyL.setText(String.valueOf(item.getQuantity()));
            this.priceL.setText(MoneyFormat.vnd(item.getLineTotal()));
            this.setGraphic(this.row);
         } else {
            this.setGraphic(null);
         }
      }
   }
}
