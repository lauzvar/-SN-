package com.robotsn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class HttpsConfigurationTest {
  @Test
  void redirectsToConfiguredOriginWithoutTrustingHost() throws Exception {
    var filter = new HttpsConfiguration().httpsOnly("https://192.168.110.8:8443").getFilter();
    var req = new MockHttpServletRequest("GET", "/r/LBR-2609-P-0001");
    req.setServerName("evil.example");
    req.setQueryString("x=1");
    var res = new MockHttpServletResponse();
    filter.doFilter(req, res, new MockFilterChain());
    assertEquals(308, res.getStatus());
    assertEquals("https://192.168.110.8:8443/r/LBR-2609-P-0001?x=1", res.getHeader("Location"));
    assertNull(req.getSession(false));
  }

  @Test
  void rejectsPlaintextMutationAndPassesTls() throws Exception {
    var filter = new HttpsConfiguration().httpsOnly("https://localhost:8443").getFilter();
    var req = new MockHttpServletRequest("POST", "/login");
    var res = new MockHttpServletResponse();
    var chain = new MockFilterChain();
    filter.doFilter(req, res, chain);
    assertEquals(400, res.getStatus());
    assertNull(chain.getRequest());
    var secure = new MockHttpServletRequest("POST", "/api/import/robots/confirm");
    secure.setSecure(true);
    var secureChain = new MockFilterChain();
    filter.doFilter(secure, new MockHttpServletResponse(), secureChain);
    assertNotNull(secureChain.getRequest());
  }

  @Test
  void rejectsInvalidTlsOriginAndPortCollision() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new HttpsConfiguration().httpsOnly("http://localhost:8082"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new HttpsConfiguration().httpsOnly("https://user:password@localhost:8443/path"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new HttpsConfiguration().redirectConnector(8082, 8082));
  }
}
