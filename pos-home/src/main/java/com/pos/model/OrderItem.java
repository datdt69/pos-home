package com.pos.model;

public class OrderItem {
   private int id;
   private int orderId;
   private int menuItemId;
   private int quantity;
   private double priceAtOrder;
   private String menuName;

   public int getId() {
      return this.id;
   }

   public void setId(int id) {
      this.id = id;
   }

   public int getOrderId() {
      return this.orderId;
   }

   public void setOrderId(int orderId) {
      this.orderId = orderId;
   }

   public int getMenuItemId() {
      return this.menuItemId;
   }

   public void setMenuItemId(int menuItemId) {
      this.menuItemId = menuItemId;
   }

   public int getQuantity() {
      return this.quantity;
   }

   public void setQuantity(int quantity) {
      this.quantity = quantity;
   }

   public double getPriceAtOrder() {
      return this.priceAtOrder;
   }

   public void setPriceAtOrder(double priceAtOrder) {
      this.priceAtOrder = priceAtOrder;
   }

   public String getMenuName() {
      return this.menuName;
   }

   public void setMenuName(String menuName) {
      this.menuName = menuName;
   }

   public double getLineTotal() {
      return (double)this.quantity * this.priceAtOrder;
   }
}
