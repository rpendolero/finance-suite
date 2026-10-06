package com.finance.server.application.port;

import com.finance.domain.*;
import java.util.List;
import java.util.Optional;

public interface LedgerPort {
  List<Product> products();

  Optional<Product> product(String id);

  void saveProduct(Product product);

  List<Movement> movements(Period period, String productId);

  int insert(List<Movement> movements);

  void classify(String id, String category, Movement.Kind kind);

  void deleteProduct(String id);
}
