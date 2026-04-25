package com.pos.controller;

import com.pos.model.Category;
import com.pos.model.MenuItem;
import com.pos.repository.CategoryRepository;
import com.pos.repository.MenuRepository;
import com.pos.util.MoneyFormat;
import com.pos.util.UiAlerts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.StringConverter;

/**
 * Màn hình thực đơn: sidebar danh mục + bảng món, lọc, dialog thêm/sửa. Giữ lại {@link MenuRepository} / {@link CategoryRepository} như cũ.
 */
public class MenuController {

   public static final class CategoryRow {
      public final boolean all;
      public final Category category;
      public final int itemCount;

      public CategoryRow(boolean all, Category category, int itemCount) {
         this.all = all;
         this.category = category;
         this.itemCount = itemCount;
      }

      public String displayName() {
         return all ? "Tất cả" : (category == null ? "—" : category.getName());
      }
   }

   @FXML
   private ListView<CategoryRow> categoryList;
   @FXML
   private TextField searchField;
   @FXML
   private ComboBox<String> statusFilter;
   @FXML
   private Label lblResultCount;
   @FXML
   private TableView<MenuTableRow> menuTable;
   @FXML
   private TableColumn<MenuTableRow, String> colName;
   @FXML
   private TableColumn<MenuTableRow, String> colCat;
   @FXML
   private TableColumn<MenuTableRow, String> colPrice;
   @FXML
   private TableColumn<MenuTableRow, Boolean> colAvail;
   @FXML
   private TableColumn<MenuTableRow, MenuTableRow> colAct;

   private final MenuRepository menuRepository = new MenuRepository();
   private final CategoryRepository categoryRepository = new CategoryRepository();
   private final Map<Integer, String> catNameById = new HashMap<>();
   private final List<MenuItem> allMenuItems = new ArrayList<>();
   private int lastCreatedOrEditedId = -1;

