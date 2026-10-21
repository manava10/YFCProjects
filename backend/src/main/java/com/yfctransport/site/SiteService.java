package com.yfctransport.site;

import com.yfctransport.audit.AuditTrail;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
class SiteService {
  private static final UUID SITE =
      UUID.fromString("00000000-0000-4000-8000-000000000001");
  private final JdbcTemplate db;
  private final AuditTrail audit;

  SiteService(JdbcTemplate db, AuditTrail audit) {
    this.db = db;
    this.audit = audit;
  }

  SiteDtos.Catalog catalog(boolean office) {
    return new SiteDtos.Catalog(
        profile(), stats(), materials(office), plants(office), destinations(office), freight(office));
  }

  SiteDtos.Desk desk() {
    return new SiteDtos.Desk(catalog(true), enquiries(), quotes());
  }

  SiteDtos.Profile saveProfile(SiteDtos.Profile profile) {
    db.update(
        """
        update site_profile set hero_kicker=?, hero_title=?, hero_title_rest=?, hero_subtitle=?,
        hero_line=?, about_kicker=?, about_title=?, about_paragraph_1=?, about_paragraph_2=?,
        about_paragraph_3=?, about_image=?, about_caption=?, about_caption_aside=?,
        contact_kicker=?, contact_title=?, contact_body=?, contact_tagline=?, footer_line=?
        where id=1
        """,
        profile.heroKicker().trim(),
        profile.heroTitle().trim(),
        profile.heroTitleRest().trim(),
        profile.heroSubtitle().trim(),
        profile.heroLine().trim(),
        profile.aboutKicker().trim(),
        profile.aboutTitle().trim(),
        profile.aboutParagraph1().trim(),
        profile.aboutParagraph2().trim(),
        profile.aboutParagraph3().trim(),
        profile.aboutImage(),
        profile.aboutCaption().trim(),
        profile.aboutCaptionAside().trim(),
        profile.contactKicker().trim(),
        profile.contactTitle().trim(),
        profile.contactBody().trim(),
        profile.contactTagline().trim(),
        profile.footerLine().trim());
    audit.record("SITE_PROFILE_UPDATED", "Site", SITE, "Customer page words updated");
    return profile();
  }

  SiteDtos.Stat saveStat(UUID id, SiteDtos.StatInput input) {
    int updated =
        db.update(
            "update site_stats set stat_value=?, label=? where id=?",
            input.value().trim(),
            input.label().trim(),
            id);
    if (updated != 1) missing("figure");
    audit.record("SITE_STAT_UPDATED", "SiteStat", id, input.label().trim());
    return new SiteDtos.Stat(id, input.value().trim(), input.label().trim());
  }

  SiteDtos.Material saveMaterial(UUID id, SiteDtos.MaterialInput input) {
    int updated =
        db.update(
            """
            update public_materials set name=?, grade=?, use_text=?, rate=?, image_path=?, active=?
            where id=?
            """,
            input.name().trim(),
            input.grade().trim(),
            input.useText().trim(),
            money(input.rate()),
            input.image(),
            input.active(),
            id);
    if (updated != 1) missing("material");
    audit.record("SITE_MATERIAL_UPDATED", "PublicMaterial", id, input.name().trim());
    return material(id);
  }

  SiteDtos.Material addMaterial(SiteDtos.NewMaterial input) {
    UUID id = UUID.randomUUID();
    int position = nextPosition("public_materials");
    db.update(
        """
        insert into public_materials(id, sort_order, name, grade, use_text, rate, image_path, active)
        values (?,?,?,?,?,?,?,true)
        """,
        id,
        position,
        input.name().trim(),
        input.grade().trim(),
        input.useText().trim(),
        money(input.rate()),
        input.image());
    audit.record("SITE_MATERIAL_ADDED", "PublicMaterial", id, input.name().trim());
    return material(id);
  }

