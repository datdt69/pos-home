package com.pos.model;

public class MenuItem {
   private int id;
   private String name;
   private double price;
   private Integer categoryId;
   private boolean available;

   public MenuItem() {
   }

   public MenuItem(int id, String name, double price, Integer categoryId, boolean available) {
      this.id = id;
      this.name = name;
      this.price = price;
      this.categoryId = categoryId;
      this.available = available;
   }

   public int getId() {
      return this.id;
   }

   public void setId(int id) {
      this.id = id;
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public double getPrice() {
      return this.price;
   }

   public void setPrice(double price) {
      this.price = price;
   }

   public Integer getCategoryId() {
      return this.categoryId;
   }

   public void setCategoryId(Integer categoryId) {
      this.categoryId = categoryId;
   }

   public boolean isAvailable() {
      return this.available;
   }

   public void setAvailable(boolean available) {
      this.available = available;
   }
}
