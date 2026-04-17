package com.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
    properties = {"auth.jwt.secret=01234567890123456789012345678901", "auth.jwt.issuer=Orchard"})
class GatewayApplicationTests {

  @Test
  void contextLoads() {}
}
