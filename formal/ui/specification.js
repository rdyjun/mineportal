// Properties of the production page (client/index.html), driven against the
// local stub. Random clicks come from Bombadil's default actions. The extra
// actions log in, add a server, switch the chat layout (which drops extra
// slots), and toggle the language, so those controls are actually used.

import { always, extract } from "@antithesishq/bombadil";
import { actions, registerCustomAction } from "@antithesishq/bombadil/browser";
import { defaultActions } from "@antithesishq/bombadil/browser/defaults";

export { defaultActions };

const copy = {
  ko: { title: "마인포탈", play: "체험하기", login: "로그인", unsupported: "준비 중" },
  en: { title: "MinePortal", play: "Try it", login: "Log in", unsupported: "not supported" },
};

function shown(state, id) {
  const el = state.document.getElementById(id);
  if (!el) return false;
  const style = state.window.getComputedStyle(el);
  return style.display !== "none" && style.visibility !== "hidden";
}

const ui = extract((state) => {
  const doc = state.document;
  const win = state.window;
  const grid = doc.getElementById("chatGrid");
  const login = doc.getElementById("loginTriggerBtn");
  const shell = Boolean(grid && login && doc.getElementById("downloadBtn"));
  if (!shell) {
    return {
      shell: false,
      lang: "",
      loggedOut: false,
      serversActive: false,
      skeleton: false,
      listHosts: [],
      cardHosts: [],
      layout: 0,
      slotCount: 0,
      slotsOk: false,
      platform: null,
      downloads: [],
      msHref: null,
      playText: "",
      title: "",
      loginText: "",
      realModeTabHidden: true,
    };
  }
  const slots = [...grid.querySelectorAll(":scope > .chat-slot")];
  const downloads = ["downloadBtn", "gateDownloadBtn"].map((id) => {
    const el = doc.getElementById(id);
    return {
      href: el ? el.getAttribute("href") : null,
      disabled: el ? el.getAttribute("aria-disabled") : null,
      blocked: el ? el.classList.contains("btn-disabled") : true,
      text: el ? el.textContent || "" : "",
    };
  });
  const platform =
    typeof win.downloadPlatform === "function" ? win.downloadPlatform(win.navigator) : null;
  return {
    shell: true,
    lang: doc.documentElement.lang,
    loggedOut: shown(state, "loginTriggerBtn") && !shown(state, "accountPicker"),
    serversActive: doc.getElementById("serversView").classList.contains("active"),
    skeleton: Boolean(doc.querySelector("#serverList .server-list-skeleton-item")),
    listHosts: [...doc.querySelectorAll("#serverList .server-meta")].map((el) => el.textContent),
    cardHosts: [...doc.querySelectorAll("#serverGridView .server-card .name")].map(
      (el) => el.textContent,
    ),
    layout: Number(grid.dataset.layout),
    slotCount: slots.length,
    slotsOk: slots.every(
      (slot) =>
        slot.querySelector(".slot-log") &&
        slot.querySelector("form") &&
        slot.querySelector(".slot-connect-btn"),
    ),
    platform,
    downloads,
    msHref: doc.getElementById("msLoginBtn").getAttribute("href"),
    playText: doc.getElementById("tabListBtn").textContent,
    title: doc.title,
    loginText: login.textContent,
    realModeTabHidden: win.getComputedStyle(doc.getElementById("modeRealBtn")).display === "none",
  };
});

export const login_gate = always(() => {
  const view = ui.current;
  if (!view.shell || !view.loggedOut) return true;
  return (
    !view.serversActive &&
    view.msHref === "/api/account/login/microsoft" &&
    (view.skeleton || view.listHosts.length === 0)
  );
});

export const server_list_matches_grid = always(() => {
  const view = ui.current;
  if (!view.shell || view.skeleton) return true;
  if (view.listHosts.length !== view.cardHosts.length) return false;
  const listed = [...view.listHosts].sort();
  const cards = [...view.cardHosts].sort();
  return listed.every((host, index) => host === cards[index]);
});

export const server_list_has_no_duplicates = always(() => {
  const view = ui.current;
  if (!view.shell) return true;
  return new Set(view.listHosts).size === view.listHosts.length;
});

export const chat_grid_matches_layout = always(() => {
  const view = ui.current;
  if (!view.shell) return true;
  return (
    (view.layout === 1 || view.layout === 2 || view.layout === 4) &&
    view.slotCount === view.layout &&
    view.slotsOk
  );
});

export const web_real_mode_tab_stays_hidden = always(() => {
  const view = ui.current;
  if (!view.shell) return true;
  return view.realModeTabHidden;
});

export const language_matches_visible_copy = always(() => {
  const view = ui.current;
  if (!view.shell) return true;
  const row = copy[view.lang];
  return (
    Boolean(row) &&
    view.title === row.title &&
    view.playText === row.play &&
    view.loginText === row.login
  );
});

export const download_buttons_follow_platform = always(() => {
  const view = ui.current;
  if (!view.shell) return true;
  const row = copy[view.lang];
  const file =
    view.platform === "windows"
      ? "MinePortal-win.zip"
      : view.platform === "mac"
        ? "MinePortal-mac-arm64.dmg"
        : null;
  return (
    view.downloads.length === 2 &&
    view.downloads.every((button) => {
      if (!file) {
        return (
          button.disabled === "true" &&
          button.blocked &&
          (button.href == null || button.href === "") &&
          Boolean(row) &&
          button.text.includes(row.unsupported)
        );
      }
      return (
        button.disabled === "false" &&
        !button.blocked &&
        typeof button.href === "string" &&
        button.href.endsWith("/" + file)
      );
    })
  );
});

const startLogin = registerCustomAction("startLogin", async (document, window) => {
  const picker = document.getElementById("accountPicker");
  if (!picker || picker.style.display !== "none") return;
  window.location.assign("/api/account/login/microsoft");
});

const addServer = registerCustomAction("addServer", async (document, _window, host) => {
  const add = document.getElementById("addServerBtn");
  const form = document.getElementById("addServerForm");
  if (!add || !form) return;
  document.getElementById("tabListBtn").click();
  add.click();
  form.elements.host.value = host;
  form.elements.version.value = "1.21.4";
  form.requestSubmit();
  await new Promise((resolve) => setTimeout(resolve, 250));
});

const shrinkChatGrid = registerCustomAction("shrinkChatGrid", async (document) => {
  const wide = document.querySelector('#layoutSwitcher button[data-n="4"]');
  const single = document.querySelector('#layoutSwitcher button[data-n="1"]');
  if (wide) wide.click();
  if (single) single.click();
});

const toggleLanguage = registerCustomAction("toggleLanguage", async (document) => {
  const button = document.getElementById("langBtn");
  const menu = document.getElementById("langMenu");
  if (!button || !menu) return;
  button.click();
  const item = menu.querySelector(".lang-item");
  if (item) item.click();
});

export const exercise = actions(() => [
  startLogin(),
  addServer("alpha.example.test"),
  addServer("alpha.example.test"),
  addServer("beta.example.test:25566"),
  shrinkChatGrid(),
  toggleLanguage(),
]);
