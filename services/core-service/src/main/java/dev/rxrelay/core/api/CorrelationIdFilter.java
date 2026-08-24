package dev.rxrelay.core.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
  public static final String HEADER = "X-Request-Id";
  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String supplied = request.getHeader(HEADER);
    String requestId =
        supplied != null && SAFE_REQUEST_ID.matcher(supplied).matches()
            ? supplied
            : UUID.randomUUID().toString();
    response.setHeader(HEADER, requestId);
    try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
      request.setAttribute(HEADER, requestId);
      chain.doFilter(request, response);
    }
  }
}
