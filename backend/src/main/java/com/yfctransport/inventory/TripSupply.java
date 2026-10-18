package com.yfctransport.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface TripSupply {
  void claim(UUID trip, UUID orderItem, UUID reservation, UUID purchase, BigDecimal quantity);

  void dispatch(UUID trip, BigDecimal loaded, String reference);

  void deliver(
      UUID trip,
      BigDecimal loaded,
      BigDecimal received,
      BigDecimal accepted,
      LocalDate date,
      String reference,
      String reason);

  void cancel(UUID trip);
}
