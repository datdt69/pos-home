package com.pos.util;

import java.util.Locale;

public final class MoneyFormat {
   private MoneyFormat() {
   }

   public static String vnd(double v) {
      long n = Math.round(v);
      String s = String.format(Locale.US, "%,d", n);
      return s.replace(',', '.') + " đ";
   }
}
