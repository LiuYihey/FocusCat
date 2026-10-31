import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "frames")
OUT = os.path.join(HERE, "cat_run.png")
N = 12

imgs = [Image.open(os.path.join(SRC, f"f{i:02d}.png")).convert("RGBA") for i in range(N)]
w, h = imgs[0].size
print("source frame:", w, "x", h)

# 关键：所有帧用「并集包围盒」统一裁剪 —— 否则每帧裁剪框不同，
# 播放时猫会左右/上下抖动
union = None
for im in imgs:
    bbox = im.getbbox()
    union = bbox if union is None else (
        min(union[0], bbox[0]), min(union[1], bbox[1]),
        max(union[2], bbox[2]), max(union[3], bbox[3]),
    )
print("union bbox:", union)

# 四周留 4px 余量，避免边缘被切
pad = 4
x0 = max(0, union[0] - pad)
y0 = max(0, union[1] - pad)
x1 = min(w, union[2] + pad)
y1 = min(h, union[3] + pad)
cw, ch = x1 - x0, y1 - y0
print("cropped:", cw, "x", ch)

sheet = Image.new("RGBA", (cw * N, ch), (0, 0, 0, 0))
for i, im in enumerate(imgs):
    sheet.paste(im.crop((x0, y0, x1, y1)), (i * cw, 0))

sheet.save(OUT, optimize=True)
print("saved", OUT, sheet.size, os.path.getsize(OUT) // 1024, "KB")
