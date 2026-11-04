package com.nitditto.service;

/** Công thức tổng tiền của một đơn đặt vé; tách riêng để kiểm thử độc lập với luồng gọi service khác. */
public final class BookingPriceCalculator {

  private BookingPriceCalculator() {
  }

  /**
   * Giá vé tính theo TỪNG hành khách nên nhân với số hành khách; phụ thu ghế đã là tổng của mọi ghế trong đơn;
   * hành lý/bảo hiểm và suất ăn tính theo đơn (mỗi dịch vụ chọn một lần).
   */
  public static double total(final Double farePerPassenger, final int passengerCount, final Double seatTotal,
      final Double ancillaryTotal, final Double mealTotal) {
    return orZero(farePerPassenger) * passengerCount + orZero(seatTotal) + orZero(ancillaryTotal) + orZero(mealTotal);
  }

  private static double orZero(final Double value) {
    return value != null ? value : 0.0;
  }
}
