package com.yfctransport.orders;

import com.yfctransport.audit.AuditTrail;
import com.yfctransport.customers.CustomerRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS','ACCOUNTS')")
class OrderController {
  private final OrderRepository orders;
  private final DispatchRepository dispatches;
  private final CustomerRegistry customers;
  private final AuditTrail audit;
  private final OrderPreparation preparation;
  private final SalesOrderItemRepository items;

  OrderController(
      OrderRepository orders,
      DispatchRepository dispatches,
      CustomerRegistry customers,
      AuditTrail audit,
      OrderPreparation preparation,
      SalesOrderItemRepository items) {
    this.orders = orders;
    this.dispatches = dispatches;
    this.customers = customers;
    this.audit = audit;
    this.preparation = preparation;
    this.items = items;
  }

  enum BusinessType {
    MATERIAL,
    TRANSPORT,
    MATERIAL_AND_TRANSPORT
  }

  record CreateOrder(
      @NotNull UUID customerId,
      @NotNull BusinessType businessType,
      @Size(max = 160) String material,
      @Size(max = 500) String deliverySite,
      @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal orderedQuantity,
      @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal unitRate,
      UUID branchId,
      UUID projectId,
      UUID deliverySiteId,
      UUID materialId) {}

  record RecordDispatch(
      @NotBlank @Size(max = 30) String reference,
      @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal loadedQuantity,
      @NotNull @DecimalMin("0.000") @Digits(integer = 11, fraction = 3) BigDecimal receivedQuantity,
      UUID orderItemId) {}

  record OrderView(
      UUID id,
      UUID customerId,
      String businessType,
      String material,
      String deliverySite,
      String billingBasis,
      BigDecimal orderedQuantity,
      BigDecimal loadedQuantity,
      BigDecimal receivedQuantity,
      BigDecimal billableQuantity,
      BigDecimal subtotal,
      UUID branchId,
      UUID projectId,
      UUID deliverySiteId,
      UUID materialId,
      UUID sourceRfqId,
      UUID sourceQuotationId,
      String paymentTerms,
      String deliveryTerms,
      String currency,
      String taxTreatment,
      java.time.LocalDate requiredDate,
      List<OrderLineView> items) {}

  record OrderLineView(
      UUID id,
      int position,
      UUID materialId,
      String material,
      String unit,
      BigDecimal orderedQuantity,
      BigDecimal billableQuantity,
      BigDecimal loadedQuantity,
      BigDecimal receivedQuantity,
      BigDecimal materialRate,
      BigDecimal transportRate,
      BigDecimal materialAmount,
      BigDecimal transportAmount,
      BigDecimal subtotal) {}

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  OrderView create(@Valid @RequestBody CreateOrder r) {
    if (!customers.exists(r.customerId()))
      throw new IllegalArgumentException("Customer does not exist");
    var resolved = preparation.prepare(r);
    SalesOrder created =
        orders.save(
            new SalesOrder(
                r.customerId(),
                r.businessType().name(),
                resolved.material(),
                resolved.address(),
                r.orderedQuantity(),
                r.unitRate()));
    created.branchId = resolved.branchId();
    created.projectId = resolved.projectId();
    created.deliverySiteId = resolved.siteId();
    created.materialId = resolved.materialId();
    SalesOrderItem line = new SalesOrderItem();
    line.orderId = created.id;
    line.position = 1;
    line.materialId = created.materialId;
    line.material = created.material;
    line.orderedQuantity = created.orderedQuantity;
    boolean transport = created.businessType.equals("TRANSPORT");
    line.materialRate = transport ? BigDecimal.ZERO : created.unitRate;
    line.transportRate = transport ? created.unitRate : BigDecimal.ZERO;
    line.materialAmount = SalesService.amount(line.orderedQuantity, line.materialRate);
    line.transportAmount = SalesService.amount(line.orderedQuantity, line.transportRate);
    items.save(line);
    audit.record("ORDER_CREATED", "ORDER", created.id, "Billing basis ORDERED_QUANTITY");
    return view(created);
  }

  @GetMapping("/{id}")
  @Transactional(readOnly = true)
  OrderView get(@PathVariable UUID id) {
    return view(find(id));
  }

  @PostMapping("/{id}/dispatches")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATIONS')")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  OrderView dispatch(@PathVariable UUID id, @Valid @RequestBody RecordDispatch r) {
    SalesOrder o =
        orders
            .lock(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    List<SalesOrderItem> lines = items.findByOrderIdOrderByPosition(id);
    UUID itemId = r.orderItemId();
    if (itemId == null) {
      if (lines.size() != 1)
        throw new IllegalArgumentException("orderItemId is required for a multi-item order");
      itemId = lines.getFirst().id;
    }
    UUID selected = itemId;
    if (lines.stream().noneMatch(i -> i.id.equals(selected)))
      throw new IllegalArgumentException("Order item does not belong to this order");
    if (lines.stream()
        .filter(i -> i.id.equals(selected))
        .anyMatch(i -> i.fulfillmentMode.equals("TRIP")))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Use the trip workflow for this order item");
    Dispatch dispatch =
        new Dispatch(id, r.reference().strip(), r.loadedQuantity(), r.receivedQuantity());
    dispatch.orderItemId = itemId;
    dispatches.save(dispatch);
    audit.record(
        "DISPATCH_RECORDED",
        "ORDER",
        o.id,
        "Loaded " + r.loadedQuantity() + " tonnes; received " + r.receivedQuantity() + " tonnes");
    return view(o);
  }

  private SalesOrder find(UUID id) {
    return orders
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
  }

  private OrderView view(SalesOrder o) {
    List<Dispatch> trips = dispatches.findByOrderId(o.id);
    List<SalesOrderItem> orderLines = items.findByOrderIdOrderByPosition(o.id);
    BigDecimal subtotal =
        orderLines.stream()
            .map(i -> i.materialAmount.add(i.transportAmount))
            .reduce(new BigDecimal("0.00"), BigDecimal::add);
    return new OrderView(
        o.id,
        o.customerId,
        o.businessType,
        o.material,
        o.deliverySite,
        "ORDERED_QUANTITY",
        o.orderedQuantity,
        trips.stream().map(d -> d.loadedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add),
        trips.stream().map(d -> d.receivedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add),
        o.orderedQuantity,
        subtotal,
        o.branchId,
        o.projectId,
        o.deliverySiteId,
        o.materialId,
        o.sourceRfqId,
        o.sourceQuotationId,
        o.paymentTerms,
        o.deliveryTerms,
        "INR",
        "EXCLUDED_PENDING_INVOICING",
        o.requiredDate,
        orderLines.stream()
            .map(
                i ->
                    new OrderLineView(
                        i.id,
                        i.position,
                        i.materialId,
                        i.material,
                        "TONNE",
                        i.orderedQuantity,
                        i.orderedQuantity,
                        trips.stream()
                            .filter(d -> i.id.equals(d.orderItemId))
                            .map(d -> d.loadedQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add),
                        trips.stream()
                            .filter(d -> i.id.equals(d.orderItemId))
                            .map(d -> d.receivedQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add),
                        i.materialRate,
                        i.transportRate,
                        i.materialAmount,
                        i.transportAmount,
                        i.materialAmount.add(i.transportAmount)))
            .toList());
  }
}
