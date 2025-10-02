package com.graph.graphservice.config;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;

import org.hibernate.engine.jdbc.internal.FormatStyle;

public class HibernatePrettySqlFormatter implements MessageFormattingStrategy {

  @Override
  public String formatMessage(int connectionId, String now, long elapsed, String category,
                              String prepared, String sql, String url) {
    if (sql == null || sql.trim().equals("")) {
      return "";
    }
    return "SQL | " + now +
        " | took " + elapsed + "ms | " + category + " | \n" +
        FormatStyle.BASIC.getFormatter().format(sql);
  }
}
