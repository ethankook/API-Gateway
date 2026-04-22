package com.scheduler.UnitTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scheduler.service.TargetUrlGuard;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TargetUrlGuardTests {

  @Test
  void allowsPublicHttpUrl() {
    TargetUrlGuard guard = guardResolvingTo("93.184.216.34");

    guard.validateAllowed("https://example.com/callback");
  }

  @Test
  void rejectsUserInfo() {
    assertBlocked("https://user:pass@example.com/callback");
  }

  @Test
  void rejectsLocalhostName() {
    assertBlocked("http://localhost/callback");
  }

  @Test
  void rejectsLoopbackAddress() {
    assertBlocked("http://127.0.0.1/callback");
  }

  @Test
  void rejectsPrivateIpv4Address() {
    assertBlocked("http://10.0.0.5/callback");
    assertBlocked("http://172.16.0.5/callback");
    assertBlocked("http://192.168.1.5/callback");
  }

  @Test
  void rejectsMetadataAddress() {
    assertBlocked("http://169.254.169.254/latest/meta-data");
  }

  @Test
  void rejectsIpv6LoopbackAndUniqueLocalAddresses() {
    assertBlocked("http://[::1]/callback");
    assertBlocked("http://[fc00::1]/callback");
  }

  @Test
  void rejectsHostThatResolvesToPrivateAddress() {
    TargetUrlGuard guard = guardResolvingTo("192.168.1.10");

    assertThatThrownBy(() -> guard.validateAllowed("https://public-name.example/callback"))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  private void assertBlocked(String url) {
    assertThatThrownBy(
            () -> TargetUrlGuard.withResolver(InetAddress::getAllByName).validateAllowed(url))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  private TargetUrlGuard guardResolvingTo(String address) {
    return TargetUrlGuard.withResolver(host -> new InetAddress[] {InetAddress.getByName(address)});
  }
}
