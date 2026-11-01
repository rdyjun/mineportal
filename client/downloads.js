// Shared by home and installation gates. Choose a platform before first paint.
var IS_DESKTOP = /MinePortalDesktop\//.test(navigator.userAgent || "");
document.documentElement.classList.toggle("desktop-app", IS_DESKTOP);
function downloadPlatform(nav) {
  var platform = (nav.userAgentData && nav.userAgentData.platform) || nav.platform || "";
  var agent = nav.userAgent || "";
  if (/Android|iPhone|iPad|iPod/i.test(agent) || (/Mac/i.test(platform) && nav.maxTouchPoints > 1)) return null;
  if (/Win/i.test(platform + " " + agent)) return "windows";
  if (/Mac/i.test(platform + " " + agent)) return "mac";
  return null;
}
function applyDownloadAvailability(el) {
  var lang;
  try { lang = localStorage.getItem("lang"); } catch (_) {}
  lang = lang || ((navigator.language || "").toLowerCase().startsWith("ko") ? "ko" : "en");
  var platform = downloadPlatform(navigator);
  el.classList.toggle("btn-disabled", !platform);
  el.setAttribute("aria-disabled", String(!platform));
  if (platform) {
    var filename = platform === "mac" ? "MinePortal-mac-arm64.dmg" : "MinePortal-win.zip";
    el.href = "https://github.com/rdyjun/mineportal/releases/latest/download/" + filename;
    el.textContent = platform === "mac" ? "⬇ Download for Mac (Apple Silicon)" : "⬇ Download for Windows";
    el.removeAttribute("tabindex");
  } else {
    el.removeAttribute("href");
    el.setAttribute("tabindex", "-1");
    el.textContent = lang === "ko" ? "🚧 이 OS는 준비 중이에요" : "🚧 This OS is not supported yet";
  }
}
