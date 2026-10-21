package com.yfctransport.customers;

import java.util.UUID;

public interface CustomerOpening {
  UUID open(String name, String mobile, String gstin);
}
