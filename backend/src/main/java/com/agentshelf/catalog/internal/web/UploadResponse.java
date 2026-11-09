package com.agentshelf.catalog.internal.web;

/** The new import and what is needed to match its columns. */
record UploadResponse(ImportResponse importJob, ImportPreviewResponse preview) {}