  SiteDtos.Plant savePlant(UUID id, SiteDtos.PlantInput input) {
    int updated =
        db.update(
            "update public_plants set name=?, place=?, note=?, image_path=?, active=? where id=?",
            input.name().trim(),
            input.place().trim(),
            input.note().trim(),
            input.image(),
            input.active(),
            id);
    if (updated != 1) missing("plant");
    audit.record("SITE_PLANT_UPDATED", "PublicPlant", id, input.name().trim());
    return plant(id);
  }

  SiteDtos.Plant addPlant(SiteDtos.NewPlant input) {
    UUID id = UUID.randomUUID();
    db.update(
        """
        insert into public_plants(id, sort_order, name, place, note, image_path, active)
        values (?,?,?,?,?,?,true)
        """,
        id,
        nextPosition("public_plants"),
        input.name().trim(),
        input.place().trim(),
        input.note().trim(),
        input.image());
    for (UUID destination : db.query("select id from public_destinations", (rs, n) -> rs.getObject("id", UUID.class))) {
      db.update(
          "insert into public_freight(plant_id, destination_id, rate) values (?,?,0)",
          id,
          destination);
    }
    audit.record("SITE_PLANT_ADDED", "PublicPlant", id, input.name().trim());
    return plant(id);
  }

  SiteDtos.Destination saveDestination(UUID id, SiteDtos.DestinationInput input) {
    String name = input.name().trim();
    if (taken(name, id)) conflict("That drop is already on the card.");
    int updated = db.update("update public_destinations set name=?, active=? where id=?", name, input.active(), id);
    if (updated != 1) missing("drop");
    audit.record("SITE_DESTINATION_UPDATED", "PublicDestination", id, name);
    return new SiteDtos.Destination(id, name, input.active());
  }

  SiteDtos.Destination addDestination(SiteDtos.NewDestination input) {
    String name = input.name().trim();
    if (taken(name, null)) conflict("That drop is already on the card.");
    UUID id = UUID.randomUUID();
    db.update(
        "insert into public_destinations(id, sort_order, name, active) values (?,?,?,true)",
        id,
        nextPosition("public_destinations"),
        name);
    for (UUID plant : db.query("select id from public_plants", (rs, n) -> rs.getObject("id", UUID.class))) {
      db.update(
          "insert into public_freight(plant_id, destination_id, rate) values (?,?,0)", plant, id);
    }
    audit.record("SITE_DESTINATION_ADDED", "PublicDestination", id, name);
    return new SiteDtos.Destination(id, name, true);
  }

  void saveFreight(SiteDtos.FreightUpdate update) {
    for (SiteDtos.FreightLaneInput lane : update.lanes()) {
      int updated =
          db.update(
              "update public_freight set rate=? where plant_id=? and destination_id=?",
              money(lane.rate()),
              lane.plantId(),
              lane.destinationId());
      if (updated != 1) conflict("That plant and drop are not on the published card.");
    }
    audit.record("SITE_FREIGHT_UPDATED", "Site", SITE, update.lanes().size() + " lanes");
  }

  SiteDtos.EnquiryReceipt enquire(SiteDtos.EnquiryInput input) {
    lock();
    UUID id = UUID.randomUUID();
    String reference = nextReference();
    String note = input.note() == null ? "" : input.note().trim();
    db.update(
        "insert into public_enquiries(id, reference, name, phone, note) values (?,?,?,?,?)",
        id,
        reference,
        input.name().trim(),
        input.phone().trim(),
        note);
    audit.record("PUBLIC_ENQUIRY", "PublicEnquiry", id, reference + " " + input.name().trim());
    return new SiteDtos.EnquiryReceipt(reference);
  }

