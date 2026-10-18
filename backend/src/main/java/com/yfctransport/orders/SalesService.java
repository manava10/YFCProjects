package com.yfctransport.orders;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.customers.CustomerLocations;
import com.yfctransport.materials.MaterialRegistry;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
class SalesService {
  private final RfqRepository rfqs;
  private final RfqItemRepository rfqItems;
  private final QuotationRepository quotations;
  private final QuotationItemRepository quoteItems;
  private final OrderRepository orders;
  private final SalesOrderItemRepository orderItems;
  private final CustomerLocations locations;
  private final MaterialRegistry materials;
  private final AuditTrail audit;
  private final Clock clock;

  SalesService(
      RfqRepository rfqs,
      RfqItemRepository rfqItems,
      QuotationRepository quotations,
      QuotationItemRepository quoteItems,
      OrderRepository orders,
      SalesOrderItemRepository orderItems,
      CustomerLocations locations,
      MaterialRegistry materials,
      AuditTrail audit,
      Clock clock) {
    this.rfqs = rfqs;
    this.rfqItems = rfqItems;
    this.quotations = quotations;
    this.quoteItems = quoteItems;
    this.orders = orders;
    this.orderItems = orderItems;
    this.locations = locations;
    this.materials = materials;
    this.audit = audit;
    this.clock = clock;
  }

