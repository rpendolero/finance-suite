package com.finance.server.infrastructure.adapter.in.rest;

import com.finance.domain.*;
import com.finance.server.application.port.FinanceQueries;
import com.finance.server.application.port.LedgerPort;
import com.finance.server.application.port.MovementSearchPort;
import java.math.BigDecimal;
import com.finance.server.application.service.*;
import java.util.List;
import com.finance.server.infrastructure.adapter.in.mapper.ProductMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FinanceController {
  private final FinanceQueries queries;


}
