package com.agentshelf.catalog.internal.persistence;

/** A category and how many active products are in it. */
public record CategoryCount(String name, long count) {}
