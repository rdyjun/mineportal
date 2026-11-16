package com.mineportal.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The fields of a GitHub release that the update check needs. */
public final class ReleaseInfo {

  private final String tagName;
  private final boolean draft;
  private final boolean prerelease;
  private final String htmlUrl;
  private final List<Asset> assets;

  ReleaseInfo(
      String tagName, boolean draft, boolean prerelease, String htmlUrl, List<Asset> assets) {
    this.tagName = tagName;
    this.draft = draft;
    this.prerelease = prerelease;
    this.htmlUrl = htmlUrl;
    this.assets = assets;
  }

  public static Optional<ReleaseInfo> parse(String json) {
    if (json == null || json.isBlank()) {
      return Optional.empty();
    }
    try {
      JsonElement root = JsonParser.parseString(json);
      if (!root.isJsonObject()) {
        return Optional.empty();
      }
      JsonObject object = root.getAsJsonObject();
      String tag = string(object, "tag_name");
      if (tag == null || tag.isBlank()) {
        return Optional.empty();
      }
      Boolean draft = optionalBoolean(object, "draft");
      Boolean prerelease = optionalBoolean(object, "prerelease");
      if (draft == null || prerelease == null) {
        return Optional.empty();
      }
      String page = string(object, "html_url");
      if (!object.has("assets") || object.get("assets").isJsonNull()) {
        return Optional.of(new ReleaseInfo(tag, draft, prerelease, page, List.of()));
      }
      if (!object.get("assets").isJsonArray()) {
        return Optional.empty();
      }
      List<Asset> assets = new ArrayList<>();
      for (JsonElement element : object.getAsJsonArray("assets")) {
        if (!element.isJsonObject()) {
          continue;
        }
        JsonObject asset = element.getAsJsonObject();
        String name = string(asset, "name");
        String download = string(asset, "browser_download_url");
        if (name == null || name.isBlank() || download == null || download.isBlank()) {
          continue;
        }
        assets.add(new Asset(name, download));
      }
      return Optional.of(new ReleaseInfo(tag, draft, prerelease, page, List.copyOf(assets)));
    } catch (RuntimeException ex) {
      return Optional.empty();
    }
  }

  /** Missing booleans are false. A present non-boolean is rejected by returning null. */
  private static Boolean optionalBoolean(JsonObject object, String name) {
    if (!object.has(name) || object.get(name).isJsonNull()) {
      return false;
    }
    JsonElement value = object.get(name);
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
      return null;
    }
    return value.getAsBoolean();
  }

  private static String string(JsonObject object, String name) {
    if (!object.has(name) || object.get(name).isJsonNull()) {
      return null;
    }
    JsonElement value = object.get(name);
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      return null;
    }
    return value.getAsString();
  }

  public String tagName() {
    return tagName;
  }

  public boolean draft() {
    return draft;
  }

  public boolean prerelease() {
    return prerelease;
  }

  public String htmlUrl() {
    return htmlUrl;
  }

  public List<Asset> assets() {
    return assets;
  }

  public record Asset(String name, String downloadUrl) {}
}
