"""Собирает промо-скриншоты 1920x1080 (JPG) из портретных скриншотов приложения.

Использование: python tools/make_store_shots.py <папка_с_png> <папка_вывода>
Слайды описаны в SLIDES: файлы скриншотов, заголовок и подпись.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1920, 1080
GREEN_900 = (11, 58, 39)
GREEN_800 = (15, 74, 50)
GOLD = (185, 138, 46)
GOLD_LIGHT = (226, 194, 122)
CREAM = (251, 246, 234)

FONT_BOLD = "C:/Windows/Fonts/segoeuib.ttf"
FONT_REG = "C:/Windows/Fonts/segoeui.ttf"
LOGO = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-nodpi/logo_emblem.png"

SLIDES = [
    (["home.png", "learn.png"], "Первые шаги в Исламе", "Понятные учебные маршруты\nдля тех, кто только начинает"),
    (["lesson.png"], "Короткие уроки", "Несколько минут в день —\nс источниками и статусом проверки"),
    (["quran.png", "surah.png"], "Коран с переводом и аудио", "Перевод смыслов Крачковского\nи 14 чтецов по аятам"),
    (["prayer.png"], "Время намаза", "Официальное расписание ДУМ КБР\nи расчёт для любого города"),
    (["questions.png"], "Вопросы и ответы", "Ответы на частые вопросы\nи возможность задать свой"),
]


def background() -> Image.Image:
    img = Image.new("RGB", (W, H), GREEN_900)
    grad = Image.linear_gradient("L").rotate(90).resize((W, H))
    img = Image.composite(Image.new("RGB", (W, H), GREEN_800), img, grad)
    d = ImageDraw.Draw(img)
    # мягкий орнамент — концентрические дуги в углу
    for i, r in enumerate(range(260, 1400, 90)):
        d.ellipse((W - 380 - r, H + 120 - r, W - 380 + r, H + 120 + r), outline=(30, 92, 66), width=2)
    return img


def phone(shot: Image.Image, height: int) -> Image.Image:
    bezel = 18
    sh = shot.resize((round(shot.width * (height - 2 * bezel) / shot.height), height - 2 * bezel), Image.LANCZOS)
    w = sh.width + 2 * bezel
    body = Image.new("RGBA", (w, height), (0, 0, 0, 0))
    ImageDraw.Draw(body).rounded_rectangle((0, 0, w - 1, height - 1), radius=64, fill=(18, 20, 19, 255))
    mask = Image.new("L", sh.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, sh.width - 1, sh.height - 1), radius=48, fill=255)
    body.paste(sh, (bezel, bezel), mask)
    return body


def paste_with_shadow(canvas: Image.Image, item: Image.Image, xy):
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    alpha = item.split()[3].point(lambda a: a * 0.55)
    shadow.paste((0, 0, 0, 255), (xy[0] + 14, xy[1] + 24), alpha)
    shadow = shadow.filter(ImageFilter.GaussianBlur(28))
    canvas.alpha_composite(shadow)
    canvas.alpha_composite(item, xy)


def render(shots, title, subtitle) -> Image.Image:
    canvas = background().convert("RGBA")
    d = ImageDraw.Draw(canvas)

    logo = Image.open(LOGO).convert("RGBA")
    logo.thumbnail((110, 110), Image.LANCZOS)
    canvas.alpha_composite(logo, (120, 110))
    d.text((120 + logo.width + 24, 110 + logo.height // 2), "Путь", font=ImageFont.truetype(FONT_BOLD, 52), fill=GOLD_LIGHT, anchor="lm")

    tf = ImageFont.truetype(FONT_BOLD, 84)
    sf = ImageFont.truetype(FONT_REG, 44)
    text_w = 820
    lines, line = [], ""
    for word in title.split():
        test = f"{line} {word}".strip()
        if d.textlength(test, font=tf) > text_w and line:
            lines.append(line)
            line = word
        else:
            line = test
    lines.append(line)
    y = 400 if len(lines) == 1 else 350
    for ln in lines:
        d.text((120, y), ln, font=tf, fill=CREAM)
        y += 104
    d.rectangle((120, y + 18, 220, y + 24), fill=GOLD)
    d.multiline_text((120, y + 60), subtitle, font=sf, fill=(220, 230, 222), spacing=16)

    ph_h = 960
    phones = [phone(Image.open(s).convert("RGB"), ph_h) for s in shots]
    if len(phones) == 1:
        paste_with_shadow(canvas, phones[0], (W - 200 - phones[0].width, (H - ph_h) // 2))
    else:
        back, front = phones[1], phones[0]
        small_h = 880
        back = phone(Image.open(shots[1]).convert("RGB"), small_h)
        x_front = W - 470 - front.width
        paste_with_shadow(canvas, back, (W - 120 - back.width, (H - small_h) // 2 + 30))
        paste_with_shadow(canvas, front, (x_front, (H - ph_h) // 2))
    return canvas.convert("RGB")


def main():
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    for i, (files, title, subtitle) in enumerate(SLIDES, 1):
        paths = [src / f for f in files]
        if not all(p.exists() for p in paths):
            print("пропуск, нет файлов:", [p.name for p in paths if not p.exists()])
            continue
        img = render(paths, title, subtitle)
        dst = out / f"{i:02d}.jpg"
        img.save(dst, "JPEG", quality=92, optimize=True, progressive=True)
        print(dst, f"{dst.stat().st_size / 1024:.0f} KB")


if __name__ == "__main__":
    main()
