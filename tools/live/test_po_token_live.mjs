// Live check of the in-app PO token page (app/src/main/assets/po_token.html).
//
// Loads the page in Chromium under https://www.youtube.com, the address the app gives it in
// the WebView, runs hazelMint(), and prints what the app would store. With --verify it then
// asks yt-dlp for a video's formats with the token and visitor data, the way the app passes
// them, and reports whether the web client's formats came through.
//
// Needs network access and Playwright:
//   NODE_PATH=$(npm root -g) node tools/live/test_po_token_live.mjs [--verify] [VIDEO_URL]
import { createRequire } from "node:module";
import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

// Loaded through require so NODE_PATH can point at a global install.
const { chromium } = createRequire(import.meta.url)("playwright");

const here = path.dirname(fileURLToPath(import.meta.url));
const page_html = readFileSync(path.join(here, "../../app/src/main/assets/po_token.html"), "utf8");
const verify = process.argv.includes("--verify");
const video = process.argv.slice(2).find((a) => a.startsWith("http")) || "https://www.youtube.com/watch?v=jNQXAC9IVRw";

const launch = { headless: true };
if (process.env.CHROMIUM_PATH) launch.executablePath = process.env.CHROMIUM_PATH;
// Chromium ignores the proxy variables other tools read, so a proxy has to be given to it.
const proxy = process.env.HTTPS_PROXY || process.env.https_proxy;
if (proxy) launch.proxy = { server: proxy };
const browser = await chromium.launch(launch);
let failed = false;
try {
  const page = await browser.newPage();
  // Every other request the page makes is carried by Playwright's own request stack when
  // HARNESS_ROUTE_REQUESTS is set: for a sandbox whose proxy certificate Chromium does not
  // trust but Node does. Certificates are still checked, by Node.
  if (process.env.HARNESS_ROUTE_REQUESTS) {
    await page.route("**/*", async (route) => route.fulfill({ response: await route.fetch() }));
  }
  await page.route("https://www.youtube.com/hazel-po-token", (route) =>
    route.fulfill({ status: 200, contentType: "text/html", body: page_html }));
  await page.goto("https://www.youtube.com/hazel-po-token");
  const started = Date.now();
  const videoId = new URL(video).searchParams.get("v") || "jNQXAC9IVRw";
  const result = JSON.parse(await page.evaluate((id) => hazelMint([id]), videoId));
  console.log(`mint took ${Date.now() - started} ms`);
  const again = Date.now();
  const second = JSON.parse(await page.evaluate((id) => hazelMint([id]), videoId));
  console.log(`second mint, reusing the session, took ${Date.now() - again} ms` + (second.error ? `: ${second.error}` : ""));
  if (result.error) {
    console.log("FAIL:", result.error);
    failed = true;
  } else {
    console.log("PASS: visitorData", result.visitorData);
    console.log("PASS: poToken", result.poToken.slice(0, 24) + "...", `(${result.poToken.length} chars), ttl ${result.ttlSeconds}s`);
    console.log("PASS: video token", (result.tokens[videoId] || "").slice(0, 24) + "...");
    if (verify) {
      // As the app passes them: the video-bound token for streaming and the player.
      const token = result.tokens[videoId];
      const args = [
        "--extractor-args",
        `youtube:player_client=default,mweb;po_token=mweb.gvs+${token},mweb.player+${token};visitor_data=${result.visitorData};player_skip=webpage,configs`,
        "--js-runtimes", process.env.JS_RUNTIME || "node", "-v", "-F", video
      ];
      let out = "";
      try {
        out = execFileSync("yt-dlp", args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
      } catch (e) {
        out = (e.stdout || "") + (e.stderr || "");
      }
      const lines = out.split("\n");
      const po = lines.filter((l) => /po.?token/i.test(l));
      po.slice(0, 6).forEach((l) => console.log("  yt-dlp:", l));
      const formats = lines.filter((l) => /^\d+\s+\w+\s+/.test(l));
      console.log(`${formats.length ? "PASS" : "FAIL"}: ${formats.length} formats listed`);
      if (!formats.length) {
        failed = true;
        lines.filter((l) => /ERROR|WARNING/.test(l)).slice(0, 6).forEach((l) => console.log("  ", l));
      }
    }
  }
} finally {
  await browser.close();
}
process.exit(failed ? 1 : 0);
