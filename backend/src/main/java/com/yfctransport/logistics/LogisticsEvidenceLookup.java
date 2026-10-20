package com.yfctransport.logistics;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class LogisticsEvidenceLookup implements LogisticsEvidence {
  private final TripExpenseContext trips;
  private final JdbcTemplate db;

  LogisticsEvidenceLookup(TripExpenseContext trips, JdbcTemplate db) {
    this.trips = trips;
    this.db = db;
  }

  public void lockTrip(UUID id) {
    trips.lock(id);
  }

  public void lockIncident(UUID id) {
    var rows =
        db.query(
            "select trip_id from trip_incidents where id=?",
            (r, n) -> r.getObject(1, UUID.class),
            id);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
    trips.lock(rows.getFirst());
  }
}