  SiteDtos.PublicQuote quote(SiteDtos.QuoteInput input) {
    lock();
    BigDecimal materialRate = materialRate(input.materialId());
    requireActive("public_plants", input.plantId(), "That plant is not on the public card.");
    List<Map<String, Object>> drops =
        db.queryForList(
            "select name, active from public_destinations where id=?", input.destinationId());
    if (drops.isEmpty() || !flag(drops.get(0).get("active")))
      conflict("That drop is not on the public card.");
    List<BigDecimal> freight =
        db.query(
            "select rate from public_freight where plant_id=? and destination_id=?",
            (rs, n) -> rs.getBigDecimal("rate"),
            input.plantId(),
            input.destinationId());
    if (freight.isEmpty() || freight.get(0).signum() <= 0)
      conflict("Freight is not published for that plant and drop.");
    UUID id = UUID.randomUUID();
    String reference = nextReference();
    String mediator = input.mediator() == null ? "" : input.mediator().trim();
    BigDecimal quantity = input.quantity().setScale(3, RoundingMode.HALF_UP);
    db.update(
        """
        insert into public_quotes(id, reference, material_id, plant_id, destination_id, quantity,
        mediator, material_rate, freight_rate, stage)
        values (?,?,?,?,?,?,?,?,?,'CONFIRMED')
        """,
        id,
        reference,
        input.materialId(),
        input.plantId(),
        input.destinationId(),
        quantity,
        mediator,
        materialRate,
        money(freight.get(0)));
    audit.record("PUBLIC_QUOTE", "PublicQuote", id, reference);
    return track(reference, true);
  }

  SiteDtos.PublicQuote track(String reference) {
    return track(reference, false);
  }

  private SiteDtos.PublicQuote track(String reference, boolean includeMediator) {
    String key = reference == null ? "" : reference.trim().toUpperCase(Locale.ROOT);
    List<SiteDtos.PublicQuote> rows =
        db.query(
            """
            select q.reference, m.name material_name, m.image_path, q.quantity, p.name plant_name,
            d.name destination, q.mediator, q.material_rate, q.freight_rate, q.stage
            from public_quotes q
            join public_materials m on m.id=q.material_id
            join public_plants p on p.id=q.plant_id
            join public_destinations d on d.id=q.destination_id
            where q.reference=?
            """,
            (rs, n) ->
                new SiteDtos.PublicQuote(
                    rs.getString("reference"),
                    rs.getString("material_name"),
                    rs.getString("image_path"),
                    rs.getBigDecimal("quantity"),
                    rs.getString("plant_name"),
                    rs.getString("destination"),
                    includeMediator ? rs.getString("mediator") : "",
                    rs.getBigDecimal("material_rate"),
                    rs.getBigDecimal("freight_rate"),
                    rs.getString("stage"),
                    label(rs.getString("stage"))),
            key);
    if (rows.isEmpty()) missing("order");
    return rows.get(0);
  }

  SiteDtos.StaffQuote advance(UUID id, SiteDtos.Stage stage) {
    List<String> rows =
        db.query(
            "select stage from public_quotes where id=?",
            (rs, n) -> rs.getString("stage"),
            id);
    if (rows.isEmpty()) missing("order");
    List<String> order = List.of("CONFIRMED", "LOADING", "ON_THE_WAY", "DELIVERED");
    int from = order.indexOf(rows.get(0));
    int to = order.indexOf(stage.name());
    if (to != from + 1) conflict("Move the ticket one stage forward.");
    int updated = db.update("update public_quotes set stage=? where id=?", stage.name(), id);
    if (updated != 1) missing("order");
    audit.record("SITE_QUOTE_STAGE", "PublicQuote", id, stage.name());
    return staffQuote(id);
  }

  private SiteDtos.Profile profile() {
    return db.queryForObject(
        """
        select hero_kicker, hero_title, hero_title_rest, hero_subtitle, hero_line, about_kicker,
        about_title, about_paragraph_1, about_paragraph_2, about_paragraph_3, about_image,
        about_caption, about_caption_aside, contact_kicker, contact_title, contact_body,
        contact_tagline, footer_line from site_profile where id=1
        """,
        (rs, n) ->
            new SiteDtos.Profile(
                rs.getString("hero_kicker"),
                rs.getString("hero_title"),
                rs.getString("hero_title_rest"),
                rs.getString("hero_subtitle"),
                rs.getString("hero_line"),
                rs.getString("about_kicker"),
                rs.getString("about_title"),
                rs.getString("about_paragraph_1"),
                rs.getString("about_paragraph_2"),
                rs.getString("about_paragraph_3"),
                rs.getString("about_image"),
                rs.getString("about_caption"),
                rs.getString("about_caption_aside"),
                rs.getString("contact_kicker"),
                rs.getString("contact_title"),
                rs.getString("contact_body"),
                rs.getString("contact_tagline"),
                rs.getString("footer_line")));
  }

