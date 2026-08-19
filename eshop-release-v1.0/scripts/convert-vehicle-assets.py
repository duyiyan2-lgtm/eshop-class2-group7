from pathlib import Path
from PIL import Image

src = Path(r"C:\Users\duyiyan\.grok\sessions\C%3A%5CUsers%5Cduyiyan\01a0087c-25a9-79c1-b127-8652b3daa771\images")
root = Path(r"C:\Users\duyiyan\Desktop\eshop-class2-group7\eshop-release-v1.0\frontend\public\product-images\vehicles")

mapping = {
    "2.jpg": "su7/white/front45.webp",
    "7.jpg": "su7/white/side.webp",
    "9.jpg": "su7/white/rear45.webp",
    "8.jpg": "su7/white/rear.webp",
    "12.jpg": "su7/white/front.webp",
    "5.jpg": "su7/black/front45.webp",
    "34.jpg": "su7/black/side.webp",
    "38.jpg": "su7/black/rear45.webp",
    "41.jpg": "su7/black/rear.webp",
    "42.jpg": "su7/black/front.webp",
    "11.jpg": "su7/silver/front45.webp",
    "35.jpg": "su7/silver/side.webp",
    "40.jpg": "su7/silver/rear45.webp",
    "43.jpg": "su7/silver/rear.webp",
    "48.jpg": "su7/silver/front.webp",
    "4.jpg": "su7/green/front45.webp",
    "33.jpg": "su7/green/side.webp",
    "37.jpg": "su7/green/rear45.webp",
    "44.jpg": "su7/green/rear.webp",
    "47.jpg": "su7/green/front.webp",
    "6.jpg": "yu7/white/front45.webp",
    "10.jpg": "yu7/white/side.webp",
    "17.jpg": "yu7/white/rear45.webp",
    "28.jpg": "yu7/white/rear.webp",
    "36.jpg": "yu7/white/front.webp",
    "14.jpg": "yu7/black/front45.webp",
    "45.jpg": "yu7/black/side.webp",
    "31.jpg": "yu7/silver/front45.webp",
    "51.jpg": "yu7/silver/side.webp",
    "27.jpg": "yu7/green/front45.webp",
    "49.jpg": "yu7/green/side.webp",
    "3.jpg": "ultra/white/front45.webp",
    "16.jpg": "ultra/white/side.webp",
    "13.jpg": "ultra/white/rear45.webp",
    "29.jpg": "ultra/white/rear.webp",
    "39.jpg": "ultra/white/front.webp",
    "32.jpg": "ultra/black/front45.webp",
    "50.jpg": "ultra/black/side.webp",
    "30.jpg": "ultra/silver/front45.webp",
    "52.jpg": "ultra/silver/side.webp",
    "15.jpg": "ultra/green/front45.webp",
    "46.jpg": "ultra/green/side.webp",
    "54.jpg": "yu7/black/rear45.webp",
    "53.jpg": "yu7/black/rear.webp",
    "55.jpg": "yu7/black/front.webp",
    "58.jpg": "yu7/silver/rear45.webp",
    "56.jpg": "yu7/silver/rear.webp",
    "57.jpg": "yu7/silver/front.webp",
    "60.jpg": "yu7/green/rear45.webp",
    "61.jpg": "yu7/green/rear.webp",
    "59.jpg": "yu7/green/front.webp",
    "64.jpg": "ultra/black/rear45.webp",
    "63.jpg": "ultra/black/rear.webp",
    "62.jpg": "ultra/black/front.webp",
    "67.jpg": "ultra/silver/rear45.webp",
    "65.jpg": "ultra/silver/rear.webp",
    "66.jpg": "ultra/silver/front.webp",
    "70.jpg": "ultra/green/rear45.webp",
    "68.jpg": "ultra/green/rear.webp",
    "69.jpg": "ultra/green/front.webp",
    "20.jpg": "shared/wheels/w19.webp",
    "18.jpg": "shared/wheels/w20.webp",
    "21.jpg": "shared/wheels/w21.webp",
    "23.jpg": "shared/interiors/black.webp",
    "22.jpg": "shared/interiors/beige.webp",
    "19.jpg": "shared/interiors/sport.webp",
    "25.jpg": "shared/packs/winter.webp",
    "26.jpg": "shared/packs/comfort.webp",
    "24.jpg": "shared/packs/track.webp",
}

copies = {}


def save_webp(img_path, dest_rel, max_w=1600, quality=80):
    dest = root / dest_rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    image = Image.open(img_path).convert("RGB")
    if image.width > max_w:
        height = int(image.height * max_w / image.width)
        image = image.resize((max_w, height), Image.Resampling.LANCZOS)
    image.save(dest, "WEBP", quality=quality, method=6)
    print(f"{dest_rel}\t{dest.stat().st_size}")


for name, dest in mapping.items():
    path = src / name
    if not path.exists():
        print("MISSING", name)
        continue
    save_webp(path, dest)

for dest, source in copies.items():
    source_file = root / source
    dest_file = root / dest
    if source_file.exists() and not dest_file.exists():
        dest_file.parent.mkdir(parents=True, exist_ok=True)
        dest_file.write_bytes(source_file.read_bytes())
        print("copied", dest)
