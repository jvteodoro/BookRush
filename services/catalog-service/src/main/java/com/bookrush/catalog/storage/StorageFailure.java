package com.bookrush.catalog.storage;

public final class StorageFailure extends RuntimeException {
  public enum Kind { NOT_FOUND, CONFLICT, UNAVAILABLE }
  private final Kind kind;
  public StorageFailure(Kind kind) { super("Object storage operation failed: "+kind); this.kind=kind; }
  public Kind kind() { return kind; }
}