  private List<SiteDtos.Stat> stats() {
    return db.query(
        "select id, stat_value, label from site_stats order by sort_order",
        (rs, n) ->
            new SiteDtos.Stat(
                rs.getObject("id", UUID.class), rs.getString("stat_value"), rs.getString("label")));
  }

  private List<SiteDtos.Material> materials(boolean office) {
    return db.query(
        "select id, name, grade, use_text, rate, image_path, active from public_materials"
            + (office ? "" : " where active")
            + " order by sort_order",
        (rs, n) -> materialRow(rs));
  }

  private SiteDtos.Material material(UUID id) {
    return db.queryForObject(
        "select id, name, grade, use_text, rate, image_path, active from public_materials where id=?",
        (rs, n) -> materialRow(rs),
        id);
  }

  private SiteDtos.Material materialRow(ResultSet rs) throws SQLException {
    return new SiteDtos.Material(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getString("grade"),
        rs.getString("use_text"),
        rs.getBigDecimal("rate"),
        rs.getString("image_path"),
        rs.getBoolean("active"));
  }

  private List<SiteDtos.Plant> plants(boolean office) {
    return db.query(
        "select id, name, place, note, image_path, active from public_plants"
            + (office ? "" : " where active")
            + " order by sort_order",
        (rs, n) -> plantRow(rs));
  }

  private SiteDtos.Plant plant(UUID id) {
    return db.queryForObject(
        "select id, name, place, note, image_path, active from public_plants where id=?",
        (rs, n) -> plantRow(rs),
        id);
  }

  private SiteDtos.Plant plantRow(ResultSet rs) throws SQLException {
    return new SiteDtos.Plant(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getString("place"),
        rs.getString("note"),
        rs.getString("image_path"),
        rs.getBoolean("active"));
  }

  private List<SiteDtos.Destination> destinations(boolean office) {
    return db.query(
        "select id, name, active from public_destinations"
            + (office ? "" : " where active")
            + " order by sort_order",
        (rs, n) ->
            new SiteDtos.Destination(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getBoolean("active")));
  }

  private List<SiteDtos.FreightLane> freight(boolean office) {
    String sql =
        office
            ? "select plant_id, destination_id, rate from public_freight"
            : """
            select f.plant_id, f.destination_id, f.rate from public_freight f
            join public_plants p on p.id=f.plant_id and p.active
            join public_destinations d on d.id=f.destination_id and d.active
            where f.rate > 0
            """;
    return db.query(
        sql,
        (rs, n) ->
            new SiteDtos.FreightLane(
                rs.getObject("plant_id", UUID.class),
                rs.getObject("destination_id", UUID.class),
                rs.getBigDecimal("rate")));
  }

