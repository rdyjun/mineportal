package com.mineportal.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Semantic version parsed from the build or from a GitHub release tag. Pre-release identifiers sort
 * before the matching stable version, and {@code +build} metadata is ignored.
 */
public final class AppVersion implements Comparable<AppVersion> {

  private final int major;
  private final int minor;
  private final int patch;
  private final List<String> preRelease;
  private final String text;

  private AppVersion(int major, int minor, int patch, List<String> preRelease) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.preRelease = preRelease;
    this.text =
        preRelease.isEmpty()
            ? major + "." + minor + "." + patch
            : major + "." + minor + "." + patch + "-" + String.join(".", preRelease);
  }

  public static Optional<AppVersion> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String text = raw.trim();
    if (text.isEmpty() || text.length() > 128) {
      return Optional.empty();
    }
    if (text.charAt(0) == 'v' || text.charAt(0) == 'V') {
      text = text.substring(1);
    }
    int plus = text.indexOf('+');
    if (plus >= 0) {
      String metadata = text.substring(plus + 1);
      if (metadata.isEmpty() || !metadata.matches("[0-9A-Za-z.-]+")) {
        return Optional.empty();
      }
      text = text.substring(0, plus);
    }
    if (text.isEmpty()) {
      return Optional.empty();
    }

    String core = text;
    String pre = "";
    int dash = text.indexOf('-');
    if (dash >= 0) {
      core = text.substring(0, dash);
      pre = text.substring(dash + 1);
      if (pre.isEmpty()) {
        return Optional.empty();
      }
    }

    String[] parts = core.split("\\.", -1);
    if (parts.length != 3) {
      return Optional.empty();
    }
    int[] numbers = new int[3];
    for (int i = 0; i < 3; i++) {
      Integer value = component(parts[i]);
      if (value == null) {
        return Optional.empty();
      }
      numbers[i] = value;
    }

    List<String> identifiers = List.of();
    if (!pre.isEmpty()) {
      String[] rawIds = pre.split("\\.", -1);
      List<String> parsed = new ArrayList<>();
      for (String id : rawIds) {
        if (!id.matches("[0-9A-Za-z-]+")) {
          return Optional.empty();
        }
        if (id.chars().allMatch(Character::isDigit)) {
          if (id.length() > 18) {
            return Optional.empty();
          }
          parsed.add(Long.toString(Long.parseLong(id)));
        } else {
          parsed.add(id);
        }
      }
      identifiers = List.copyOf(parsed);
    }
    return Optional.of(new AppVersion(numbers[0], numbers[1], numbers[2], identifiers));
  }

  private static Integer component(String token) {
    if (token.isEmpty() || token.length() > 9) {
      return null;
    }
    for (int i = 0; i < token.length(); i++) {
      if (!Character.isDigit(token.charAt(i))) {
        return null;
      }
    }
    try {
      return Integer.parseInt(token);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  public boolean stable() {
    return preRelease.isEmpty();
  }

  public boolean newerThan(AppVersion other) {
    return compareTo(other) > 0;
  }

  @Override
  public int compareTo(AppVersion other) {
    int core = Integer.compare(major, other.major);
    if (core != 0) {
      return core;
    }
    core = Integer.compare(minor, other.minor);
    if (core != 0) {
      return core;
    }
    core = Integer.compare(patch, other.patch);
    if (core != 0) {
      return core;
    }
    if (preRelease.isEmpty() && other.preRelease.isEmpty()) {
      return 0;
    }
    if (preRelease.isEmpty()) {
      return 1;
    }
    if (other.preRelease.isEmpty()) {
      return -1;
    }
    int limit = Math.min(preRelease.size(), other.preRelease.size());
    for (int i = 0; i < limit; i++) {
      int id = compareIdentifier(preRelease.get(i), other.preRelease.get(i));
      if (id != 0) {
        return id;
      }
    }
    return Integer.compare(preRelease.size(), other.preRelease.size());
  }

  private static int compareIdentifier(String left, String right) {
    boolean leftNum = left.chars().allMatch(Character::isDigit);
    boolean rightNum = right.chars().allMatch(Character::isDigit);
    if (leftNum && rightNum) {
      return Long.compare(Long.parseLong(left), Long.parseLong(right));
    }
    if (leftNum) {
      return -1;
    }
    if (rightNum) {
      return 1;
    }
    return left.compareTo(right);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AppVersion version && compareTo(version) == 0;
  }

  @Override
  public int hashCode() {
    return text.hashCode();
  }

  @Override
  public String toString() {
    return text;
  }
}
