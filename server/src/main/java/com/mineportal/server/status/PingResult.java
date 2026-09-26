package com.mineportal.server.status;

/** 프론트엔드가 GET /api/ping에서 이미 기대하고 있는 JSON 형태를 그대로 따른다. */
public record PingResult(
        boolean online,
        Integer playersOnline,
        Integer playersMax,
        String motd,
        String version,
        // 서버의 프로토콜 번호 — 우리(26.1)와 다르면 ViaVersion으로 번역해서 접속한다.
        Integer protocol,
        // Forge 서버는 status 응답에 forgeData.fmlNetworkVersion을 넣는다 — null이면 Forge가 아니다.
        Integer forgeNetVersion,
        String error
) {

    public static PingResult offline(String error) {
        return new PingResult(false, null, null, null, null, null, null, error);
    }

    public static PingResult online(Integer playersOnline, Integer playersMax, String motd, String version,
                                    Integer protocol, Integer forgeNetVersion) {
        return new PingResult(true, playersOnline, playersMax, motd, version, protocol, forgeNetVersion, null);
    }

}
