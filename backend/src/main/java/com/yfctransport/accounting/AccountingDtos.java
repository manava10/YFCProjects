package com.yfctransport.accounting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

class AccountingDtos {
  enum Type {
    ASSET,
    LIABILITY,
    EQUITY,
    INCOME,
    EXPENSE
  }

  record Account(
      @NotNull UUID accountId,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,29}") String code,
      @NotBlank @Size(max = 160) String name,
      @NotNull Type type) {}

  record AccountStatus(
      @NotNull Boolean expectedActive,
      @NotNull Boolean active,
      @NotBlank @Size(max = 1000) String reason) {}

  record Period(
      @NotNull UUID periodId,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,29}") String code,
      @NotNull LocalDate startsOn,
      @NotNull LocalDate endsOn) {}

  record Line(
      @NotNull UUID accountId,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal debit,
      @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal credit,
      @NotNull @Size(max = 500) String narration) {}

  record Journal(
      @NotNull UUID journalId,
      @NotNull UUID periodId,
      @NotNull UUID branchId,
      @NotNull LocalDate entryDate,
      @NotBlank @Size(max = 1000) String description,
      @NotBlank @Size(max = 300) String reference,
      @NotNull @Size(min = 2, max = 100) List<@NotNull @Valid Line> lines) {}

  record Decision(@NotNull UUID eventId, @NotBlank @Size(max = 1000) String reason) {}

  record Reversal(
      @NotNull UUID journalId,
      @NotNull UUID periodId,
      @NotNull LocalDate entryDate,
      @NotBlank @Size(max = 1000) String reason,
      @NotBlank @Size(max = 300) String reference) {}
}
