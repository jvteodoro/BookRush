package com.bookrush.ingestion.source;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Validates every source URL and redirect; it is intentionally separate from the trusted S3 client. */
public final class SourceUrlPolicy {
  private final Set<String> allowedHosts;
  private final boolean allowPrivateAddresses;
  private final int maxRedirects;

  public SourceUrlPolicy(Set<String> allowedHosts, boolean allowPrivateAddresses, int maxRedirects) {
    this.allowedHosts = allowedHosts.stream().map(h -> h.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    this.allowPrivateAddresses = allowPrivateAddresses;
    this.maxRedirects = maxRedirects;
    if (maxRedirects < 0 || maxRedirects > 10) throw new IllegalArgumentException("redirect limit must be 0..10");
  }

  public URI validate(URI candidate, int redirectCount) {
    if (candidate == null || candidate.getScheme() == null
        || !Set.of("https", "http").contains(candidate.getScheme().toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("source URL must use HTTP(S)");
    }
    if (candidate.getUserInfo() != null || candidate.getFragment() != null) {
      throw new IllegalArgumentException("source URL cannot contain credentials or fragment");
    }
    if (redirectCount > maxRedirects) throw new IllegalArgumentException("redirect limit exceeded");
    var host = candidate.getHost();
    if (host == null || !allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("source host is not allowlisted");
    }
    if (!allowPrivateAddresses) {
      try {
        for (var address : InetAddress.getAllByName(host)) {
          if (isPrivate(address)) throw new IllegalArgumentException("private or link-local source address");
        }
      } catch (java.net.UnknownHostException e) {
        throw new IllegalArgumentException("source host cannot be resolved", e);
      }
    }
    return candidate;
  }

  private static boolean isPrivate(InetAddress address) {
    if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
        || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
    if (address instanceof Inet4Address v4) {
      int value = java.nio.ByteBuffer.wrap(v4.getAddress()).getInt();
      return (value & 0xff000000) == 0x0a000000
          || (value & 0xfff00000) == 0xac100000
          || (value & 0xffff0000) == 0xc0a80000
          || (value & 0xffff0000) == 0xa9fe0000;
    }
    return address instanceof Inet6Address && address.getHostAddress().toLowerCase(Locale.ROOT).startsWith("fc");
  }
}
