package com.mineportal;

import com.mineportal.net.BackendClient;
import com.mineportal.pair.ProtocolRegistrar;
import com.mineportal.pair.SingleInstance;

import java.util.concurrent.CountDownLatch;

/**
 * UI가 전혀 없는 백그라운드 프로그램 — 웹(mineportal.kr)이
 * 백엔드를 통해 보내는 명령(로그인 토큰/연결/해제/채팅)을 BackendClient로 받아 실행한다.
 * 서버 목록, 로그인 버튼, 채팅창은 전부 웹에 있다 — 이 앱은 사용자 실IP로 실제
 * 마인크래프트 프로토콜 접속만 대신 수행한다.
 */
public final class Main {

    public static void main(String[] args) {
        ProtocolRegistrar.registerIfNeeded();
        String pairCode = ProtocolRegistrar.extractPairCode(args);

        // 이미 떠있는 인스턴스가 있으면(mineportal:// 링크를 다시 클릭한 경우 등) 코드만
        // 넘겨주고 이 프로세스는 바로 종료한다 — 창을 또 띄우거나 트레이 아이콘이 중복되면 안 됨.
        if (!SingleInstance.acquire()) {
            SingleInstance.handOffAndExit(pairCode);
            return;
        }

        BackendClient client = new BackendClient(connected -> { });
        if (pairCode != null) {
            client.pairImmediately(pairCode);
        }
        client.start();

        // All worker threads are daemon threads. Keep this UI-less agent alive until Windows or
        // the user terminates the process; every user-facing action remains on the website.
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Main() {
    }
}
