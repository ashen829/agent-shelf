package com.agentshelf.catalog.internal.importing;

import com.agentshelf.shared.DomainException;

/** The uploaded file can't be imported at all (encoding, size, no rows). Shown to the supplier. */
public class InvalidCsvException extends DomainException {

  private static final long serialVersionUID = 1L;

  public InvalidCsvException(String message) {
    super(message);
  }
}
