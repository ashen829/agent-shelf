package com.agentshelf.enrichment.internal.web;

import com.agentshelf.enrichment.internal.application.EnrichmentService;
import com.agentshelf.identity.CurrentUser;
import com.agentshelf.identity.IdentityApi;
import com.agentshelf.shared.ProblemResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/enrichment", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "AI suggestions", description = "AI cleanup of the catalog, run in the background")
@ApiResponse(
    responseCode = "401",
    description = "Not signed in",
    content =
        @Content(
            mediaType = ProblemResponses.MEDIA_TYPE,
            schema = @Schema(implementation = ProblemDetail.class)))
class EnrichmentController {

  private final EnrichmentService enrichment;
  private final IdentityApi identity;

  EnrichmentController(EnrichmentService enrichment, IdentityApi identity) {
    this.enrichment = enrichment;
    this.identity = identity;
  }

  @Operation(
      operationId = "getEnrichmentOverview",
      summary = "Whether AI suggestions are available, and the latest job")
  @ApiResponse(responseCode = "200", description = "The overview")
  @GetMapping
  EnrichmentOverview overview() {
    return new EnrichmentOverview(
        enrichment.enabled(),
        enrichment.latest(user()).map(EnrichmentJobResponse::from).orElse(null));
  }

  @Operation(
      operationId = "startEnrichment",
      summary = "Ask AI to suggest fixes for the active products (runs in the background)")
  @ApiResponse(responseCode = "202", description = "Queued")
  @ApiResponse(
      responseCode = "409",
      description = "A job is already running for this supplier",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "AI suggestions are not switched on",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/jobs")
  @ResponseStatus(HttpStatus.ACCEPTED)
  EnrichmentJobResponse start() {
    return EnrichmentJobResponse.from(enrichment.start(user()));
  }

  @Operation(operationId = "listEnrichmentJobs", summary = "AI suggestion jobs, newest first")
  @ApiResponse(responseCode = "200", description = "A page of jobs")
  @GetMapping("/jobs")
  EnrichmentJobPage list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return EnrichmentJobPage.from(enrichment.list(user(), page, size));
  }

  @Operation(operationId = "getEnrichmentJob", summary = "Status and results of one job")
  @ApiResponse(responseCode = "200", description = "The job")
  @ApiResponse(
      responseCode = "404",
      description = "No such job for this supplier",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping("/jobs/{id}")
  EnrichmentJobResponse get(@PathVariable UUID id) {
    return EnrichmentJobResponse.from(enrichment.get(user(), id));
  }

  @Operation(
      operationId = "listEnrichmentSuggestions",
      summary = "One job's suggestions, grouped by product")
  @ApiResponse(responseCode = "200", description = "A page of suggestions")
  @ApiResponse(
      responseCode = "404",
      description = "No such job for this supplier",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping("/jobs/{id}/suggestions")
  SuggestionPage suggestions(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
    return SuggestionPage.from(enrichment.suggestions(user(), id, page, size));
  }

  private CurrentUser user() {
    return identity.currentUser();
  }
}
