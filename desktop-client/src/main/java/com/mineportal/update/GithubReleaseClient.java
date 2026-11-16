package com.mineportal.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Reads {@code /repos/{owner}/{repo}/releases/latest}. That endpoint already skips drafts and
 * pre-releases. Non-200 responses (including rate limits), timeouts, and malformed bodies become an
 * empty result so the app can keep running.
 */
public final class GithubReleaseClient implements ReleaseSource {

  static final int MAX_BODY_BYTES = 1_000_000;

  private final HttpClient http;
  private final URI latestUri;
  private final String userAgent;
  private final Duration timeout;

  public GithubReleaseClient(HttpClient http, URI latestUri, String userAgent, Duration timeout) {
    this.http = http;
    this.latestUri = latestUri;
    this.userAgent = userAgent == null || userAgent.isBlank() ? "MinePortalDesktop" : userAgent;
    this.timeout = timeout == null ? Duration.ofSeconds(8) : timeout;
  }

  public static ReleaseSource create(String repo, String appVersion) {
    Optional<URI> endpoint = latestApi(repo);
    if (endpoint.isEmpty()) return Optional::empty;
    String token = appVersion == null ? "" : appVersion.trim();
    if (token.length() > 32 || !token.matches("[A-Za-z0-9._+-]*")) token = "";
    String agent = token.isEmpty() ? "MinePortalDesktop" : "MinePortalDesktop/" + token;
    HttpClient http =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    return new GithubReleaseClient(http, endpoint.get(), agent, Duration.ofSeconds(8));
  }

  public static Optional<URI> latestApi(String repo) {
    if (!SafeGithubUrl.validRepo(repo)) return Optional.empty();
    return Optional.of(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"));
  }

  @Override
  public Optional<ReleaseInfo> fetchLatest() {
    if (http == null || latestUri == null) return Optional.empty();
    HttpRequest request =
        HttpRequest.newBuilder(latestUri)
            .timeout(timeout)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", userAgent)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .GET()
            .build();
    try {
      HttpResponse<InputStream> response =
          http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (InputStream body = response.body()) {
        if (response.statusCode() != 200 || body == null) return Optional.empty();
        byte[] data = body.readNBytes(MAX_BODY_BYTES + 1);
        if (data.length == 0 || data.length > MAX_BODY_BYTES) return Optional.empty();
        return ReleaseInfo.parse(new String(data, StandardCharsets.UTF_8));
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (IOException | RuntimeException ex) {
      return Optional.empty();
    }
  }
}
