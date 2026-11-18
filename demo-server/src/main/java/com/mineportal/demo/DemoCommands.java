package com.mineportal.demo;

import java.util.List;
import java.util.Locale;

/** Chat commands. A reply is either broadcast or sent only to the player who typed it. */
public final class DemoCommands {
  public record Reply(String toEveryone, String toSender) {}

  private static final String HELP =
      "명령어: /help, /list, /say, /me\n"
          + "/help — 이 도움말\n"
          + "/list — 접속 중인 사람\n"
          + "/say <내용> — 모두에게 말하기\n"
          + "/me <행동> — 행동으로 말하기";

  private DemoCommands() {}

  public static Reply handle(String sender, List<String> online, String commandLine) {
    String trimmed = commandLine == null ? "" : commandLine.trim();
    int space = trimmed.indexOf(' ');
    String name = (space < 0 ? trimmed : trimmed.substring(0, space)).toLowerCase(Locale.ROOT);
    String argument = space < 0 ? "" : trimmed.substring(space + 1).trim();
    return switch (name) {
      case "help" -> new Reply(null, HELP);
      case "list" -> new Reply(null, list(online));
      case "say" ->
          argument.isEmpty()
              ? new Reply(null, "사용법: /say <내용>")
              : new Reply("[" + sender + "] " + argument, null);
      case "me" ->
          argument.isEmpty()
              ? new Reply(null, "사용법: /me <행동>")
              : new Reply("* " + sender + " " + argument, null);
      default -> new Reply(null, "알 수 없는 명령어예요. /help 로 명령어를 확인해 주세요.");
    };
  }

  private static String list(List<String> online) {
    if (online.isEmpty()) {
      return "접속 중인 사람이 없어요.";
    }
    return "접속 중 " + online.size() + "명: " + String.join(", ", online);
  }
}
