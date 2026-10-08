package com.finance.server.application.port;

import com.finance.domain.*;
import java.util.List;
import java.util.Optional;

public interface LedgerPort {
  List<Product> products();

  Optional<Product> product(String id);

  void saveProduct(Product product);

  List<Movement> movements(Period period, String productId);

  Optional<Movement> movement(String id);

  List<Movement> allMovements();

  int insert(List<Movement> movements);

  int updateClassifications(List<Movement> movements);

  /** Backward-compatible manual classification entry point. */
  void classify(String id, String category, Movement.Kind kind);

  void deleteMovement(String id);

  void deleteProduct(String id);
}