  private List<SiteDtos.EnquiryView> enquiries() {
    return db.query(
        """
        select id, reference, name, phone, note, created_at from public_enquiries
        order by created_at desc, id desc limit 50
        """,
        (rs, n) ->
            new SiteDtos.EnquiryView(
                rs.getObject("id", UUID.class),
                rs.getString("reference"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("note"),
                rs.getTimestamp("created_at").toInstant()));
  }

  private List<SiteDtos.StaffQuote> quotes() {
    return db.query(
        """
        select q.id, q.reference, m.name material_name, q.quantity, p.name plant_name,
        d.name destination, q.mediator, q.material_rate, q.freight_rate, q.stage, q.created_at
        from public_quotes q
        join public_materials m on m.id=q.material_id
        join public_plants p on p.id=q.plant_id
        join public_destinations d on d.id=q.destination_id
        order by q.created_at desc, q.id desc limit 50
        """,
        (rs, n) ->
            new SiteDtos.StaffQuote(
                rs.getObject("id", UUID.class),
                rs.getString("reference"),
                rs.getString("material_name"),
                rs.getBigDecimal("quantity"),
                rs.getString("plant_name"),
                rs.getString("destination"),
                rs.getString("mediator"),
                rs.getBigDecimal("material_rate"),
                rs.getBigDecimal("freight_rate"),
                rs.getString("stage"),
                label(rs.getString("stage")),
                rs.getTimestamp("created_at").toInstant()));
  }

  private BigDecimal materialRate(UUID id) {
    List<Map<String, Object>> rows =
        db.queryForList("select rate, active from public_materials where id=?", id);
    if (rows.isEmpty() || !flag(rows.get(0).get("active")))
      conflict("That material is not on the public card.");
    return money(new BigDecimal(rows.get(0).get("rate").toString()));
  }

  private void requireActive(String table, UUID id, String message) {
    List<Map<String, Object>> rows =
        db.queryForList("select active from " + table + " where id=?", id);
    if (rows.isEmpty() || !flag(rows.get(0).get("active"))) conflict(message);
  }

  private SiteDtos.StaffQuote staffQuote(UUID id) {
    return db.queryForObject(
        """
        select q.id, q.reference, m.name material_name, q.quantity, p.name plant_name,
        d.name destination, q.mediator, q.material_rate, q.freight_rate, q.stage, q.created_at
        from public_quotes q
        join public_materials m on m.id=q.material_id
        join public_plants p on p.id=q.plant_id
        join public_destinations d on d.id=q.destination_id
        where q.id=?
        """,
        (rs, n) ->
            new SiteDtos.StaffQuote(
                rs.getObject("id", UUID.class),
                rs.getString("reference"),
                rs.getString("material_name"),
                rs.getBigDecimal("quantity"),
                rs.getString("plant_name"),
                rs.getString("destination"),
                rs.getString("mediator"),
                rs.getBigDecimal("material_rate"),
                rs.getBigDecimal("freight_rate"),
                rs.getString("stage"),
                label(rs.getString("stage")),
                rs.getTimestamp("created_at").toInstant()),
        id);
  }

  private boolean taken(String name, UUID except) {
    Integer count =
        except == null
            ? db.queryForObject(
                "select count(*) from public_destinations where lower(name)=lower(?)",
                Integer.class,
                name)
            : db.queryForObject(
                "select count(*) from public_destinations where lower(name)=lower(?) and id<>?",
                Integer.class,
                name,
                except);
    return count != null && count > 0;
  }

  private int nextPosition(String table) {
    Integer max = db.queryForObject("select coalesce(max(sort_order),0) from " + table, Integer.class);
    return (max == null ? 0 : max) + 1;
  }

  private void lock() {
    db.queryForObject("select id from site_guard where id=1 for update", Integer.class);
  }

  private static final SecureRandom TICKETS = new SecureRandom();

  private String nextReference() {
    db.update("update site_counters set last_number=last_number+1 where name='ticket'");
    Integer number =
        db.queryForObject("select last_number from site_counters where name='ticket'", Integer.class);
    byte[] raw = new byte[4];
    TICKETS.nextBytes(raw);
    String token = java.util.HexFormat.of().withUpperCase().formatHex(raw);
    return "YFC-" + number + "-" + token;
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private static boolean flag(Object value) {
    if (value instanceof Boolean on) return on;
    if (value instanceof Number number) return number.intValue() != 0;
    return false;
  }

  private static String label(String stage) {
    return switch (stage) {
      case "CONFIRMED" -> "Confirmed";
      case "LOADING" -> "Loading";
      case "ON_THE_WAY" -> "On the Way";
      case "DELIVERED" -> "Delivered";
      default -> stage;
    };
  }

  private static void missing(String what) {
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No " + what + " with that number.");
  }

  private static void conflict(String detail) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
  }

}
