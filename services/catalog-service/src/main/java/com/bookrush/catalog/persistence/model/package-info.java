/**
 * Catalog persistence mappings. UUID foreign-key fields own writes; lazy associations
 * are read-only navigation and must be refreshed after changing an FK on a managed object.
 * Entities are not API DTOs. No cascading deletes or unbounded inverse collections.
 * Equality remains object identity except for immutable-value composite key comparisons.
 */
package com.bookrush.catalog.persistence.model;
