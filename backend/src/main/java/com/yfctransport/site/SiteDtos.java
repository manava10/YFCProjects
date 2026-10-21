package com.yfctransport.site;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

class SiteDtos {
  static final String IMAGE =
      "/media/(agg-20|agg-10|agg-vsi|agg-dust|agg-msand|plant|hero|truck)\\.jpg";

  enum Stage {
    CONFIRMED,
    LOADING,
    ON_THE_WAY,
    DELIVERED
  }

  record Profile(
      @NotBlank @Size(max = 80) String heroKicker,
      @NotBlank @Size(max = 120) String heroTitle,
      @NotBlank @Size(max = 120) String heroTitleRest,
      @NotBlank @Size(max = 300) String heroSubtitle,
      @NotBlank @Size(max = 200) String heroLine,
      @NotBlank @Size(max = 80) String aboutKicker,
      @NotBlank @Size(max = 200) String aboutTitle,
      @NotBlank @Size(max = 800) String aboutParagraph1,
      @NotBlank @Size(max = 800) String aboutParagraph2,
      @NotBlank @Size(max = 800) String aboutParagraph3,
      @NotBlank @Pattern(regexp = IMAGE) String aboutImage,
      @NotBlank @Size(max = 80) String aboutCaption,
      @NotBlank @Size(max = 80) String aboutCaptionAside,
      @NotBlank @Size(max = 80) String contactKicker,
      @NotBlank @Size(max = 200) String contactTitle,
      @NotBlank @Size(max = 1000) String contactBody,
      @NotBlank @Size(max = 200) String contactTagline,
      @NotBlank @Size(max = 200) String footerLine) {}

  record Stat(UUID id, String value, String label) {}

  record StatInput(@NotBlank @Size(max = 40) String value, @NotBlank @Size(max = 80) String label) {}

  record Material(
      UUID id,
      String name,
      String grade,
      String useText,
      BigDecimal rate,
      String image,
      boolean active) {}

  record MaterialInput(
      @NotBlank @Size(max = 80) String name,
      @NotBlank @Size(max = 40) String grade,
      @NotBlank @Size(max = 200) String useText,
      @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal rate,
      @NotBlank @Pattern(regexp = IMAGE) String image,
      @NotNull Boolean active) {}

  record NewMaterial(
      @NotBlank @Size(max = 80) String name,
      @NotBlank @Size(max = 40) String grade,
      @NotBlank @Size(max = 200) String useText,
      @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal rate,
      @NotBlank @Pattern(regexp = IMAGE) String image) {}

  record Plant(
      UUID id, String name, String place, String note, String image, boolean active) {}

  record PlantInput(
      @NotBlank @Size(max = 80) String name,
      @NotBlank @Size(max = 80) String place,
      @NotBlank @Size(max = 300) String note,
      @NotBlank @Pattern(regexp = IMAGE) String image,
      @NotNull Boolean active) {}

  record NewPlant(
      @NotBlank @Size(max = 80) String name,
      @NotBlank @Size(max = 80) String place,
      @NotBlank @Size(max = 300) String note,
      @NotBlank @Pattern(regexp = IMAGE) String image) {}

  record Destination(UUID id, String name, boolean active) {}

  record DestinationInput(
      @NotBlank @Size(max = 120) String name, @NotNull Boolean active) {}

  record NewDestination(@NotBlank @Size(max = 120) String name) {}

  record FreightLane(UUID plantId, UUID destinationId, BigDecimal rate) {}

  record FreightLaneInput(
      @NotNull UUID plantId,
      @NotNull UUID destinationId,
      @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal rate) {}

  record FreightUpdate(
      @NotEmpty @Size(max = 400) List<@NotNull @Valid FreightLaneInput> lanes) {}

  record Catalog(
      Profile profile,
      List<Stat> stats,
      List<Material> materials,
      List<Plant> plants,
      List<Destination> destinations,
      List<FreightLane> freight) {}

  record EnquiryInput(
      @NotBlank @Size(min = 2, max = 160) String name,
      @NotBlank @Pattern(regexp = "[+0-9 ()-]{7,20}") String phone,
      @Size(max = 1000) String note) {}

  record EnquiryView(
      UUID id, String reference, String name, String phone, String note, Instant createdAt) {}

  record EnquiryReceipt(String reference) {}

  record QuoteInput(
      @NotNull UUID materialId,
      @NotNull UUID plantId,
      @NotNull UUID destinationId,
      @NotNull @DecimalMin("1") @DecimalMax("500") @Digits(integer = 3, fraction = 3)
          BigDecimal quantity,
      @Size(max = 160) String mediator) {}

  record PublicQuote(
      String reference,
      String materialName,
      String materialImage,
      BigDecimal quantity,
      String plantName,
      String destination,
      String mediator,
      BigDecimal materialRate,
      BigDecimal freightRate,
      String stage,
      String stageLabel) {}

  record StaffQuote(
      UUID id,
      String reference,
      String materialName,
      BigDecimal quantity,
      String plantName,
      String destination,
      String mediator,
      BigDecimal materialRate,
      BigDecimal freightRate,
      String stage,
      String stageLabel,
      Instant createdAt) {}

  record StageInput(@NotNull Stage stage) {}

  record Desk(Catalog catalog, List<EnquiryView> enquiries, List<StaffQuote> quotes) {}
}
