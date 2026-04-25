package com.pos.model;

import java.time.LocalDateTime;

public class Order {
   private int id;
   private String orderNumber;
   private String status;
   private LocalDateTime createdAt;
   private LocalDateTime paidAt;
   private double total;

   public int getId() {
      return this.id;
   }

   public void setId(int id) {
      this.id = id;
   }

   public String getOrderNumber() {
      return this.orderNumber;
   }

   public void setOrderNumber(String orderNumber) {
      this.orderNumber = orderNumber;
   }

   public String getStatus() {
      return this.status;
   }

   public void setStatus(String status) {
      this.status = status;
   }

   public LocalDateTime getCreatedAt() {
      return this.createdAt;
   }

   public void setCreatedAt(LocalDateTime createdAt) {
      this.createdAt = createdAt;
   }

   public LocalDateTime getPaidAt() {
      return this.paidAt;
   }

   public void setPaidAt(LocalDateTime paidAt) {
      this.paidAt = paidAt;
   }

   public double getTotal() {
      return this.total;
   }

   public void setTotal(double total) {
      this.total = total;
   }
}
