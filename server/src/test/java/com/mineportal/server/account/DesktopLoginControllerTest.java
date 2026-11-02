package com.mineportal.server.account;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.mineportal.server.connection.McConnectionManager;
import com.mineportal.server.desktop.DesktopConnectionManager;
import com.mineportal.server.servers.ServerRegistry;
import com.mineportal.server.ws.EventBroadcaster;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DesktopLoginControllerTest {
  private AuthController controller() {
    return new AuthController(
        mock(AccountSessionManager.class),
        mock(EventBroadcaster.class),
        mock(ServerRegistry.class),
        mock(McConnectionManager.class),
        mock(DesktopConnectionManager.class));
  }

  @Test
  void claimJsonIsAcceptedButAnotherSessionOrProofCannotClaim() throws Exception {
    AuthController controller = controller();
    Map<?, ?> started = (Map<?, ?>) controller.startDesktopLogin("app").getBody();
    String path = "/api/account/desktop-login/" + started.get("id") + "/claim";
    var mvc = MockMvcBuilders.standaloneSetup(controller).build();
    String json = "{\"proof\":\"" + started.get("proof") + "\"}";
    mvc.perform(
            post(path)
                .requestAttr("sid", "app")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(false))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().doesNotExist("Set-Cookie"));
    mvc.perform(
            post(path)
                .requestAttr("sid", "attacker")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isBadRequest())
        .andExpect(header().doesNotExist("Set-Cookie"));
    mvc.perform(
            post(path)
                .requestAttr("sid", "app")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"proof\":null}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void confirmationCannotBeFramedOrSendItsNonceToAnotherOrigin() {
    AuthController controller = controller();
    Map<?, ?> started = (Map<?, ?>) controller.startDesktopLogin("app").getBody();
    var page = controller.confirmDesktopLogin((String) started.get("id"), "browser");
    assertEquals("no-referrer", page.getHeaders().getFirst("Referrer-Policy"));
    assertTrue(
        page.getHeaders().getFirst("Content-Security-Policy").contains("frame-ancestors 'none'"));
    assertTrue(
        page.getHeaders().getFirst("Content-Security-Policy").contains("form-action 'self'"));
    assertFalse(page.getBody().toString().contains(started.get("proof").toString()));
  }

  @Test
  void completedHandoffSetsHttpOnlyCookieWithoutExposingSessionInJsonAndCannotReplay()
      throws Exception {
    AuthController controller = controller();
    Map<?, ?> started = (Map<?, ?>) controller.startDesktopLogin("app").getBody();
    String id = (String) started.get("id");
    var attempts = (DesktopLoginAttempts) ReflectionTestUtils.getField(controller, "desktopLogins");
    String confirmation = attempts.prepare(id, "browser");
    attempts.authorize(id, "browser", confirmation);
    attempts.complete(id, "rotated-session");
    var mvc = MockMvcBuilders.standaloneSetup(controller).build();
    String path = "/api/account/desktop-login/" + id + "/claim";
    String json = "{\"proof\":\"" + started.get("proof") + "\"}";
    mvc.perform(
            post(path)
                .requestAttr("sid", "app")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"ready\":true}"))
        .andExpect(cookie().value("sid", "rotated-session"))
        .andExpect(cookie().httpOnly("sid", true))
        .andExpect(cookie().path("sid", "/"));
    mvc.perform(
            post(path)
                .requestAttr("sid", "app")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isBadRequest())
        .andExpect(header().doesNotExist("Set-Cookie"));
  }
}
