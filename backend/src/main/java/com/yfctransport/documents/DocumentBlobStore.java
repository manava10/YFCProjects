package com.yfctransport.documents;

import java.util.UUID;

interface DocumentBlobStore {
  String backendName();

  String put(UUID id, String targetType, String filename, String mediaType, byte[] bytes);

  byte[] get(String storageKey);

  void deleteQuietly(String storageKey);
}
