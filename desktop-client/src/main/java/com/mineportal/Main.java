package com.mineportal;

import com.mineportal.net.BackendClient;
import com.mineportal.pair.ProtocolRegistrar;
import com.mineportal.pair.SingleInstance;
import com.mineportal.tray.TrayApp;
import com.mineportal.update.Updater;

/**
 * 창이 없는 백그라운드 프로그램 — 트레이 아이콘 하나만 띄우고, 웹(mineportal.kr)이
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

        TrayApp tray = new TrayApp();
        tray.start();

        BackendClient client = new BackendClient(tray::setBackendConnected);
        if (pairCode != null) {
            client.pairImmediately(pairCode);
        }
        client.start();

        checkForUpdatesInBackground();

        // 트레이 아이콘이 있으면 AWT 이벤트 스레드가 JVM을 계속 살려두지만, 트레이가 없는
        // 환경(SystemTray.isSupported()==false)에서는 non-daemon 스레드가 하나도 없어
        // main()이 끝나자마자 종료돼버린다 — 그런 경우에도 백그라운드로 계속 떠있게 막는다.
        if (!java.awt.SystemTray.isSupported()) {
            try {
                new java.util.concurrent.CountDownLatch(1).await();
            } catch (InterruptedException ignored) {
            }
        }
    }

    /** GUI가 없으니 물어보지 않고, 새 버전이 있으면 그냥 받아서 재시작한다 — 지금은 실행
     * 중인 접속이 없을 이 시점(기동 직후)에만 하는 것으로 위험을 줄인다. */
    private static void checkForUpdatesInBackground() {
        new Thread(() -> {
            try {
                Updater updater = new Updater();
                Updater.ReleaseInfo release = updater.checkForUpdate();
                if (release != null && updater.applyUpdate(release)) {
                    System.exit(0);
                }
            } catch (Exception ignored) {
            }
        }, "mineportal-update").start();
    }

    private Main() {
    }
}
