package com.mineportal;

import com.mineportal.net.BackendClient;
import com.mineportal.pair.ProtocolRegistrar;
import com.mineportal.pair.SingleInstance;
import com.mineportal.ui.DesktopApp;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

/** Desktop window and local Minecraft connection engine. */
public final class Main {

  public static void main(String[] args) {
    ProtocolRegistrar.registerIfNeeded();
    String pairCode = ProtocolRegistrar.extractPairCode(args);

    // 이미 떠있는 인스턴스가 있으면(mineportal:// 링크를 다시 클릭한 경우 등) 코드만
    // 넘겨주고 이 프로세스는 바로 종료한다 — 백그라운드 에이전트가 중복 실행되면 안 됨.
    if (!SingleInstance.acquire()) {
      SingleInstance.handOffAndExit(pairCode);
      return;
    }

    BackendClient client = new BackendClient(connected -> {});
    if (pairCode != null) {
      client.pairImmediately(pairCode);
    }
    client.start();

    // Keep an explicit background mode for diagnostics and existing web pairing integrations.
    if (!Arrays.asList(args).contains("--headless")) {
      DesktopApp.open(client);
      return;
    }
    try {
      new CountDownLatch(1).await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private Main() {}
}
