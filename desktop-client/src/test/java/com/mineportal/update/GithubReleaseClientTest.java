package com.mineportal.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GithubReleaseClientTest {

  @Test
  void latestApiStaysOnTheReleasesEndpoint() {
    assertEquals(
        URI.create("https://api.github.com/repos/rdyjun/mineportal/releases/latest"),
        GithubReleaseClient.latestApi("rdyjun/mineportal").orElseThrow());
    assertTrue(GithubReleaseClient.latestApi("../rdyjun/mineportal").isEmpty());
    assertTrue(GithubReleaseClient.latestApi("rdyjun/mineportal/releases/latest").isEmpty());
  }

  @Test
  void readsASuccessfulReleaseAndSendsNoCredentials() throws Exception {
    HttpClient http = mock(HttpClient.class);
    HttpResponse<InputStream> response = response(200, PUBLISHED.getBytes(StandardCharsets.UTF_8));
    when(http.<InputStream>send(any(HttpRequest.class), any())).thenReturn(response);
    GithubReleaseClient client =
        new GithubReleaseClient(
            http,
            URI.create("https://api.github.com/repos/rdyjun/mineportal/releases/latest"),
            "MinePortalDesktop/1.1.1",
            Duration.ofSeconds(8));

    Optional<ReleaseInfo> release = client.fetchLatest();

    assertEquals("1.0.0", release.orElseThrow().tagName());
    assertFalse(
        release.orElseThrow().assets().stream().anyMatch(asset -> asset.name().endsWith(".dmg")));
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).send(captor.capture(), any());
    HttpRequest request = captor.getValue();
    assertEquals("GET", request.method());
    assertEquals(
        "MinePortalDesktop/1.1.1", request.headers().firstValue("User-Agent").orElseThrow());
    assertEquals(
        "application/vnd.github+json", request.headers().firstValue("Accept").orElseThrow());
    assertTrue(request.headers().firstValue("Authorization").isEmpty());
    assertEquals(Duration.ofSeconds(8), request.timeout().orElseThrow());
  }

  @Test
  void rateLimitTimeoutAndBrokenBodiesAreEmpty() throws Exception {
    assertTrue(fetch(403, "{\"message\":\"API rate limit exceeded\"}").isEmpty());
    assertTrue(fetch(429, "").isEmpty());
    assertTrue(fetch(500, PUBLISHED).isEmpty());
    assertTrue(fetch(200, "not-json").isEmpty());
    assertTrue(fetch(200, "").isEmpty());

    HttpClient http = mock(HttpClient.class);
    when(http.<InputStream>send(any(HttpRequest.class), any()))
        .thenThrow(new HttpTimeoutException("slow"));
    assertTrue(client(http).fetchLatest().isEmpty());

    when(http.<InputStream>send(any(HttpRequest.class), any()))
        .thenThrow(new IOException("offline"));
    assertTrue(client(http).fetchLatest().isEmpty());

    when(http.<InputStream>send(any(HttpRequest.class), any()))
        .thenThrow(new InterruptedException("stop"));
    assertTrue(client(http).fetchLatest().isEmpty());
    assertTrue(Thread.currentThread().isInterrupted());
    Thread.interrupted();
  }

  @Test
  void oversizedBodiesAreIgnored() throws Exception {
    HttpClient http = mock(HttpClient.class);
    byte[] huge = new byte[GithubReleaseClient.MAX_BODY_BYTES + 1];
    HttpResponse<InputStream> response = response(200, huge);
    when(http.<InputStream>send(any(HttpRequest.class), any())).thenReturn(response);
    assertTrue(client(http).fetchLatest().isEmpty());
  }

  @Test
  void checkerTreatsThePublishedReleaseAsCurrentAndSwallowsSourceFailures() {
    AppVersion current = AppVersion.parse("1.1.1").orElseThrow();
    ReleaseInfo published = ReleaseInfo.parse(PUBLISHED).orElseThrow();
    UpdateChecker checker =
        new UpdateChecker(
            Optional.of(current),
            () -> Optional.of(published),
            InstallPlatform.of("Mac OS X", "aarch64"),
            "rdyjun/mineportal");
    assertEquals(UpdateCheck.Status.UP_TO_DATE, checker.check().status());

    UpdateChecker failing =
        new UpdateChecker(
            Optional.of(current),
            () -> {
              throw new IllegalStateException("boom");
            },
            InstallPlatform.detect(),
            "rdyjun/mineportal");
    assertEquals(UpdateCheck.Status.CHECK_FAILED, failing.check().status());
    assertEquals(
        UpdateCheck.Status.CHECK_FAILED,
        new UpdateChecker(
                Optional.empty(), Optional::empty, InstallPlatform.detect(), "rdyjun/mineportal")
            .check()
            .status());
  }

  private static Optional<ReleaseInfo> fetch(int status, String body) throws Exception {
    HttpClient http = mock(HttpClient.class);
    HttpResponse<InputStream> response = response(status, body.getBytes(StandardCharsets.UTF_8));
    when(http.<InputStream>send(any(HttpRequest.class), any())).thenReturn(response);
    return client(http).fetchLatest();
  }

  private static GithubReleaseClient client(HttpClient http) {
    return new GithubReleaseClient(
        http,
        URI.create("http://127.0.0.1/latest"),
        "MinePortalDesktop/1.1.1",
        Duration.ofSeconds(8));
  }

  @SuppressWarnings("unchecked")
  private static HttpResponse<InputStream> response(int status, byte[] body) {
    HttpResponse<InputStream> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(new ByteArrayInputStream(body));
    return response;
  }

  private static final String PUBLISHED =
      """
      {
        "tag_name": "1.0.0",
        "draft": false,
        "prerelease": false,
        "html_url": "https://github.com/rdyjun/mineportal/releases/tag/1.0.0",
        "assets": [
          {"name": "mineportal-client.jar", "browser_download_url": "https://github.com/rdyjun/mineportal/releases/download/1.0.0/mineportal-client.jar"},
          {"name": "MinePortal-win.zip", "browser_download_url": "https://github.com/rdyjun/mineportal/releases/download/1.0.0/MinePortal-win.zip"}
        ]
      }
      """;
}
