package com.scheduler.service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class TargetUrlGuard {

  private final HostResolver hostResolver;

  public TargetUrlGuard() {
    this(host -> InetAddress.getAllByName(host));
  }

  private TargetUrlGuard(HostResolver hostResolver) {
    this.hostResolver = hostResolver;
  }

  public static TargetUrlGuard withResolver(HostResolver hostResolver) {
    return new TargetUrlGuard(hostResolver);
  }

  public void validateAllowed(String targetUrl) {
    URI uri = parseUri(targetUrl);
    String scheme = uri.getScheme();
    String host = uri.getHost();

    if (scheme == null
        || host == null
        || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
      throw invalidTargetUrl("Target URL must use http or https");
    }

    if (uri.getUserInfo() != null) {
      throw invalidTargetUrl("Target URL must not contain user info");
    }

    String normalizedHost = normalizeHost(host);
    if (isBlockedHostname(normalizedHost)) {
      throw invalidTargetUrl("Target URL host is not allowed");
    }

    InetAddress[] addresses = resolve(normalizedHost);
    if (addresses.length == 0) {
      throw invalidTargetUrl("Target URL host could not be resolved");
    }

    for (InetAddress address : addresses) {
      if (isBlockedAddress(address)) {
        throw invalidTargetUrl("Target URL resolves to a private or local address");
      }
    }
  }

  private URI parseUri(String targetUrl) {
    try {
      return new URI(targetUrl);
    } catch (URISyntaxException e) {
      throw invalidTargetUrl("Target URL is invalid");
    }
  }

  private String normalizeHost(String host) {
    String normalized = host.toLowerCase();
    return normalized.endsWith(".") ? normalized.substring(0, normalized.length() - 1) : normalized;
  }

  private boolean isBlockedHostname(String host) {
    return host.equals("localhost")
        || host.endsWith(".localhost")
        || host.equals("localhost.localdomain")
        || host.endsWith(".localdomain")
        || host.endsWith(".local");
  }

  private InetAddress[] resolve(String host) {
    try {
      return hostResolver.resolve(host);
    } catch (UnknownHostException e) {
      throw invalidTargetUrl("Target URL host could not be resolved");
    }
  }

  private boolean isBlockedAddress(InetAddress address) {
    return address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()
        || isBlockedIpv4Address(address)
        || isBlockedIpv6Address(address);
  }

  private boolean isBlockedIpv4Address(InetAddress address) {
    if (!(address instanceof Inet4Address)) {
      return false;
    }

    byte[] bytes = address.getAddress();
    int first = Byte.toUnsignedInt(bytes[0]);
    int second = Byte.toUnsignedInt(bytes[1]);

    return first == 0
        || first == 10
        || first == 127
        || (first == 169 && second == 254)
        || (first == 172 && second >= 16 && second <= 31)
        || (first == 192 && second == 168)
        || (first == 100 && second >= 64 && second <= 127)
        || (first == 198 && (second == 18 || second == 19));
  }

  private boolean isBlockedIpv6Address(InetAddress address) {
    if (!(address instanceof Inet6Address)) {
      return false;
    }

    byte[] bytes = address.getAddress();
    int first = Byte.toUnsignedInt(bytes[0]);
    int second = Byte.toUnsignedInt(bytes[1]);

    return (first & 0xfe) == 0xfc || (first == 0xfe && (second & 0xc0) == 0x80);
  }

  private ResponseStatusException invalidTargetUrl(String reason) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
  }

  @FunctionalInterface
  public interface HostResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }
}
