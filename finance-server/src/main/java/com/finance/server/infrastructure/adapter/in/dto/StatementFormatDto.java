package com.finance.server.infrastructure.adapter.in.dto;

import com.finance.statements.StatementFormat;

public record StatementFormatDto(StatementFormat id, String label, String extension) {
  public static StatementFormatDto from(StatementFormat format) {
    return new StatementFormatDto(format, format.label(), format.extension());
  }
}
