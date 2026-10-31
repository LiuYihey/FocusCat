import base64, io, os, sys, time
from playwright.sync_api import sync_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
HTML = os.path.join(HERE, "cat.html").replace("\\", "/")
OUT_DIR = os.path.join(HERE, "frames")
os.makedirs(OUT_DIR, exist_ok=True)

ARGS = [
    "--use-angle=swiftshader",
    "--use-gl=angle",
    "--enable-unsafe-swiftshader",
    "--disable-gpu-sandbox",
    "--no-sandbox",
    "--allow-file-access-from-files",
    "--disable-dev-shm-usage",
    "--disable-features=Vulkan",
]


def render_all(browser):
    page = browser.new_page(viewport={"width": 220, "height": 220}, device_scale_factor=1)
    page.on("pageerror", lambda e: print("[pageerror]", e))
    page.goto("file:///" + HTML)
    page.wait_for_function("window.__ready === true", timeout=30000)

    n = page.evaluate("window.FRAME_COUNT")
    frames = []
    for i in range(n):
        data = page.evaluate("i => window.renderFrame(i)", i)
        raw = base64.b64decode(data.split(",", 1)[1])
        frames.append(raw)
    page.close()
    return frames


def main():
    with sync_playwright() as p:
        for attempt in range(6):
            browser = p.chromium.launch(args=ARGS)
            try:
                frames = render_all(browser)
            except Exception as e:
                print(f"attempt {attempt} error: {str(e)[:160]}")
                browser.close()
                time.sleep(2)
                continue
            browser.close()

            # 校验：所有帧必须非空且彼此不同（大小差异说明画面确实在动）
            sizes = [len(f) for f in frames]
            if min(sizes) < 3000 or len(set(sizes)) < len(sizes) * 0.5:
                print(f"attempt {attempt}: frames look blank/identical, sizes={sizes}")
                time.sleep(2)
                continue

            for i, raw in enumerate(frames):
                with open(os.path.join(OUT_DIR, f"f{i:02d}.png"), "wb") as f:
                    f.write(raw)
            print("OK frames:", len(frames), "sizes:", sizes)
            return
    raise SystemExit("render failed after retries")


main()
print("done")
