package com.agentshelf.catalog.internal.web;

import com.agentshelf.catalog.internal.importing.ImportService;
import com.agentshelf.identity.CurrentUser;
import com.agentshelf.identity.IdentityApi;
import com.agentshelf.shared.DomainException;
import com.agentshelf.shared.ProblemResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(path = "/api/v1/imports", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Imports", description = "CSV price list imports, run in the background")
@ApiResponse(
    responseCode = "401",
    description = "Not signed in",
    content =
        @Content(
            mediaType = ProblemResponses.MEDIA_TYPE,
            schema = @Schema(implementation = ProblemDetail.class)))
class ImportController {

  private final ImportService imports;
  private final IdentityApi identity;

  ImportController(ImportService imports, IdentityApi identity) {
    this.imports = imports;
    this.identity = identity;
  }

  @Operation(
      operationId = "uploadImport",
      summary = "Upload a CSV file (UTF-8, at most 10 MB and 20,000 rows)")
  @ApiResponse(responseCode = "201", description = "Checked and stored; match its columns next")
  @ApiResponse(
      responseCode = "413",
      description = "File larger than 10 MB",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Not a usable CSV file (encoding, rows, quotes)",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  UploadResponse upload(@RequestPart("file") MultipartFile file) {
    if (file.isEmpty()) {
      throw new DomainException("The file is empty.");
    }
    CurrentUser user = user();
    String name = file.getOriginalFilename();
    var job = imports.upload(user, name == null ? "upload.csv" : name, bytes(file));
    return new UploadResponse(
        ImportResponse.from(job), ImportPreviewResponse.from(imports.preview(user, job.id())));
  }

  @Operation(operationId = "listImports", summary = "Import history, newest first")
  @ApiResponse(responseCode = "200", description = "A page of imports")
  @GetMapping
  ImportPage list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ImportPage.from(imports.list(user(), page, size));
  }

  @Operation(operationId = "getImport", summary = "Status, progress and results of one import")
  @ApiResponse(responseCode = "200", description = "The import")
  @ApiResponse(
      responseCode = "404",
      description = "No such import for this supplier",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping("/{id}")
  ImportResponse get(@PathVariable UUID id) {
    return ImportResponse.from(imports.get(user(), id));
  }

  @Operation(
      operationId = "getImportPreview",
      summary = "Headers, first rows and a suggested column mapping")
  @ApiResponse(responseCode = "200", description = "The preview")
  @ApiResponse(
      responseCode = "404",
      description = "No such import for this supplier",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping("/{id}/preview")
  ImportPreviewResponse preview(@PathVariable UUID id) {
    return ImportPreviewResponse.from(imports.preview(user(), id));
  }

  @Operation(
      operationId = "startImport",
      summary = "Save the column mapping and run the import in the background")
  @ApiResponse(responseCode = "202", description = "Queued")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid request",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Another import of this supplier is still running",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Mapping incomplete, or the import was already started",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping("/{id}/start")
  @ResponseStatus(HttpStatus.ACCEPTED)
  ImportResponse start(@PathVariable UUID id, @Valid @RequestBody StartImportRequest body) {
    return ImportResponse.from(
        imports.start(
            user(),
            id,
            body.mapping().toDomain(),
            body.decimalSeparator().charAt(0),
            body.completeCatalog()));
  }

  @Operation(operationId = "listImportErrors", summary = "Rows that could not be imported")
  @ApiResponse(responseCode = "200", description = "A page of row errors")
  @ApiResponse(
      responseCode = "404",
      description = "No such import for this supplier",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping("/{id}/errors")
  ImportErrorPage errors(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
    return ImportErrorPage.from(imports.errors(user(), id, page, size));
  }

  @Operation(
      operationId = "downloadImportErrors",
      summary = "The failed rows as CSV, with an Error column, to fix and upload again")
  @ApiResponse(
      responseCode = "200",
      description = "CSV file",
      content =
          @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
  @ApiResponse(
      responseCode = "404",
      description = "No such import for this supplier",
      content =
          @Content(
              mediaType = ProblemResponses.MEDIA_TYPE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping(path = "/{id}/errors.csv", produces = "text/csv")
  ResponseEntity<byte[]> errorReport(@PathVariable UUID id) {
    byte[] csv = imports.errorReport(user(), id);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("rows-with-errors.csv").build().toString())
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .body(csv);
  }

  private CurrentUser user() {
    return identity.currentUser();
  }

  private static byte[] bytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
