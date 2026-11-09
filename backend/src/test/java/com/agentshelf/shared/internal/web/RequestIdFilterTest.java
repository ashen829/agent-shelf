package com.agentshelf.shared.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void generatesIdWhenHeaderIsMissing() throws Exception {
    Result result = run(null);

    assertThat(result.idInChain()).isNotBlank().isEqualTo(result.responseHeader());
    assertThat(result.idInChain()).matches("[0-9a-f-]{36}");
  }

  @Test
  void keepsValidIncomingId() throws Exception {
    Result result = run("abc-123-XYZ-789");

    assertThat(result.idInChain()).isEqualTo("abc-123-XYZ-789");
    assertThat(result.responseHeader()).isEqualTo("abc-123-XYZ-789");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "abc-123\r\nfake-log-line",
        "<script>alert(1)</script>",
        "short",
        "a-very-long-id-that-goes-on-and-on-and-on-well-past-sixty-four-characters"
      })
  void replacesUnsafeIncomingId(String unsafe) throws Exception {
    Result result = run(unsafe);

    assertThat(result.idInChain()).isNotEqualTo(unsafe).matches("[0-9a-f-]{36}");
    assertThat(result.responseHeader()).isEqualTo(result.idInChain());
  }

  @Test
  void clearsLogContextAfterRequest() throws Exception {
    run("abc-123-XYZ-789");

    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }

  private Result run(@Nullable String header) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/anything");
    if (header != null) {
      request.addHeader(RequestIdFilter.HEADER, header);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> seen = new AtomicReference<>("");
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            String id = MDC.get(RequestIdFilter.MDC_KEY);
            seen.set(id == null ? "" : id);
          }
        };

    filter.doFilter(request, response, chain);

    String responseHeader = response.getHeader(RequestIdFilter.HEADER);
    return new Result(seen.get(), responseHeader == null ? "" : responseHeader);
  }

  private record Result(String idInChain, String responseHeader) {}
}
