package com.bookrush.subscription;

import com.bookrush.platform.core.TechnicalIdentity;
import java.util.UUID;

/** Provider boundary: produce a verified normalized delivery, or a decline with no commercial mutation. */
interface BillingProvider {
  record Delivery(UUID externalEventId, BillingInbox.Event event) {}

  Delivery checkout(TechnicalIdentity owner, UUID requestId);
}
