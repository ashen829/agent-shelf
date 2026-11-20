package com.agentshelf.catalog.internal.importing;

import com.agentshelf.shared.DomainException;

/**
 * The uploaded file can't be imported at all (type, encoding, size, no rows). Shown to the
 * supplier.
 */
public class InvalidImportFileException extends DomainException {

  private static final long serialVersionUID = 1L;

  public InvalidImportFileException(String message) {
    super(message);
  }
}