  private ResponseStatusException conflict(String reason) {
    return new ResponseStatusException(HttpStatus.CONFLICT, reason);
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  private Rfq lock(UUID id) {
    return rfqs.lock(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RFQ not found"));
  }

  @Transactional(readOnly = true)
  Rfq rfq(UUID id) {
    return rfqs.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RFQ not found"));
  }

  private void canQuote(Rfq rfq) {
    if (!Set.of("OPEN", "QUOTED").contains(rfq.status))
      throw conflict("RFQ cannot receive quotations in its current state");
  }

  private record LockedQuote(Rfq rfq, Quotation quote) {}

  private LockedQuote lockQuote(UUID id) {
    UUID rfqId =
        quotations
            .rfqIdFor(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
    Rfq r = lock(rfqId);
    return new LockedQuote(r, quotations.findById(id).orElseThrow());
  }

  SalesDtos.RfqView create(SalesDtos.CreateRfq request) {
    if (request.requiredDate().isBefore(today()))
      throw new IllegalArgumentException("Required date cannot be in the past");
    var site = locations.getActive(request.customerId(), request.deliverySiteId());
    if (request.items().stream().map(SalesDtos.RfqLine::materialId).distinct().count()
        != request.items().size())
      throw new IllegalArgumentException("Each material may appear only once per RFQ");
    Rfq r = new Rfq();
    r.customerId = request.customerId();
    r.branchId = site.branchId();
    r.projectId = site.projectId();
    r.deliverySiteId = site.siteId();
    r.deliveryAddress = site.address();
    r.businessType = request.businessType().name();
    r.requiredDate = request.requiredDate();
    r.remarks = request.remarks();
    rfqs.save(r);
    int position = 0;
    for (var line : request.items()) {
      var material = materials.getActive(line.materialId());
      RfqItem i = new RfqItem();
      i.rfqId = r.id;
      i.position = ++position;
      i.materialId = material.id();
      i.material = material.name();
      i.quantity = line.quantity().setScale(3, RoundingMode.UNNECESSARY);
      rfqItems.save(i);
    }
    audit.record("RFQ_CREATED", "RFQ", r.id, "Materials=" + request.items().size());
    return rfqView(r);
  }

  @Transactional(readOnly = true)
  SalesDtos.RfqView rfqView(Rfq r) {
    return new SalesDtos.RfqView(
        r.id,
        r.customerId,
        r.branchId,
        r.projectId,
        r.deliverySiteId,
        r.deliveryAddress,
        r.businessType,
        r.requiredDate,
        r.remarks,
        r.status,
        r.createdAt,
        rfqItems.findByRfqIdOrderByPosition(r.id).stream()
            .map(
                i ->
                    new SalesDtos.RfqLineView(
                        i.id, i.position, i.materialId, i.material, "TONNE", i.quantity))
            .toList());
  }

  SalesDtos.QuoteView createQuotation(UUID rfqId, SalesDtos.CreateQuotation request) {
    Rfq r = lock(rfqId);
    canQuote(r);
    if (request.validUntil().isBefore(today()))
      throw new IllegalArgumentException("Quotation validity cannot be in the past");
    List<RfqItem> lines = rfqItems.findByRfqIdOrderByPosition(r.id);
    Map<UUID, SalesDtos.QuotePrice> prices = new HashMap<>();
    for (var p : request.items())
      if (prices.put(p.rfqItemId(), p) != null)
        throw new IllegalArgumentException("Duplicate RFQ item price");
    if (prices.size() != lines.size()
        || !prices.keySet().equals(lines.stream().map(i -> i.id).collect(Collectors.toSet())))
      throw new IllegalArgumentException(
          "Price every RFQ item exactly once; extra items are not allowed");
    var history = quotations.findByRfqIdOrderByRevisionDesc(r.id);
    Quotation q = new Quotation();
    q.rfqId = r.id;
    q.revision = history.isEmpty() ? 1 : history.getFirst().revision + 1;
    q.validUntil = request.validUntil();
    q.paymentTerms = request.paymentTerms().strip();
    q.deliveryTerms = request.deliveryTerms().strip();
    quotations.save(q);
    for (RfqItem line : lines) {
      var price = prices.get(line.id);
      if (r.businessType.equals("MATERIAL") && price.transportRate().signum() != 0)
        throw new IllegalArgumentException("Material-only RFQs cannot include transport charges");
      if (r.businessType.equals("TRANSPORT") && price.materialRate().signum() != 0)
        throw new IllegalArgumentException("Transport-only RFQs cannot include material charges");
      QuotationItem i = new QuotationItem();
      i.quotationId = q.id;
      i.rfqId = r.id;
      i.rfqItemId = line.id;
      i.position = line.position;
      i.materialId = line.materialId;
      i.material = line.material;
      i.quantity = line.quantity;
      i.materialRate = price.materialRate().setScale(2, RoundingMode.UNNECESSARY);
      i.transportRate = price.transportRate().setScale(2, RoundingMode.UNNECESSARY);
      i.materialAmount = amount(i.quantity, i.materialRate);
      i.transportAmount = amount(i.quantity, i.transportRate);
      quoteItems.save(i);
    }
    audit.record(
        "QUOTATION_CREATED", "QUOTATION", q.id, "RFQ " + r.id + ", revision " + q.revision);
    return quoteView(q);
  }

  SalesDtos.QuoteView send(UUID id) {
    var locked = lockQuote(id);
    var r = locked.rfq();
    var q = locked.quote();
    canQuote(r);
    if (!q.status.equals("DRAFT")) throw conflict("Only a draft quotation can be sent");
    if (q.validUntil.isBefore(today())) throw conflict("Quotation has expired");
    var history = quotations.findByRfqIdOrderByRevisionDesc(r.id);
    if (history.getFirst().revision != q.revision)
      throw conflict("Only the latest draft revision can be sent");
    for (var previous : history)
      if (!previous.id.equals(q.id) && Set.of("DRAFT", "SENT").contains(previous.status))
        previous.status = "SUPERSEDED";
    q.status = "SENT";
    q.sentAt = Instant.now(clock);
    r.status = "QUOTED";
    audit.record(
        "QUOTATION_SENT",
        "QUOTATION",
        id,
        "Revision " + q.revision + " marked sent; external delivery is recorded manually");
    return quoteView(q);
  }

  SalesDtos.QuoteView accept(UUID id, SalesDtos.AcceptQuotation request) {
    var locked = lockQuote(id);
    var r = locked.rfq();
    var q = locked.quote();
    requireDecisionState(r, q);
    if (q.validUntil.isBefore(today())) throw conflict("Expired quotations cannot be accepted");
    q.status = "ACCEPTED";
    decide(q, request.customerContact(), request.approvalReference(), request.note());
    r.status = "ACCEPTED";
    for (var previous : quotations.findByRfqIdOrderByRevisionDesc(r.id))
      if (!previous.id.equals(q.id) && Set.of("DRAFT", "SENT").contains(previous.status))
        previous.status = "SUPERSEDED";
    audit.record(
        "QUOTATION_ACCEPTED",
        "QUOTATION",
        id,
        "Customer acceptance recorded; reference " + q.approvalReference);
    return quoteView(q);
  }

  SalesDtos.QuoteView reject(UUID id, SalesDtos.RejectQuotation request) {
    var locked = lockQuote(id);
    var r = locked.rfq();
    var q = locked.quote();
    requireDecisionState(r, q);
    q.status = "REJECTED";
    decide(q, request.customerContact(), request.approvalReference(), request.reason());
    r.status = "OPEN";
    audit.record("QUOTATION_REJECTED", "QUOTATION", id, "Customer rejection recorded");
    return quoteView(q);
  }

  private void requireDecisionState(Rfq r, Quotation q) {
    if (!r.status.equals("QUOTED") || !q.status.equals("SENT"))
      throw conflict("Only the current sent quotation can receive a decision");
  }

  private void decide(Quotation q, String contact, String reference, String note) {
    q.decidedAt = Instant.now(clock);
    q.decidedBy = SecurityContextHolder.getContext().getAuthentication().getName();
    q.customerContact = contact.strip();
    q.approvalReference = reference.strip();
    q.decisionNote = note;
  }

  SalesDtos.Conversion convert(UUID id) {
    var locked = lockQuote(id);
    var r = locked.rfq();
    var q = locked.quote();
    var existing = orders.findBySourceRfqId(r.id);
    if (existing.isPresent()) {
      if (!id.equals(existing.get().sourceQuotationId))
        throw conflict("RFQ was converted using another quotation");
      return new SalesDtos.Conversion(existing.get().id, id, r.id, false);
    }
    if (!r.status.equals("ACCEPTED") || !q.status.equals("ACCEPTED"))
      throw conflict("Only an accepted quotation can become an order");
    locations.getActive(r.customerId, r.deliverySiteId);
    var lines = quoteItems.findByQuotationIdOrderByPosition(id);
    for (var line : lines) materials.getActive(line.materialId);
    BigDecimal quantity =
        lines.stream().map(i -> i.quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    String description =
        lines.size() == 1 ? lines.getFirst().material : "Multiple materials (" + lines.size() + ")";
    BigDecimal rate =
        lines.size() == 1
            ? lines.getFirst().materialRate.add(lines.getFirst().transportRate)
            : BigDecimal.ZERO;
    SalesOrder o =
        new SalesOrder(
            r.customerId, r.businessType, description, r.deliveryAddress, quantity, rate);
    o.branchId = r.branchId;
    o.projectId = r.projectId;
    o.deliverySiteId = r.deliverySiteId;
    o.materialId = lines.size() == 1 ? lines.getFirst().materialId : null;
    o.sourceRfqId = r.id;
    o.sourceQuotationId = q.id;
    o.requiredDate = r.requiredDate;
    o.paymentTerms = q.paymentTerms;
    o.deliveryTerms = q.deliveryTerms;
    orders.save(o);
    for (var line : lines) {
      SalesOrderItem i = new SalesOrderItem();
      i.orderId = o.id;
      i.sourceRfqItemId = line.rfqItemId;
      i.materialId = line.materialId;
      i.position = line.position;
      i.material = line.material;
      i.orderedQuantity = line.quantity;
      i.materialRate = line.materialRate;
      i.transportRate = line.transportRate;
      i.materialAmount = line.materialAmount;
      i.transportAmount = line.transportAmount;
      orderItems.save(i);
    }
    r.status = "CONVERTED";
    audit.record(
        "QUOTATION_CONVERTED",
        "ORDER",
        o.id,
        "Source quotation " + q.id + ", ordered-quantity billing");
    return new SalesDtos.Conversion(o.id, id, r.id, true);
  }

  static BigDecimal amount(BigDecimal quantity, BigDecimal rate) {
    return quantity.multiply(rate).setScale(2, RoundingMode.HALF_UP);
  }

  @Transactional(readOnly = true)
  SalesDtos.QuoteView quotation(UUID id) {
    return quoteView(
        quotations
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found")));
  }

  @Transactional(readOnly = true)
  List<SalesDtos.QuoteView> quotationHistory(UUID rfqId) {
    rfq(rfqId);
    return quotations.findByRfqIdOrderByRevisionDesc(rfqId).stream().map(this::quoteView).toList();
  }

  private SalesDtos.QuoteView quoteView(Quotation q) {
    var lines = quoteItems.findByQuotationIdOrderByPosition(q.id);
    BigDecimal material =
        lines.stream().map(i -> i.materialAmount).reduce(new BigDecimal("0.00"), BigDecimal::add);
    BigDecimal transport =
        lines.stream().map(i -> i.transportAmount).reduce(new BigDecimal("0.00"), BigDecimal::add);
    var converted =
        orders
            .findBySourceRfqId(q.rfqId)
            .filter(o -> q.id.equals(o.sourceQuotationId))
            .map(o -> o.id)
            .orElse(null);
    return new SalesDtos.QuoteView(
        q.id,
        q.rfqId,
        q.revision,
        q.status,
        q.validUntil.isBefore(today()),
        q.validUntil,
        q.paymentTerms,
        q.deliveryTerms,
        "INR",
        "ORDERED_QUANTITY",
        "EXCLUDED_PENDING_INVOICING",
        material,
        transport,
        material.add(transport),
        q.sentAt,
        q.decidedAt,
        q.decidedBy,
        q.customerContact,
        q.approvalReference,
        q.decisionNote,
        converted,
        lines.stream()
            .map(
                i ->
                    new SalesDtos.QuoteLineView(
                        i.rfqItemId,
                        i.position,
                        i.materialId,
                        i.material,
                        "TONNE",
                        i.quantity,
                        i.materialRate,
                        i.transportRate,
                        i.materialAmount,
                        i.transportAmount,
                        i.materialAmount.add(i.transportAmount)))
            .toList());
  }
}
