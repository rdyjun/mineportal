package com.mineportal.server.account;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class AccountSessionManagerTest {

  @Test
  void springSelectsTheProductionConstructorWhenTestConstructorAlsoExists() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(AccountSessionManager.class);
      context.refresh();

      assertNotNull(context.getBean(AccountSessionManager.class));
    }
  }

  @Test
  void expiredSessionIsRemovedAndPublishedForCleanup() {
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    AccountSessionManager manager = new AccountSessionManager(events, -1);
    AccountState original = manager.get("sid-a");

    manager.removeExpiredSessions();

    ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
    verify(events).publishEvent(event.capture());
    ExpiredAccountSession expired = assertInstanceOf(ExpiredAccountSession.class, event.getValue());
    assertEquals("sid-a", expired.sid());
    assertSame(original, expired.state());
    assertNotSame(original, manager.get("sid-a"));
  }

  @Test
  void explicitRemovalDoesNotEmitExpiryEvent() {
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    AccountSessionManager manager = new AccountSessionManager(events, 1000);
    manager.get("sid-a");

    manager.remove("sid-a");

    verifyNoInteractions(events);
  }
}
