package com.yfctransport.suppliers;

import java.util.UUID;

public interface SupplierOpening {
  UUID open(String name, String gstin, String mobile, String email, String address);
}
