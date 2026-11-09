package com.agentshelf.shared.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentshelf.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ErrorTestController.class})
class ErrorHandlingIT {

  @Autowired private MockMvcTester mvc;

  @ParameterizedTest
  @CsvSource({
    "/test/errors/not-found, 404, Product 42 was not found.",
    "/test/errors/conflict,  409, SKU ABC-1 already exists.",
    "/test/errors/rule,      422, Quantity is below the minimum order.",
  })
  void mapsOurExceptionsToProblemDetail(String path, int status, String detail) {
    MvcTestResult result = mvc.get().uri(path).exchange();

    assertProblem(result, status);
    assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(detail);
  }

  @Test
  void staleUpdateIsConflictWithoutInternalDetails() throws Exception {
    MvcTestResult result = mvc.get().uri("/test/errors/stale").exchange();

    assertProblem(result, 409);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo(GlobalExceptionHandler.STALE_MESSAGE);
    assertThat(result.getResponse().getContentAsString()).doesNotContain("version 3");
  }

  @Test
  void unexpectedErrorHidesStackTraceAndMessage() throws Exception {
    MvcTestResult result = mvc.get().uri("/test/errors/boom").exchange();

    assertProblem(result, 500);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo(GlobalExceptionHandler.UNEXPECTED_MESSAGE);
    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .doesNotContain(ErrorTestController.SECRET)
        .doesNotContain("IllegalStateException")
        .doesNotContain("at com.agentshelf");
  }

  @Test
  void invalidBodyListsFieldErrors() {
    MvcTestResult result =
        mvc.post()
            .uri("/test/errors/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"\", \"quantity\": 0}")
            .exchange();

    assertProblem(result, 400);
    assertThat(result).bodyJson().extractingPath("$.errors.length()").isEqualTo(2);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.errors[*].field")
        .asArray()
        .containsExactlyInAnyOrder("name", "quantity");
  }

  @Test
  void malformedJsonIsBadRequest() {
    MvcTestResult result =
        mvc.post()
            .uri("/test/errors/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{not json")
            .exchange();

    assertProblem(result, 400);
  }

  @Test
  void unknownRouteIsNotFound() {
    assertProblem(mvc.get().uri("/api/v1/does-not-exist").exchange(), 404);
  }

  @Test
  void problemCarriesIncomingRequestId() {
    MvcTestResult result =
        mvc.get()
            .uri("/test/errors/not-found")
            .header(RequestIdFilter.HEADER, "client-req-0001")
            .exchange();

    assertThat(result).bodyJson().extractingPath("$.requestId").isEqualTo("client-req-0001");
    assertThat(result).headers().hasValue(RequestIdFilter.HEADER, "client-req-0001");
  }

  private static void assertProblem(MvcTestResult result, int status) {
    assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    String headerId = result.getResponse().getHeader(RequestIdFilter.HEADER);
    assertThat(headerId).isNotBlank();
    assertThat(result).bodyJson().extractingPath("$.requestId").isEqualTo(headerId);
  }
}
