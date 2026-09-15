package com.mineportal.server.account;

public record ExpiredAccountSession(String sid, AccountState state) {
}
