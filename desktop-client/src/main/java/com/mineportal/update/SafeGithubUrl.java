package com.mineportal.update;

import java.net.URI;
import java.net.URISyntaxException;

/** Accepts only https URLs that stay on this repository's GitHub release pages. */
public final class SafeGithubUrl {

  private SafeGithubUrl() {}

  public static boolean validRepo(String repo) {
    if (repo == null || !repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
      return false;
    }
    int slash = repo.indexOf('/');
    String owner = repo.substring(0, slash);
    String name = repo.substring(slash + 1);
    return !owner.contains("..") && !name.contains("..");
  }

  public static boolean isRepoHome(String url, String repo) {
    URI uri = base(url, repo);
    return uri != null && uri.getPath().equals("/" + repo);
  }

  public static boolean isRepoReleasePage(String url, String repo) {
    URI uri = base(url, repo);
    if (uri == null) {
      return false;
    }
    String releases = "/" + repo + "/releases";
    String path = uri.getPath();
    return path.equals(releases) || path.startsWith(releases + "/tag/");
  }

  public static boolean isRepoAsset(String url, String repo, String assetName) {
    if (assetName == null
        || assetName.isBlank()
        || assetName.contains("/")
        || assetName.contains("\\")
        || assetName.contains("..")) {
      return false;
    }
    URI uri = base(url, repo);
    if (uri == null) {
      return false;
    }
    String path = uri.getPath();
    String prefix = "/" + repo + "/releases/download/";
    return path.startsWith(prefix) && path.endsWith("/" + assetName);
  }

  private static URI base(String url, String repo) {
    if (url == null || !validRepo(repo)) {
      return null;
    }
    URI uri;
    try {
      uri = new URI(url.trim());
    } catch (URISyntaxException ex) {
      return null;
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      return null;
    }
    if (uri.getHost() == null || !"github.com".equalsIgnoreCase(uri.getHost())) {
      return null;
    }
    int port = uri.getPort();
    if (port != -1 && port != 443) {
      return null;
    }
    if (uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
      return null;
    }
    String path = uri.getPath();
    if (path == null || path.contains("..") || path.contains("//") || path.contains("\\")) {
      return null;
    }
    String prefix = "/" + repo;
    if (!path.equals(prefix) && !path.startsWith(prefix + "/")) {
      return null;
    }
    return uri;
  }
}
