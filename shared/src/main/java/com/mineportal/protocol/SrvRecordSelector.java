package com.mineportal.protocol;

import com.mineportal.protocol.MinecraftAddressResolver.SrvRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

/** Orders SRV candidates by priority and RFC 2782 relative weight. */
final class SrvRecordSelector {

  private SrvRecordSelector() {}

  static List<SrvRecord> order(List<SrvRecord> records, RandomGenerator random) {
    List<SrvRecord> remaining = new ArrayList<>(records);
    remaining.sort(Comparator.comparingInt(SrvRecord::priority));
    List<SrvRecord> ordered = new ArrayList<>(remaining.size());

    while (!remaining.isEmpty()) {
      int priority = remaining.get(0).priority();
      int groupEnd = 0;
      while (groupEnd < remaining.size() && remaining.get(groupEnd).priority() == priority) {
        groupEnd++;
      }

      List<SrvRecord> group = new ArrayList<>(remaining.subList(0, groupEnd));
      remaining.subList(0, groupEnd).clear();
      while (!group.isEmpty()) {
        int selected = selectWeighted(group, random);
        ordered.add(group.remove(selected));
      }
    }
    return List.copyOf(ordered);
  }

  private static int selectWeighted(List<SrvRecord> records, RandomGenerator random) {
    long totalWeight = 0;
    for (SrvRecord record : records) totalWeight += Math.max(0, record.weight());
    if (totalWeight == 0) return random.nextInt(records.size());

    long choice = random.nextLong(totalWeight);
    long cumulative = 0;
    for (int i = 0; i < records.size(); i++) {
      cumulative += Math.max(0, records.get(i).weight());
      if (choice < cumulative) return i;
    }
    throw new IllegalStateException("weighted SRV selection did not select a record");
  }
}
