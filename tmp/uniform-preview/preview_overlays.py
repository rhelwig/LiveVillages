#!/usr/bin/env python3
"""Composite profession overlays onto the vanilla villager and draw front puppets."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from paint_profession_overlays import JOBS, paint_job

VANILLA_BASE = Path("/tmp/vanilla-villager/villager.png")
OUT = Path("/home/rhelwig/Projects/LiveVillages/tmp/uniform-preview/preview")
SCALE = 8


def load_base() -> Image.Image:
	return Image.open(VANILLA_BASE).convert("RGBA")


def composite(base: Image.Image, overlay: Image.Image) -> Image.Image:
	out = base.copy()
	out.alpha_composite(overlay)
	return out


def blit_face(dst: Image.Image, src: Image.Image, dx: int, dy: int, su: int, sv: int, sw: int, sh: int, scale: int) -> None:
	face = src.crop((su, sv, su + sw, sv + sh)).resize((sw * scale, sh * scale), Image.NEAREST)
	dst.alpha_composite(face, (dx, dy))


def draw_puppet(sheet: Image.Image, origin: tuple[int, int], overlay: Image.Image) -> Image.Image:
	"""Front-view puppet: head, optional brim, jacket, arms."""
	base = load_base()
	tex = composite(base, overlay)
	w, h = 20 * SCALE, 36 * SCALE
	im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
	# brim behind/around head if present
	brim = overlay.crop((31, 48, 47, 64))
	if brim.getbbox():
		bw, bh = 16 * SCALE, 3 * SCALE
		brim_s = brim.resize((16 * SCALE, 16 * SCALE), Image.NEAREST)
		# take a mid strip as a wide hat disc
		strip = brim_s.crop((0, 6 * SCALE, 16 * SCALE, 10 * SCALE)).resize((bw, bh), Image.NEAREST)
		im.alpha_composite(strip, (2 * SCALE, 1 * SCALE))
	# head front (8,8) 8x10 — hat front composites on top via overlay
	head = Image.new("RGBA", (8 * SCALE, 10 * SCALE), (0, 0, 0, 0))
	blit_face(head, tex, 0, 0, 8, 8, 8, 10, SCALE)
	# hat front overlay already in tex if opaque; also blit hat front
	hat_front = overlay.crop((40, 8, 48, 18))
	if hat_front.getbbox():
		head.alpha_composite(hat_front.resize((8 * SCALE, 10 * SCALE), Image.NEAREST))
	# hat top as a shallow cap above the head
	hat_top = overlay.crop((40, 0, 48, 8))
	if hat_top.getbbox():
		im.alpha_composite(hat_top.resize((8 * SCALE, 3 * SCALE), Image.NEAREST), (6 * SCALE, 0))
	im.alpha_composite(head, (6 * SCALE, 2 * SCALE))
	# jacket front (6,44) 8x20
	jacket = overlay.crop((6, 44, 14, 64))
	if jacket.getbbox():
		im.alpha_composite(jacket.resize((8 * SCALE, 20 * SCALE), Image.NEAREST), (6 * SCALE, 12 * SCALE))
	# arms (folded middle 40,38 8x4 looks like the crossed arms)
	arms = overlay.crop((44, 42, 52, 46))
	if not arms.getbbox():
		arms = overlay.crop((40, 38, 48, 42))
	if arms.getbbox():
		im.alpha_composite(arms.resize((4 * SCALE, 8 * SCALE), Image.NEAREST), (2 * SCALE, 14 * SCALE))
		im.alpha_composite(arms.resize((4 * SCALE, 8 * SCALE), Image.NEAREST), (14 * SCALE, 14 * SCALE))
	# nose from base
	nose = base.crop((24, 8, 28, 12)).resize((3 * SCALE, 4 * SCALE), Image.NEAREST)
	im.alpha_composite(nose, (8 * SCALE + SCALE // 2, 6 * SCALE))
	dst = Image.new("RGBA", im.size, (48, 44, 40, 255))
	dst.alpha_composite(im)
	return dst


def label(im: Image.Image, text: str) -> Image.Image:
	pad = Image.new("RGBA", (im.width, im.height + 18), (32, 30, 28, 255))
	pad.paste(im, (0, 0))
	d = ImageDraw.Draw(pad)
	d.text((4, im.height + 2), text, fill=(240, 232, 210, 255))
	return pad


def main() -> None:
	OUT.mkdir(parents=True, exist_ok=True)
	base = load_base()
	tiles: list[Image.Image] = []
	names: list[str] = []
	for name, job in JOBS.items():
		for tier in (1, 2, 3, 4):
			ov = paint_job(job, tier)
			puppet = draw_puppet(None, (0, 0), ov)  # type: ignore[arg-type]
			puppet = label(puppet, f"{name} t{tier}")
			puppet.save(OUT / f"{name}_t{tier}.png")
			# face close-up: head front + hat front
			face = Image.new("RGBA", (8, 10), (0, 0, 0, 0))
			face.alpha_composite(base.crop((8, 8, 16, 18)))
			face.alpha_composite(ov.crop((40, 8, 48, 18)))
			face = face.resize((8 * 12, 10 * 12), Image.NEAREST)
			face.save(OUT / f"{name}_t{tier}_face.png")
			if tier in (1, 3):
				tiles.append(puppet)
				names.append(f"{name} t{tier}")
	# contact sheet of T1 and T3
	cols = 8
	tw, th = tiles[0].size
	rows = (len(tiles) + cols - 1) // cols
	sheet = Image.new("RGB", (cols * tw, rows * th), (28, 26, 24))
	for i, tile in enumerate(tiles):
		x = (i % cols) * tw
		y = (i // cols) * th
		sheet.paste(tile.convert("RGB"), (x, y))
	sheet.save(OUT / "sheet_t1_t3.png")
	print(f"wrote {len(list(OUT.glob('*.png')))} previews to {OUT}")


if __name__ == "__main__":
	main()
