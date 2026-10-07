package com.finance.server.infrastructure.config;

import com.finance.server.application.port.*;
import com.finance.server.application.port.FinanceQueries;
import com.finance.server.application.service.*;
import com.finance.server.infrastructure.adapter.in.mcp.FinanceTools;
import java.time.Clock;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.*;

@Configuration
public class ApplicationConfig {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  FinancialSummaryService summaries(LedgerPort ledger) {
    return new FinancialSummaryService(ledger);
  }

  @Bean
  MerchantPatternService patterns(LedgerPort ledger) {
    return new MerchantPatternService(ledger);
  }

  @Bean
  CardExposureService cardExposure(LedgerPort ledger) {
    return new CardExposureService(ledger);
  }

  @Bean
  LiquidityForecastService forecasts(LedgerPort ledger, Clock clock) {
    return new LiquidityForecastService(ledger, clock);
  }

  @Bean
  DataQualityService quality(LedgerPort ledger, Clock clock) {
    return new DataQualityService(ledger, clock);
  }

  @Bean
  FinanceQueries financeQueries(
      LedgerPort ledger,
      FinancialSummaryService summaries,
      MerchantPatternService patterns,
      CardExposureService exposure,
      LiquidityForecastService forecasts,
      DataQualityService quality) {
    return new FinanceService(ledger, summaries, patterns, exposure, forecasts, quality);
  }

  @Bean
  MovementExportService movementExport(MovementSearchPort movementSearch) {
    return new MovementExportService(movementSearch);
  }

  @Bean
  DashboardAnalysisService dashboardAnalysis(LedgerPort ledger) {
    return new DashboardAnalysisService(ledger);
  }

  @Bean
  CategoryCatalogService categoryCatalog() {
    return new CategoryCatalogService();
  }

  @Bean
  MerchantNormalizationService merchantNormalization() {
    return new MerchantNormalizationService();
  }

  @Bean
  MovementKindDetectionService movementKindDetection() {
    return new MovementKindDetectionService();
  }

  @Bean
  MovementClassificationService classification(
      MerchantNormalizationService merchants,
      CategoryCatalogService categories,
      MovementKindDetectionService kindDetection) {
    return new MovementClassificationService(merchants, categories, kindDetection);
  }

  @Bean
  ClassificationManagementService classificationManagement(
      LedgerPort ledger,
      SettingsPort settings,
      MovementClassificationService classifier,
      CategoryCatalogService categories,
      MerchantNormalizationService merchants) {
    return new ClassificationManagementService(
        ledger, settings, classifier, categories, merchants);
  }

  @Bean
  ImportService importService(
      LedgerPort ledger,
      StatementParserPort parser,
      SettingsPort settings,
      MovementClassificationService classification) {
    return new ImportService(settings, ledger, parser, classification);
  }

  @Bean
  MonthlyTrendService trends(FinanceQueries queries) {
    return new MonthlyTrendService(queries);
  }

  @Bean
  MerchantSpendingService spending(LedgerPort ledger) {
    return new MerchantSpendingService(ledger);
  }

  @Bean
  BudgetAnalysisService budgets(FinanceQueries queries, SettingsPort settings) {
    return new BudgetAnalysisService(queries, settings);
  }

  @Bean
  ReconciliationService reconciliation(LedgerPort ledger) {
    return new ReconciliationService(ledger);
  }

  @Bean
  RecurringIncreaseService increases(FinanceQueries queries, LedgerPort ledger) {
    return new RecurringIncreaseService(queries, ledger);
  }

  @Bean
  CashFlowService cashFlow(LedgerPort ledger) {
    return new CashFlowService(ledger);
  }

  @Bean
  ExtendedAnalysisService extendedAnalysis(
      MonthlyTrendService trends,
      MerchantSpendingService merchants,
      BudgetAnalysisService budgets,
      ReconciliationService reconciliation,
      RecurringIncreaseService increases,
      CashFlowService cashFlow) {
    return new ExtendedAnalysisService(
        trends, merchants, budgets, reconciliation, increases, cashFlow);
  }

  @Bean
  ToolCallbackProvider tools(
      FinanceTools tools,
      com.finance.server.infrastructure.adapter.in.mcp.ExtendedTools extended,
      com.finance.server.infrastructure.adapter.in.mcp.FinancialReportTools report) {
    return MethodToolCallbackProvider.builder().toolObjects(tools, extended, report).build();
  }
}