   @FXML
   private void initialize() {
      this.statusFilter.setItems(FXCollections.observableArrayList("Tất cả", "Còn món", "Hết món"));
      this.statusFilter.getSelectionModel().selectFirst();
      this.statusFilter.setOnAction(e -> this.applyFilter());
      this.searchField.textProperty().addListener((a, b, c) -> this.applyFilter());
      this.setupCategoryList();
      this.setupTable();
      this.categoryList.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> this.applyFilter());
      try {
         this.fullReload();
      } catch (Exception e) {
         UiAlerts.error("Lỗi", e.getMessage());
      }
   }

   private void setupCategoryList() {
      this.categoryList.setCellFactory(lv -> new ListCell<CategoryRow>() {
         @Override
         protected void updateItem(CategoryRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
               this.setGraphic(null);
               this.setText(null);
               return;
            }
            Circle dot = new Circle(4);
            dot.setStyle(MenuController.this.dotStyleForRow(item));
            Label nameL = new Label(item.displayName());
            nameL.getStyleClass().add("category-name-label");
            Label countL = new Label("(" + item.itemCount + ")");
            countL.getStyleClass().add("category-count-label");
            Region r = new Region();
            HBox.setHgrow(r, Priority.ALWAYS);
            Button bEdit = new Button("Sửa");
            bEdit.getStyleClass().add("btn-cat-icon");
            bEdit.setOnAction(e -> {
               e.consume();
               MenuController.this.onEditCategory(item);
            });
            bEdit.setVisible(!item.all);
            bEdit.setManaged(!item.all);
            Button bDel = new Button("Xóa");
            bDel.getStyleClass().add("btn-cat-icon-danger");
            bDel.setOnAction(e -> {
               e.consume();
               MenuController.this.onDeleteCategory(item);
            });
            bDel.setVisible(!item.all);
            bDel.setManaged(!item.all);
            HBox line = new HBox(8, dot, nameL, countL, r, bEdit, bDel);
            line.setAlignment(Pos.CENTER_LEFT);
            this.setGraphic(line);
         }
      });
   }

   private String dotStyleForRow(CategoryRow row) {
      if (row.all) {
         return "-fx-fill: #6b7280;";
      }
      String n = row.category.getName() == null ? "" : row.category.getName();
      n = n.toLowerCase(Locale.ROOT);
      if (n.contains("ăn") || n.equals("đồ ăn") || n.contains("an")) {
         return "-fx-fill: #111827;";
      }
      if (n.contains("uống") || n.contains("uong")) {
         return "-fx-fill: #374151;";
      }
      if (n.contains("tráng") || n.contains("miệng") || n.contains("trang")) {
         return "-fx-fill: #4b5563;";
      }
      return "-fx-fill: #1f2937;";
   }

   private void setupTable() {
      this.colName.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getName()));
      this.colCat.setCellValueFactory(
         c -> new ReadOnlyObjectWrapper<>(c.getValue().getCategoryName() == null ? "—" : c.getValue().getCategoryName())
      );
      this.colPrice.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(MoneyFormat.vnd(c.getValue().getPrice())));
      this.colPrice.setStyle("-fx-alignment: CENTER-RIGHT");
      this.colPrice.setCellFactory(
         c -> new TableCell<MenuTableRow, String>() {
            @Override
            protected void updateItem(String s, boolean empty) {
               super.updateItem(s, empty);
               if (empty || s == null) {
                  this.setText(null);
                  this.setStyle("");
               } else {
                  this.setText(s);
                  this.setStyle(
                     "-fx-alignment: CENTER-RIGHT; -fx-text-fill: #111827; -fx-font-weight: 600; -fx-font-size: 16px;"
                  );
               }
            }
         }
      );
      this.colAvail.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue().isAvailable()));
      this.colAvail.setCellFactory(
         col -> new TableCell<MenuTableRow, Boolean>() {
            @Override
            protected void updateItem(Boolean v, boolean empty) {
               super.updateItem(v, empty);
               if (empty) {
                  this.setGraphic(null);
                  return;
               }
               if (this.getTableRow() == null || this.getTableRow().getItem() == null) {
                  this.setGraphic(null);
                  return;
               }
               MenuTableRow row = (MenuTableRow) this.getTableRow().getItem();
               CheckBox sw = new CheckBox();
               sw.getStyleClass().add("menu-avail-switch");
               sw.setSelected(row.isAvailable());
               sw.setOnAction(e -> MenuController.this.onToggleAvailable(row, sw.isSelected(), sw));
               this.setGraphic(sw);
               this.setAlignment(Pos.CENTER);
            }
         }
      );
      this.colAct.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
      this.colAct.setCellFactory(
         col -> new TableCell<MenuTableRow, MenuTableRow>() {
            @Override
            protected void updateItem(MenuTableRow r, boolean empty) {
               super.updateItem(r, empty);
               if (empty || r == null) {
                  this.setGraphic(null);
                  return;
               }
               Button bEdit = new Button("Sửa");
               bEdit.getStyleClass().add("btn-outline-blue");
               bEdit.setOnAction(e -> MenuController.this.showEditDialog(r));
               boolean blocked = false;
               try {
                  blocked = MenuController.this.menuRepository.isReferencedInOpenOrders(r.getId());
               } catch (Exception ex) {
                  blocked = false;
               }
               Button bDel = new Button("Xóa");
               bDel.getStyleClass().add("btn-outline-red");
               bDel.setDisable(blocked);
               if (blocked) {
                  bDel.setTooltip(
                     new Tooltip("Món đang trong đơn chưa thanh toán")
                  );
               } else {
                  bDel.setTooltip(null);
               }
               bDel.setOnAction(e -> MenuController.this.onDeleteItem(r));
               HBox h = new HBox(6, bEdit, bDel);
               h.setAlignment(Pos.CENTER);
               this.setGraphic(h);
            }
         }
      );
      this.colName.setStyle("-fx-alignment: CENTER-LEFT; -fx-font-size: 16px;");
      this.menuTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
      this.menuTable.setRowFactory(tv -> {
         TableRow<MenuTableRow> row = new TableRow<>();
         row.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY
               && ev.getClickCount() == 2
               && !row.isEmpty()
               && row.getItem() != null) {
               this.showEditDialog(row.getItem());
            }
         });
         return row;
      });
   }

   @FXML
   private void onAddCategory() {
      String res = this.oneFieldDialog("Thêm danh mục", "Tên:", "");
      if (res == null) {
         return;
      }
      if (res.isBlank()) {
         UiAlerts.warn("Danh mục", "Tên không được để trống.");
         return;
      }
      try {
         this.categoryRepository.insert(res.trim());
         this.fullReload();
      } catch (Exception e) {
         UiAlerts.error("Lỗi", e.getMessage());
      }
   }

   private void onEditCategory(CategoryRow row) {
      if (row == null || row.all || row.category == null) {
         return;
      }
      String res = this.oneFieldDialog("Sửa danh mục", "Tên:", row.category.getName());
      if (res == null || res.isBlank()) {
         return;
      }
      try {
         this.categoryRepository.update(row.category.getId(), res.trim());
         this.fullReload();
      } catch (Exception e) {
         UiAlerts.error("Lỗi", e.getMessage());
      }
   }

   private void onDeleteCategory(CategoryRow row) {
      if (row == null || row.all || row.category == null) {
         return;
      }
      if (!UiAlerts.confirm("Xóa danh mục", "Xóa danh mục \"" + row.category.getName() + "\"?")) {
         return;
      }
      try {
         if (this.menuRepository.categoryHasItems(row.category.getId())) {
            UiAlerts.warn("Không xóa được", "Danh mục vẫn còn món, hãy chuyển hoặc xóa món trước.");
            return;
         }
         this.categoryRepository.delete(row.category.getId());
         this.fullReload();
      } catch (Exception e) {
         UiAlerts.error("Lỗi", e.getMessage());
      }
   }

   @FXML
   private void onAdd() {
      this.showEditDialog(null);
   }

   private void fullReload() throws Exception {
      this.allMenuItems.clear();
      this.allMenuItems.addAll(this.menuRepository.findAll());
      this.rebuildCategoryMap();
      this.refreshCategoryItems();
      this.applyFilter();
   }

   private void rebuildCategoryMap() throws Exception {
      this.catNameById.clear();
      for (Category c : this.categoryRepository.findAll()) {
         this.catNameById.put(c.getId(), c.getName());
      }
   }

   private void refreshCategoryItems() {
      ObservableList<CategoryRow> rows = FXCollections.observableArrayList();
      int allCount = this.allMenuItems.size();
      rows.add(new CategoryRow(true, null, allCount));
      List<Category> cats;
      try {
         cats = this.categoryRepository.findAll();
      } catch (Exception e) {
         cats = new ArrayList<>();
      }
      cats.sort(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER));
      for (Category c : cats) {
         int n = 0;
         for (MenuItem m : this.allMenuItems) {
            if (m.getCategoryId() != null && m.getCategoryId() == c.getId()) {
               n++;
            }
         }
         rows.add(new CategoryRow(false, c, n));
      }
      this.categoryList.setItems(rows);
      if (this.categoryList.getSelectionModel().getSelectedItem() == null) {
         this.categoryList.getSelectionModel().selectFirst();
      }
   }

   private void applyFilter() {
      CategoryRow sel = this.categoryList.getSelectionModel().getSelectedItem();
      String keyword = this.searchField.getText() == null
         ? ""
         : this.searchField.getText().toLowerCase(Locale.ROOT).trim();
      String status = this.statusFilter.getSelectionModel().getSelectedItem();
      if (status == null) {
         status = "Tất cả";
      }
      final String st = status;
      final CategoryRow fsel = sel;
      List<MenuTableRow> out = this.allMenuItems.stream()
         .filter(
            m -> {
               if (fsel == null || fsel.all) {
                  return true;
               }
               return m.getCategoryId() != null && fsel.category != null && m.getCategoryId() == fsel.category.getId();
            }
         )
         .filter(
            m -> keyword.isEmpty()
               || m.getName().toLowerCase(Locale.ROOT).contains(keyword)
         )
         .filter(
            m -> {
               if ("Còn món".equals(st)) {
                  return m.isAvailable();
               }
               if ("Hết món".equals(st)) {
                  return !m.isAvailable();
               }
               return true;
            }
         )
         .map(this::toRow)
         .collect(Collectors.toList());
      this.menuTable.setItems(FXCollections.observableArrayList(out));
      this.lblResultCount.setText("Đang hiển thị " + out.size() + "/" + this.allMenuItems.size() + " món");
      if (this.lastCreatedOrEditedId > 0) {
         int look = this.lastCreatedOrEditedId;
         for (MenuTableRow r : out) {
            if (r.getId() == look) {
               this.menuTable.getSelectionModel().select(r);
               this.menuTable.scrollTo(r);
               break;
            }
         }
         this.lastCreatedOrEditedId = -1;
      }
   }

   private MenuTableRow toRow(MenuItem m) {
      Integer cid = m.getCategoryId();
      String cname = cid == null ? "—" : this.catNameById.getOrDefault(cid, "—");
      return new MenuTableRow(m.getId(), m.getName(), m.getPrice(), cid, cname, m.isAvailable());
   }

   private void onToggleAvailable(MenuTableRow row, boolean v, CheckBox b) {
      int id = row.getId();
      try {
         this.menuRepository.setAvailable(id, v);
         for (int i = 0; i < this.allMenuItems.size(); i++) {
            if (this.allMenuItems.get(i).getId() == id) {
               MenuItem m = this.allMenuItems.get(i);
               this.allMenuItems.set(
                  i,
                  new MenuItem(
                     m.getId(), m.getName(), m.getPrice(), m.getCategoryId(), v
                  )
               );
               break;
            }
         }
         this.applyFilter();
      } catch (Exception e) {
         b.setSelected(!v);
         UiAlerts.error("Lỗi", e.getMessage());
      }
   }

   private void onDeleteItem(MenuTableRow row) {
      if (!UiAlerts.confirm("Xóa món", "Xóa món \"" + row.getName() + "\"?")) {
         return;
      }
      try {
         if (this.menuRepository.isReferencedInOpenOrders(row.getId())) {
            UiAlerts.warn("Không xóa được", "Món đang nằm trong đơn mở, hãy xử lý đơn trước.");
            return;
         }
         this.menuRepository.deleteById(row.getId());
         this.fullReload();
      } catch (Exception e) {
         UiAlerts.error("Lỗi", e.getMessage());
      }
   }

   private void showEditDialog(MenuTableRow existing) {
      Dialog<ButtonType> dlg = new Dialog<>();
      boolean add = existing == null;
      dlg.setTitle(add ? "Thêm món mới" : "Sửa món");
      DialogPane pane = dlg.getDialogPane();
      pane.getStyleClass().add("custom-dialog");
      Label tTitle = new Label(add ? "Thêm món mới" : "Sửa món");
      tTitle.getStyleClass().add("dialog-title");
      VBox nameV = new VBox(4);
      Label ln = new Label("Tên món *");
      ln.getStyleClass().add("field-label");
      TextField nameF = new TextField();
      nameF.getStyleClass().add("dialog-field");
      nameF.setPromptText("Ví dụ: Cơm tấm sườn");
      Label nameErr = new Label();
      nameErr.getStyleClass().add("field-error");
      nameErr.setVisible(false);
      nameErr.managedProperty().bind(nameErr.visibleProperty());
      nameV.getChildren().addAll(ln, nameF, nameErr);
      VBox priceV = new VBox(4);
      Label lp = new Label("Giá (đ) *");
      lp.getStyleClass().add("field-label");
      TextField priceF = new TextField();
      priceF.getStyleClass().add("dialog-field");
      priceF.setPromptText("Ví dụ: 45000");
      Label priceErr = new Label();
      priceErr.getStyleClass().add("field-error");
      priceErr.setVisible(false);
      priceErr.managedProperty().bind(priceErr.visibleProperty());
      priceV.getChildren().addAll(lp, priceF, priceErr);
      VBox catV = new VBox(4);
      Label lc = new Label("Danh mục *");
      lc.getStyleClass().add("field-label");
      ComboBox<Category> cmb = new ComboBox<>();
      cmb.getStyleClass().add("dialog-combo");
      cmb.setMaxWidth(Double.MAX_VALUE);
      try {
         cmb.getItems().addAll(this.categoryRepository.findAll());
      } catch (Exception e) {
         // để rỗng
      }
      cmb.setConverter(
         new StringConverter<Category>() {
            @Override
            public String toString(Category c) {
               return c == null ? "" : c.getName();
            }

            @Override
            public Category fromString(String s) {
               return null;
            }
         }
      );
      catV.getChildren().addAll(lc, cmb);
      VBox stV = new VBox(6);
      Label ls = new Label("Trạng thái");
      ls.getStyleClass().add("field-label");
      ToggleGroup tg = new ToggleGroup();
      RadioButton rOn = new RadioButton("Còn món");
      RadioButton rOff = new RadioButton("Hết món");
      rOn.setToggleGroup(tg);
      rOff.setToggleGroup(tg);
      stV.getChildren().addAll(ls, new HBox(16, rOn, rOff));
      if (existing != null) {
         nameF.setText(existing.getName());
         priceF.setText(String.valueOf((long) existing.getPrice()));
         for (Category cx : cmb.getItems()) {
            if (existing.getCategoryId() != null && cx.getId() == existing.getCategoryId()) {
               cmb.getSelectionModel().select(cx);
               break;
            }
         }
         if (existing.isAvailable()) {
            rOn.setSelected(true);
         } else {
            rOff.setSelected(true);
         }
      } else {
         rOn.setSelected(true);
         if (!cmb.getItems().isEmpty()) {
            cmb.getSelectionModel().selectFirst();
         }
      }
      VBox form = new VBox(14, tTitle, new Separator(), nameV, priceV, catV, stV);
      form.setPadding(new Insets(20));
      pane.setContent(form);
      ButtonType save = new ButtonType("Lưu món", ButtonData.OK_DONE);
      ButtonType cancel = new ButtonType("Hủy", ButtonData.CANCEL_CLOSE);
      pane.getButtonTypes().setAll(save, cancel);
      Runnable validate = () -> {
         String name = nameF.getText() == null ? "" : nameF.getText().trim();
         nameErr.setVisible(false);
         priceErr.setVisible(false);
         nameF.getStyleClass().remove("dialog-field-error");
         priceF.getStyleClass().remove("dialog-field-error");
         if (name.isEmpty()) {
            nameErr.setText("Nhập tên món.");
            nameErr.setVisible(true);
            if (!nameF.getStyleClass().contains("dialog-field-error")) {
               nameF.getStyleClass().add("dialog-field-error");
            }
         }
         String ps = priceF.getText() == null
            ? ""
            : priceF.getText().replace(".", "").replace(",", "").replace(" ", "").trim();
         boolean priceOk = false;
         if (ps.isEmpty()) {
            priceErr.setText("Nhập giá lớn hơn 0.");
            priceErr.setVisible(true);
            if (!priceF.getStyleClass().contains("dialog-field-error")) {
               priceF.getStyleClass().add("dialog-field-error");
            }
         } else {
            try {
               long p = Long.parseLong(ps);
               if (p > 0L) {
                  priceOk = true;
               } else {
                  throw new NumberFormatException();
               }
            } catch (NumberFormatException e) {
               priceErr.setText("Giá phải là số dương (đồng).");
               priceErr.setVisible(true);
               if (!priceF.getStyleClass().contains("dialog-field-error")) {
                  priceF.getStyleClass().add("dialog-field-error");
               }
            }
         }
         boolean nameOk = !name.isEmpty();
         boolean ok = nameOk && priceOk && cmb.getValue() != null;
         Node saveN = pane.lookupButton(save);
         if (saveN instanceof Button) {
            Button saveBtn = (Button) saveN;
            saveBtn.setDisable(!ok);
         }
      };
      nameF.textProperty().addListener((a, b, c) -> validate.run());
      priceF.textProperty().addListener((a, b, c) -> validate.run());
      cmb.valueProperty().addListener((a, b, c) -> validate.run());
      tg.selectedToggleProperty().addListener((a, b, c) -> validate.run());
      validate.run();
      Optional<ButtonType> res = dlg.showAndWait();
      if (res.isEmpty() || res.get() != save) {
         return;
      }
      Node saveN2 = pane.lookupButton(save);
      if (saveN2 instanceof Button) {
         Button sbtn = (Button) saveN2;
         if (sbtn.isDisabled()) {
            return;
         }
      }
      String name = nameF.getText().trim();
      String ps2 = priceF.getText() == null
         ? "0"
         : priceF.getText().replace(".", "").replace(",", "").replace(" ", "").trim();
      long priceL = Long.parseLong(ps2);
      Category cat = cmb.getValue();
      if (cat == null) {
         return;
      }
      Integer cid = cat.getId();
      boolean av = rOn.isSelected();
      try {
         if (add) {
            this.lastCreatedOrEditedId = this.menuRepository.insert(name, (double) priceL, cid, av);
         } else {
            this.lastCreatedOrEditedId = existing.getId();
            this.menuRepository.update(existing.getId(), name, (double) priceL, cid, av);
         }
         this.fullReload();
      } catch (Exception e) {
         UiAlerts.error("Lỗi lưu", e.getMessage());
      }
   }

   private String oneFieldDialog(String title, String label, String initial) {
      TextField tf = new TextField(initial);
      Label l = new Label(label);
      l.getStyleClass().add("field-label");
      VBox v = new VBox(8, l, tf);
      v.setPadding(new Insets(12));
      Dialog<ButtonType> d = new Dialog<>();
      d.setTitle(title);
      d.getDialogPane().setContent(v);
      d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
      Optional<ButtonType> r = d.showAndWait();
      if (r.isEmpty() || r.get() != ButtonType.OK) {
         return null;
      }
      return tf.getText();
   }

   public static class MenuTableRow {
      private final int id;
      private final String name;
      private final double price;
      private final Integer categoryId;
      private final String categoryName;
      private final boolean available;

      public MenuTableRow(int id, String name, double price, Integer categoryId, String categoryName, boolean available) {
         this.id = id;
         this.name = name;
         this.price = price;
         this.categoryId = categoryId;
         this.categoryName = categoryName;
         this.available = available;
      }

      public int getId() {
         return this.id;
      }

      public String getName() {
         return this.name;
      }

      public double getPrice() {
         return this.price;
      }

      public Integer getCategoryId() {
         return this.categoryId;
      }

      public String getCategoryName() {
         return this.categoryName;
      }

      public boolean isAvailable() {
         return this.available;
      }
   }
}
