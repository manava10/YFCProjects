package com.yfctransport;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MigrationUpgradeTest {
  @Test
  void businessMastersMigrationPreservesExistingOrders() {
    String url = "jdbc:h2:mem:yfc_upgrade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    try (HikariDataSource datasource = new HikariDataSource()) {
      datasource.setJdbcUrl(url);
      datasource.setUsername("sa");
      datasource.setPassword("");
      datasource.setMaximumPoolSize(3);
      Flyway.configure().dataSource(datasource).target("3").load().migrate();
      JdbcTemplate db = new JdbcTemplate(datasource);
      UUID customer = UUID.randomUUID(), order = UUID.randomUUID();
      db.update(
          "insert into customers(id,name,mobile,created_at) values(?,?,?,current_timestamp)",
          customer,
          "Existing buyer",
          "9876543210");
      db.update(
          "insert into"
              + " sales_orders(id,version,customer_id,business_type,material,delivery_site,ordered_quantity,unit_rate,created_at)"
              + " values(?,0,?,'MATERIAL','20 MM','Existing site',100,1200,current_timestamp)",
          order,
          customer);
      UUID dispatch = UUID.randomUUID();
      db.update(
          "insert into dispatches(id,order_id,reference,loaded_quantity,received_quantity)"
              + " values(?,?,?,99,98)",
          dispatch,
          order,
          "LEGACY-SLIP");
      UUID truck = UUID.randomUUID(), rate = UUID.randomUUID(), fuel = UUID.randomUUID();
      db.update(
          "insert into"
              + " trucks(id,registration,capacity_tonnes,tank_capacity_litres,mileage_km_per_litre)"
              + " values(?,'HR55UPGRADE',35,400,3)",
          truck);
      db.update(
          "insert into diesel_rates(id,pump,location,effective_date,price_per_litre)"
              + " values(?,'Existing pump','Gurgaon',date '2026-01-01',91.50)",
          rate);
      db.update(
          "insert into"
              + " fuel_entries(id,truck_id,rate_id,receipt_reference,filled_on,quantity_litres,actual_price_per_litre,amount)"
              + " values(?,?,?,'LEGACY-FUEL',date '2026-01-01',100,92,9200)",
          fuel,
          truck,
          rate);
      Flyway.configure().dataSource(datasource).load().migrate();
      assertThat(
              db.queryForObject("select status from fuel_entries where id=?", String.class, fuel))
          .isEqualTo("PENDING_VERIFICATION");
      assertThat(
              db.queryForObject(
                  "select submitted_by from fuel_entries where id=?", String.class, fuel))
          .isEqualTo("LEGACY_IMPORT");
      assertThat(
              db.queryForObject(
                  "select reference_price_per_litre from fuel_entries where id=?",
                  java.math.BigDecimal.class,
                  fuel))
          .isEqualByComparingTo("91.50");
      assertThat(
              db.queryForObject(
                  "select amount from fuel_entries where id=?", java.math.BigDecimal.class, fuel))
          .isEqualByComparingTo("9200");
      assertThat(
              db.queryForObject(
                  "select operational_status from trucks where id=?", String.class, truck))
          .isEqualTo("INACTIVE");
      assertThat(
              db.queryForObject(
                  "select order_item_id from dispatches where id=?", UUID.class, dispatch))
          .isEqualTo(order);
      assertThat(
              db.queryForObject(
                  "select material_amount+transport_amount from sales_order_items where order_id=?",
                  java.math.BigDecimal.class,
                  order))
          .isEqualByComparingTo("120000.00");
      assertThat(
              db.queryForObject(
                  "select ordered_quantity from sales_orders where id=?",
                  java.math.BigDecimal.class,
                  order))
          .isEqualByComparingTo("100");
      assertThat(
              db.queryForObject(
                  "select delivery_site from sales_orders where id=?", String.class, order))
          .isEqualTo("Existing site");
      assertThat(
              db.queryForObject(
                  "select count(*) from sales_orders where id=? and branch_id is null and"
                      + " project_id is null and material_id is null and delivery_site_id is null",
                  Integer.class,
                  order))
          .isEqualTo(1);
    }
  }
}
