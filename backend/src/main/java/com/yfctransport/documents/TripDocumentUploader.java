package com.yfctransport.documents;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/** Public SPI allowing the driver portal to attach and list weighbridge slips / PODs on assigned trips. */
public interface TripDocumentUploader {
  Map<String, Object> uploadTripDocument(UUID documentId, UUID tripId, MultipartFile file);

  List<Map<String, Object>> listTripDocuments(UUID tripId);
}
