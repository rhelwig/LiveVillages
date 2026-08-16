#!/usr/bin/env python3
"""Paint 64/128/256 villager overlays natively and upscale base skins.

128 and 256 are not nearest-neighbor copies of 64. Each extra texel gets
weave, stitch, buttons, and a large job mark so professions read in-game.
"""

from __future__ import annotations

import shutil
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

from paint_profession_overlays import (
	CUSTOM,
	CUSTOM_Z,
	HAT_MCMETA,
	JOBS,
	VANILLA,
	VANILLA_Z,
	GOLD,
	SILVER,
	COPPER,
	BRASS,
	COBBLE,
	LEATHER,
	STRAW,
	JobLook,
	assert_face_open,
	cube_faces,
	jacket_colors,
	paint_job,
	shade,
	uses_apron,
	write_job,
)

ROOT = Path("/home/rhelwig/Projects/LiveVillages/src/main/resources/assets/live-villages/textures/entity")
ASSETS = Path("/home/rhelwig/Projects/LiveVillages/src/main/resources/assets")
JAR = Path(
	"/home/rhelwig/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
	"minecraft-clientonly-deobf/26.1.1/minecraft-clientonly-deobf-26.1.1.jar"
)
VANILLA_ENTITY_PREFIX = "assets/minecraft/textures/entity/"
ILLAGERS = ("pillager", "vindicator", "evoker", "illusioner")
RGBA = tuple[int, int, int, int]


def dest(scale: int, *parts: str) -> Path:
	path = ROOT.joinpath(f"scale{scale}", *parts)
	path.parent.mkdir(parents=True, exist_ok=True)
	return path


def put(im: Image.Image, x: int, y: int, color: RGBA) -> None:
	if 0 <= x < im.width and 0 <= y < im.height:
		im.putpixel((x, y), color)


def fill(im: Image.Image, x: int, y: int, w: int, h: int, color: RGBA) -> None:
	for yy in range(y, y + h):
		for xx in range(x, x + w):
			put(im, xx, yy, color)


def fill_weave(im: Image.Image, x: int, y: int, w: int, h: int, color: RGBA, period: int) -> None:
	period = max(2, period)
	for yy in range(y, y + h):
		for xx in range(x, x + w):
			# Soft cloth, not a loud checkerboard — folds read at a glance.
			fold = -14 if (yy - y) % (period * 5) < max(1, period // 2) else 0
			pore = 3 if ((xx // period) + (yy // (period * 2))) % 2 == 0 else -2
			put(im, xx, yy, shade(color, fold + pore))


def stitch_rect(im: Image.Image, x: int, y: int, w: int, h: int, color: RGBA, gap: int) -> None:
	gap = max(2, gap)
	for xx in range(x, x + w, gap):
		put(im, xx, y, color)
		put(im, xx, y + h - 1, color)
	for yy in range(y, y + h, gap):
		put(im, x, yy, color)
		put(im, x + w - 1, yy, color)


def scaled_job(scale: int, job: JobLook, tier: int) -> Image.Image:
	base = paint_job(job, tier)
	if scale == 64:
		return base
	factor = scale // 64
	im = base.resize((scale, scale), Image.NEAREST)
	robe, accent, trim, collar = jacket_colors(job, tier)
	period = max(2, factor)
	# re-paint jacket faces with weave so they are not 4x4 clones
	for name, (x, y, w, h) in cube_faces(0, 38, 8, 20, 6).items():
		sx, sy, sw, sh = x * factor, y * factor, w * factor, h * factor
		face = robe if name == "front" else shade(robe, -8 if name == "back" else 0)
		if name == "top":
			face = shade(robe, 12)
		fill_weave(im, sx, sy, sw, sh, face, period)
	# jacket front details
	fx, fy, fw, fh = 6 * factor, 44 * factor, 8 * factor, 20 * factor
	collar_h = (3 if tier >= 3 else 2) * factor
	fill_weave(im, fx, fy, fw, collar_h, collar, max(1, factor // 2))
	if uses_apron(job, tier):
		apron = accent if job.hat_style != "chef" else (244, 236, 214, 255)
		if job == JOBS_BAKER:
			apron = (244, 236, 214, 255)
		ay = fy + collar_h
		fill_weave(im, fx + factor, ay, fw - 2 * factor, fh - collar_h - 2 * factor, apron, period)
		stitch_rect(im, fx + factor, ay, fw - 2 * factor, fh - collar_h - 2 * factor, shade(apron, -24), factor)
		btn = SILVER if tier == 3 else GOLD if tier >= 4 else shade(apron, -30)
		for by in (ay + 2 * factor, ay + 6 * factor, ay + 10 * factor):
			if by < fy + fh - 2 * factor:
				fill(im, fx + 2 * factor, by, factor, factor, btn)
				fill(im, fx + fw - 3 * factor, by, factor, factor, btn)
	if job.sash_row is not None:
		sy = fy + job.sash_row * factor
		fill(im, fx, sy, fw, 2 * factor, accent)
		buckle = GOLD if tier >= 3 else shade(accent, 20)
		fill(im, fx + 3 * factor, sy, 2 * factor, 2 * factor, buckle)
		put(im, fx + 3 * factor, sy, shade(buckle, -40))
	# side piping
	if tier >= 2:
		fill(im, fx, fy, factor, fh, trim)
		fill(im, fx + fw - factor, fy, factor, fh, trim)
	if tier == 4:
		fill(im, fx + factor, fy + collar_h, factor, fh - collar_h - factor, (148, 58, 42, 255))
		fill(im, fx + fw - 2 * factor, fy + collar_h, factor, fh - collar_h - factor, (148, 58, 42, 255))
	paint_large_emblem(im, job, tier, factor)
	paint_native_uniform_details(im, job, tier, factor)
	refine_hat(im, job, tier, factor)
	if tier == 1:
		paint_scaled_tier_one_wear(im, job, factor)
	return im


def paint_native_uniform_details(im: Image.Image, job: JobLook, tier: int, f: int) -> None:
	"""Add details at the real HD pixel grid instead of logical 64px blocks."""
	name = next(n for n, look in ALL_JOBS.items() if look is job)
	fx, fy, fw, fh = 6 * f, 44 * f, 8 * f, 20 * f
	robe, accent, trim, collar = jacket_colors(job, tier)
	draw = ImageDraw.Draw(im)
	thread = shade(trim if tier >= 2 else robe, 30)
	shadow = shade(robe, -24)
	highlight = shade(robe, 18)
	thin = max(1, f // 2)

	# Fine tailoring: narrow folds, a center opening, and individually spaced
	# hem stitches. These are intentionally sub-logical-pixel at 128/256.
	draw.line((fx + fw // 2, fy + 3 * f, fx + fw // 2, fy + fh - 1), fill=shadow, width=thin)
	draw.line((fx + fw // 2 + thin, fy + 4 * f, fx + fw // 2 + thin, fy + fh - 2), fill=highlight, width=1)
	for x in range(fx + 1, fx + fw - 1, max(2, f)):
		put(im, x, fy + fh - 2, thread)

	# A true V-neck reads more like the tailored portrait collars than a flat
	# horizontal band, especially at 256 where the diagonal can be smooth.
	if name in {"gardener", "guard", "trademaster", "librarian", "forester"}:
		cx = fx + fw // 2
		draw.line((fx + thin, fy, cx, fy + 3 * f), fill=shade(collar, -18), width=thin)
		draw.line((fx + fw - thin - 1, fy, cx, fy + 3 * f), fill=shade(collar, 18), width=thin)

	if name == "trademaster":
		# Portrait-inspired merchant sash, stone edging, plaque and seal. Keep the
		# plaque free of tiny lettering: the circle is legible at game distance.
		stone_dark = shade(COBBLE, -38)
		sash = (184, 132, 62, 255)
		sash_light = shade(sash, 24)
		draw.polygon([
			(fx, fy + 2 * f), (fx + 2 * f, fy + f),
			(fx + fw, fy + 13 * f), (fx + fw, fy + 16 * f),
		], fill=sash)
		draw.line((fx + thin, fy + 2 * f, fx + fw - thin, fy + 14 * f), fill=sash_light, width=thin)
		# stone hem and center edging
		draw.line((fx, fy + fh - f, fx + fw - 1, fy + fh - f), fill=stone_dark, width=thin)
		for x in range(fx, fx + fw, max(2, f)):
			put(im, x, fy + fh - f, shade(COBBLE, 22))
		# brass plaque with dark inset and circular merchant seal
		px0, py0 = fx + fw // 2, fy + 3 * f
		px1, py1 = fx + fw - 1, fy + 8 * f
		draw.rectangle((px0, py0, px1, py1), fill=shade(GOLD, -16), outline=shade(GOLD, 32), width=thin)
		for rx, ry in ((px0 + 1, py0 + 1), (px1 - 1, py0 + 1), (px0 + 1, py1 - 1), (px1 - 1, py1 - 1)):
			put(im, rx, ry, shade(GOLD, -55))
		r = max(2, f)
		cx, cy = (px0 + px1) // 2, (py0 + py1) // 2
		draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=shade(GOLD, -70), width=thin)

	elif name == "gardener":
		# Two outlined flower pockets are the silhouette-independent identifier.
		pocket_y = fy + 12 * f
		pocket_w = 3 * f
		pocket_h = 6 * f
		for px in (fx + thin, fx + fw - pocket_w - thin):
			draw.rectangle((px, pocket_y, px + pocket_w, pocket_y + pocket_h), fill=shade(robe, -12), outline=thread, width=thin)
		flowers = ((220, 52, 58, 255), (236, 196, 48, 255), (186, 70, 168, 255))
		for index, (cx, cy) in enumerate(((fx + 2 * f, pocket_y + 2 * f), (fx + fw - 2 * f, pocket_y + 2 * f), (fx + 2 * f, pocket_y + 4 * f), (fx + fw - 2 * f, pocket_y + 4 * f))):
			petal = flowers[index % len(flowers)]
			put(im, cx, cy, petal)
			put(im, cx - 1, cy, shade(petal, 22))
			put(im, cx + 1, cy, shade(petal, -22))
			put(im, cx, cy - 1, petal)
			put(im, cx, cy + 1, (74, 138, 58, 255))

	elif name == "guard":
		# A fitted leather vest with diagonal fastening prevents the Guard from
		# reading as another green-robed worker.
		leather_dark = shade(LEATHER, -30)
		vest_top, vest_bottom = fy + 2 * f, fy + 12 * f
		draw.polygon([
			(fx + thin, vest_top), (fx + fw - thin - 1, vest_top),
			(fx + fw - f, vest_bottom), (fx + f, vest_bottom),
		], fill=LEATHER, outline=leather_dark)
		draw.line((fx + f, vest_top + f, fx + fw - f, vest_bottom - f), fill=leather_dark, width=thin)
		for step in range(1, 5):
			rx = fx + f + ((fw - 2 * f) * step) // 5
			ry = vest_top + f + ((vest_bottom - vest_top - 2 * f) * step) // 5
			put(im, rx, ry, shade(GOLD, 12))
		# clear belt and hollow square buckle
		belt_y = fy + 12 * f
		draw.rectangle((fx, belt_y, fx + fw - 1, belt_y + 2 * f), fill=leather_dark)
		bx0, by0 = fx + fw // 2 - f, belt_y
		draw.rectangle((bx0, by0, bx0 + 2 * f, by0 + 2 * f), outline=GOLD, width=thin)

	else:
		paint_profession_signature(im, draw, name, tier, f, fx, fy, fw, fh, robe, accent, trim, thread, thin)


def paint_profession_signature(im: Image.Image, draw: ImageDraw.ImageDraw, name: str, tier: int, f: int,
	fx: int, fy: int, fw: int, fh: int, robe: RGBA, accent: RGBA, trim: RGBA, thread: RGBA, thin: int) -> None:
	"""Portrait-derived, profession-specific HD marks for every remaining job."""
	metal = SILVER if tier == 3 else GOLD if tier >= 4 else COBBLE
	dark = shade(accent, -42)
	cx = fx + fw // 2
	for x in range(fx + thin, fx + fw - thin, max(2, f)):
		put(im, x, fy + 2 * f, thread)

	if name == "baker":
		apron = (242, 235, 214, 255)
		draw.rectangle((fx + f, fy + 3 * f, fx + fw - f - 1, fy + fh - 2 * f), fill=apron, outline=metal, width=thin)
		for y in (fy + 6 * f, fy + 10 * f):
			put(im, cx - f, y, (112, 166, 204, 255)); put(im, cx + f, y, (112, 166, 204, 255))
		draw.line((cx - 2 * f, fy + 14 * f, cx + 2 * f, fy + 17 * f), fill=(190, 154, 90, 255), width=thin)
		draw.line((cx + 2 * f, fy + 14 * f, cx - 2 * f, fy + 17 * f), fill=(190, 154, 90, 255), width=thin)
	elif name == "beekeeper":
		draw.rectangle((fx + f, fy + 4 * f, fx + fw - f - 1, fy + 15 * f), outline=metal, width=thin)
		for y in (fy + 6 * f, fy + 10 * f, fy + 14 * f):
			put(im, cx - 2 * f, y, GOLD); put(im, cx + 2 * f, y, GOLD)
		for ox, oy in ((-f, 16 * f), (f, 16 * f), (0, 15 * f)):
			draw.rectangle((cx + ox - thin, fy + oy - thin, cx + ox + thin, fy + oy + thin), outline=shade(GOLD, -20))
	elif name == "carpenter":
		draw.rectangle((fx + f, fy + 4 * f, fx + fw - f - 1, fy + fh - 2 * f), fill=LEATHER, outline=dark, width=thin)
		for x in (fx + f + 1, fx + fw - f - 2):
			for y in (fy + 5 * f, fy + 11 * f, fy + 17 * f): put(im, x, y, COPPER)
		draw.line((cx - 2 * f, fy + 10 * f, cx + 2 * f, fy + 10 * f), fill=metal, width=thin)
		draw.line((cx, fy + 8 * f, cx, fy + 15 * f), fill=metal, width=thin)
	elif name == "forester":
		draw.polygon([(fx, fy + 4 * f), (fx + f, fy + 3 * f), (fx + fw, fy + 14 * f), (fx + fw, fy + 17 * f)], fill=(66, 112, 48, 255))
		draw.line((cx, fy + 8 * f, cx, fy + 16 * f), fill=(92, 62, 34, 255), width=thin)
		draw.line((cx, fy + 11 * f, cx - 2 * f, fy + 9 * f), fill=(86, 146, 62, 255), width=thin)
		draw.line((cx, fy + 13 * f, cx + 2 * f, fy + 10 * f), fill=(86, 146, 62, 255), width=thin)
	elif name == "miner":
		draw.rectangle((fx + f, fy + 4 * f, fx + fw - f - 1, fy + 15 * f), fill=(54, 58, 62, 255), outline=metal, width=thin)
		draw.line((cx - 2 * f, fy + 7 * f, cx + 2 * f, fy + 13 * f), fill=COPPER, width=thin)
		draw.line((cx + 2 * f, fy + 7 * f, cx - 2 * f, fy + 13 * f), fill=COPPER, width=thin)
		draw.line((cx - 3 * f, fy + 7 * f, cx - f, fy + 6 * f), fill=shade(COPPER, 25), width=thin)
	elif name == "portmaster":
		for y in (fy + 6 * f, fy + 10 * f):
			put(im, cx - 2 * f, y, BRASS); put(im, cx + 2 * f, y, BRASS)
		draw.line((cx, fy + 13 * f, cx, fy + 18 * f), fill=BRASS, width=thin)
		draw.arc((cx - 2 * f, fy + 15 * f, cx + 2 * f, fy + 19 * f), 0, 180, fill=BRASS, width=thin)
	elif name == "roadwright":
		draw.polygon([(cx - 2 * f, fy + 4 * f), (cx + 2 * f, fy + 4 * f), (cx + 3 * f, fy + 18 * f), (cx - 3 * f, fy + 18 * f)], fill=(88, 78, 60, 255))
		for y in range(fy + 6 * f, fy + 18 * f, 3 * f):
			draw.line((cx, y, cx, y + f), fill=(210, 190, 126, 255), width=thin)
	elif name == "scribe":
		draw.rectangle((fx + 2 * f, fy + 6 * f, fx + fw - 2 * f, fy + 15 * f), fill=(238, 225, 190, 255), outline=(48, 70, 122, 255), width=thin)
		for y in (fy + 9 * f, fy + 12 * f): draw.line((cx - f, y, cx + 2 * f, y), fill=(86, 82, 76, 255), width=thin)
		draw.line((cx - 2 * f, fy + 16 * f, cx + 2 * f, fy + 8 * f), fill=(48, 70, 122, 255), width=thin)
	elif name == "farmer":
		draw.line((cx, fy + 7 * f, cx, fy + 17 * f), fill=STRAW, width=thin)
		for y, side in ((9, -1), (11, 1), (13, -1), (15, 1)):
			draw.line((cx, fy + y * f, cx + side * 2 * f, fy + (y - 2) * f), fill=STRAW, width=thin)
	elif name == "butcher":
		draw.rectangle((fx + f, fy + 4 * f, fx + fw - f - 1, fy + fh - 2 * f), fill=(112, 38, 46, 255), outline=dark, width=thin)
		draw.polygon([(cx - 2 * f, fy + 8 * f), (cx + 2 * f, fy + 8 * f), (cx + f, fy + 13 * f), (cx - 2 * f, fy + 13 * f)], fill=metal)
		draw.line((cx, fy + 13 * f, cx + 2 * f, fy + 17 * f), fill=LEATHER, width=max(thin, f))
	elif name == "fisherman":
		for y in range(fy + 5 * f, fy + 16 * f, 2 * f): draw.line((fx + f, y, fx + fw - f, y + 3 * f), fill=shade(robe, 20), width=1)
		draw.polygon([(cx - 3 * f, fy + 11 * f), (cx, fy + 9 * f), (cx + 2 * f, fy + 11 * f), (cx, fy + 13 * f)], fill=(180, 166, 116, 255))
		draw.arc((cx, fy + 13 * f, cx + 3 * f, fy + 18 * f), 0, 220, fill=metal, width=thin)
	elif name == "shepherd":
		draw.rectangle((fx + f, fy + 4 * f, fx + fw - f - 1, fy + 17 * f), fill=(226, 220, 198, 255), outline=metal, width=thin)
		draw.line((cx, fy + 8 * f, cx, fy + 18 * f), fill=(118, 84, 52, 255), width=thin)
		draw.arc((cx - 2 * f, fy + 6 * f, cx + 2 * f, fy + 10 * f), 160, 350, fill=(118, 84, 52, 255), width=thin)
	elif name == "mason":
		draw.rectangle((fx + f, fy + 5 * f, fx + fw - f - 1, fy + 17 * f), fill=(112, 110, 106, 255), outline=metal, width=thin)
		for y in range(fy + 7 * f, fy + 17 * f, 3 * f):
			draw.line((fx + f, y, fx + fw - f, y), fill=(76, 74, 72, 255), width=1)
			for x in range(fx + (2 if ((y // f) % 2) else 1) * f, fx + fw - f, 3 * f): draw.line((x, y - 3 * f, x, y), fill=(76, 74, 72, 255), width=1)
	elif name == "fletcher":
		draw.line((fx, fy + 3 * f, fx + fw, fy + 14 * f), fill=LEATHER, width=max(f, thin))
		draw.line((fx + fw, fy + 3 * f, fx, fy + 14 * f), fill=LEATHER, width=max(f, thin))
		draw.polygon([(cx, fy + 8 * f), (cx - f, fy + 11 * f), (cx + f, fy + 11 * f)], fill=metal)
		for x in (cx - 2 * f, cx + 2 * f):
			draw.line((x, fy + 14 * f, x, fy + 18 * f), fill=(224, 216, 190, 255), width=thin)
			draw.line((x, fy + 15 * f, x - f, fy + 16 * f), fill=shade(robe, -30), width=1)
	elif name == "leatherworker":
		draw.rectangle((fx + f, fy + 4 * f, fx + fw - f - 1, fy + fh - 2 * f), fill=LEATHER, outline=dark, width=thin)
		draw.rectangle((fx + 2 * f, fy + 9 * f, cx, fy + 14 * f), fill=shade(LEATHER, 18), outline=thread, width=1)
		for y in range(fy + 5 * f, fy + 17 * f, 2 * f): put(im, fx + fw - f - 2, y, thread)
	elif name == "armorer":
		draw.polygon([(fx + f, fy + 4 * f), (fx + fw - f, fy + 4 * f), (fx + fw - 2 * f, fy + 16 * f), (fx + 2 * f, fy + 16 * f)], fill=(84, 86, 90, 255), outline=metal)
		draw.ellipse((cx - 2 * f, fy + 8 * f, cx + 2 * f, fy + 12 * f), fill=shade(metal, -20), outline=shade(metal, 24), width=thin)
	elif name == "toolsmith":
		draw.rectangle((fx + f, fy + 5 * f, fx + fw - f - 1, fy + 17 * f), fill=(116, 78, 42, 255), outline=metal, width=thin)
		draw.line((cx - 2 * f, fy + 8 * f, cx + 2 * f, fy + 15 * f), fill=metal, width=thin)
		draw.line((cx + 2 * f, fy + 8 * f, cx - 2 * f, fy + 15 * f), fill=COPPER, width=thin)
		draw.line((cx + f, fy + 8 * f, cx + 3 * f, fy + 8 * f), fill=COPPER, width=max(thin, f))
	elif name == "weaponsmith":
		draw.polygon([(fx, fy + 3 * f), (fx + f, fy + 2 * f), (fx + fw, fy + 14 * f), (fx + fw, fy + 17 * f)], fill=(148, 40, 40, 255))
		draw.line((cx, fy + 7 * f, cx, fy + 16 * f), fill=metal, width=thin)
		draw.line((cx - 2 * f, fy + 11 * f, cx + 2 * f, fy + 11 * f), fill=GOLD if tier >= 4 else COPPER, width=thin)
	elif name == "cleric":
		draw.polygon([(fx + f, fy + 3 * f), (fx + 3 * f, fy + 3 * f), (cx + f, fy + 18 * f), (cx - f, fy + 18 * f)], fill=shade(GOLD, -12))
		draw.ellipse((cx - 2 * f, fy + 8 * f, cx + 2 * f, fy + 12 * f), outline=GOLD, width=thin)
		draw.line((cx, fy + 8 * f, cx, fy + 12 * f), fill=GOLD, width=thin)
		draw.line((cx - 2 * f, fy + 10 * f, cx + 2 * f, fy + 10 * f), fill=GOLD, width=thin)
	elif name == "librarian":
		draw.polygon([(fx + f, fy + 7 * f), (cx, fy + 8 * f), (cx, fy + 15 * f), (fx + f, fy + 14 * f)], fill=(224, 204, 150, 255), outline=metal)
		draw.polygon([(cx, fy + 8 * f), (fx + fw - f, fy + 7 * f), (fx + fw - f, fy + 14 * f), (cx, fy + 15 * f)], fill=(236, 218, 164, 255), outline=metal)
		for y in (fy + 10 * f, fy + 12 * f):
			draw.line((fx + 2 * f, y, cx - 1, y), fill=(110, 82, 62, 255)); draw.line((cx + 1, y, fx + fw - 2 * f, y), fill=(110, 82, 62, 255))
	elif name == "cartographer":
		draw.rectangle((fx + f, fy + 6 * f, fx + fw - f - 1, fy + 16 * f), fill=(226, 210, 164, 255), outline=metal, width=thin)
		draw.line((fx + 2 * f, fy + 8 * f, cx, fy + 12 * f, fx + fw - 2 * f, fy + 9 * f), fill=(74, 128, 166, 255), width=thin)
		draw.line((fx + 2 * f, fy + 14 * f, fx + fw - 2 * f, fy + 11 * f), fill=(116, 78, 42, 255), width=thin)
		draw.polygon([(cx, fy + 7 * f), (cx - f, fy + 10 * f), (cx + f, fy + 10 * f)], fill=(148, 40, 40, 255))


def accent_for_hd(name: str, tier: int, fallback: RGBA) -> RGBA:
	if name in {"miner", "carpenter", "fletcher"}:
		return COPPER if tier < 4 else GOLD
	if tier == 3:
		return SILVER
	if tier >= 4:
		return GOLD
	return shade(fallback, 18)


# late bind after import
from paint_profession_overlays import JOBS as ALL_JOBS

JOBS_BAKER = ALL_JOBS["baker"]


def paint_scaled_tier_one_wear(im: Image.Image, job: JobLook, f: int) -> None:
	name = next(n for n, look in ALL_JOBS.items() if look is job)
	patch = shade(job.robe, -38)
	thread = shade(job.accent, 26)
	fill_weave(im, 7 * f, 56 * f, 3 * f, 3 * f, patch, max(1, f // 2))
	stitch_rect(im, 7 * f, 56 * f, 3 * f, 3 * f, thread, f)
	# Rough cuffs, grime, and several transparent nicks in the lower hem.
	fill(im, 49 * f, 27 * f, 2 * f, f, shade(job.robe, -48))
	if name == "baker":
		stain = (174, 158, 126, 255)
	elif name in {"farmer", "forester", "gardener", "shepherd"}:
		stain = (82, 68, 42, 255)
	elif name in {"miner", "mason", "armorer", "toolsmith", "weaponsmith"}:
		stain = (58, 58, 56, 255)
	elif name in {"fisherman", "portmaster"}:
		stain = (108, 116, 118, 255)
	elif name in {"butcher", "leatherworker"}:
		stain = (92, 48, 38, 255)
	else:
		stain = shade(job.robe, -46)
	fill(im, 10 * f, 59 * f, 3 * f, f, stain)
	for x, y in ((6, 63), (8, 62), (11, 63), (13, 62)):
		fill(im, x * f, y * f, f, f, (0, 0, 0, 0))
	if name == "trademaster":
		fill(im, 12 * f, 53 * f, 2 * f, 2 * f, patch)
		fill(im, 13 * f, 54 * f, f, f, thread)
	if uses_apron(job, 1):
		fill(im, 8 * f, 52 * f, f, f, stain)
		fill(im, 11 * f, 55 * f, f, f, shade(stain, 12))
	if job.hat and job.hat_style not in {"none", "", "hood"}:
		fill(im, 42 * f, 9 * f, f, f, shade(job.hat, 28))
		fill(im, 43 * f, 10 * f, f, f, shade(job.hat, -36))
		fill(im, 44 * f, 9 * f, f, f, shade(job.hat, 28))


def paint_large_emblem(im: Image.Image, job: JobLook, tier: int, f: int) -> None:
	name = next(n for n, look in ALL_JOBS.items() if look is job)
	fx, fy = 6 * f, 44 * f
	fw, fh = 8 * f, 20 * f
	cx = fx + 4 * f
	cy = fy + (9 * f if uses_apron(job, tier) else 8 * f)
	mark = job.accent if tier < 4 else GOLD
	dark = shade(mark, -40)
	if name == "trademaster":
		fill(im, fx, fy, fw, 3 * f, COBBLE)
		fill(im, fx + f, fy + 4 * f, 6 * f, 4 * f, GOLD)
		fill(im, fx + 2 * f, fy + 5 * f, 4 * f, 2 * f, dark)
		return
	if name == "gardener":
		fill(im, fx + f, fy + 13 * f, 2 * f, 3 * f, (220, 52, 58, 255))
		fill(im, fx + 5 * f, fy + 13 * f, 2 * f, 3 * f, (236, 196, 48, 255))
		fill(im, fx + f, fy + 15 * f, 2 * f, f, (186, 70, 168, 255))
		fill(im, fx + 5 * f, fy + 15 * f, 2 * f, f, (90, 176, 72, 255))
		return
	if name == "guard":
		fill(im, fx + f, fy + 3 * f, fw - 2 * f, 7 * f, LEATHER)
		fill(im, fx + 3 * f, fy + 6 * f, 2 * f, 2 * f, GOLD)
		fill(im, fx, fy, fw, f, COBBLE)
		return
	if name == "librarian":
		fill(im, fx, fy, fw, 3 * f, (188, 166, 110, 255))
		fill(im, fx + 2 * f, fy + 8 * f, 4 * f, 4 * f, GOLD)
		return
	if name == "beekeeper":
		for y in (fy + 5 * f, fy + 8 * f, fy + 11 * f):
			fill(im, fx + 2 * f, y, f, f, GOLD)
			fill(im, fx + 5 * f, y, f, f, GOLD)
		return
	if name in {"farmer", "shepherd"}:
		fill(im, cx, cy - 3 * f, f, 4 * f, shade(mark, 20))
		fill(im, cx - 2 * f, cy - 3 * f, 2 * f, f, mark)
		fill(im, cx + f, cy - 2 * f, 2 * f, f, mark)
	elif name == "baker":
		fill(im, cx - 2 * f, cy, 5 * f, f, (236, 208, 140, 255))
		fill(im, cx, cy - 2 * f, f, 5 * f, (210, 180, 110, 255))
	elif name == "butcher":
		fill(im, cx - 2 * f, cy - f, 5 * f, f, dark)
		fill(im, cx, cy, f, 3 * f, mark)
	elif name == "fisherman":
		fill(im, cx - 2 * f, cy, 5 * f, f, mark)
		fill(im, cx + 2 * f, cy - f, f, f, mark)
		fill(im, cx - 2 * f, cy + f, f, f, dark)
	elif name == "shepherd":
		fill(im, cx - 2 * f, cy - f, 5 * f, 3 * f, (236, 232, 220, 255))
		fill(im, cx, cy, f, f, dark)
	elif name == "beekeeper":
		fill(im, cx - 2 * f, cy - f, 5 * f, 3 * f, (232, 188, 64, 255))
		for dy in range(0, 3 * f, f):
			fill(im, cx - 2 * f, cy - f + dy, 5 * f, 1, dark)
	elif name in {"forester", "carpenter"}:
		fill(im, cx, cy - 2 * f, f, 5 * f, (92, 64, 36, 255))
		fill(im, cx - 2 * f, cy - 3 * f, 5 * f, 2 * f, mark)
	elif name == "miner":
		fill(im, cx - 3 * f, cy, 7 * f, f, COPPER if tier < 4 else GOLD)
		fill(im, cx, cy - 3 * f, f, 4 * f, dark)
	elif name == "mason":
		fill(im, cx - 2 * f, cy - f, 5 * f, 3 * f, (140, 136, 128, 255))
		fill(im, cx - 2 * f, cy - f, 5 * f, f, dark)
	elif name == "roadwright":
		fill(im, cx - 3 * f, cy, 7 * f, f, (92, 82, 62, 255))
		fill(im, cx - 2 * f, cy - f, 5 * f, f, mark)
	elif name == "guard":
		fill(im, cx - 2 * f, cy - 2 * f, 5 * f, 5 * f, mark)
		fill(im, cx, cy - f, f, 3 * f, dark)
	elif name in {"armorer", "toolsmith", "weaponsmith"}:
		fill(im, cx - 2 * f, cy, 5 * f, f, mark)
		fill(im, cx, cy - 2 * f, f, 5 * f, mark)
	elif name == "fletcher":
		fill(im, cx, cy - 3 * f, f, 6 * f, (188, 160, 96, 255))
		fill(im, cx - 2 * f, cy - 3 * f, 5 * f, f, mark)
	elif name == "leatherworker":
		fill(im, cx - 2 * f, cy - f, 5 * f, 3 * f, LEATHER)
		stitch_rect(im, cx - 2 * f, cy - f, 5 * f, 3 * f, dark, f)
	elif name == "cleric":
		fill(im, cx - 2 * f, cy, 5 * f, f, GOLD)
		fill(im, cx, cy - 2 * f, f, 5 * f, GOLD)
	elif name == "librarian":
		fill(im, cx - 2 * f, cy - 2 * f, 5 * f, 5 * f, (92, 40, 48, 255))
		fill(im, cx - 2 * f, cy, 5 * f, f, mark)
	elif name == "cartographer":
		fill(im, cx - 2 * f, cy - 2 * f, 5 * f, 5 * f, (196, 176, 124, 255))
		put(im, cx, cy, dark)
	elif name == "scribe":
		fill(im, cx - 2 * f, cy - 2 * f, 5 * f, 5 * f, (232, 220, 188, 255))
		fill(im, cx - 2 * f, cy, 5 * f, f, (48, 70, 122, 255))
	elif name == "trademaster":
		fill(im, cx - 2 * f, cy - f, 5 * f, 3 * f, GOLD)
		fill(im, cx, cy, f, f, dark)
	elif name == "portmaster":
		fill(im, cx - 2 * f, cy - 2 * f, 5 * f, f, BRASS)
		fill(im, cx, cy - f, f, 4 * f, BRASS)


def refine_hat(im: Image.Image, job: JobLook, tier: int, f: int) -> None:
	if not job.hat or job.hat_style in ("none", ""):
		return
	faces = cube_faces(32, 0, 8, 10, 8)
	color = shade(job.hat, -12 if tier == 1 else 0)
	if job.hat_style == "pointed":
		tx, ty, tw, th = faces["top"]
		fill_weave(im, tx * f, ty * f, tw * f, th * f, shade(color, 14), max(1, f // 2))
		band = shade(color, -26) if tier < 3 else SILVER if tier == 3 else GOLD
		for side in ("right", "front", "left", "back"):
			x, y, w, h = faces[side]
			fill_weave(im, x * f, y * f, w * f, min(4 * f, h * f), color, max(1, f // 2))
			fill(im, x * f, (y + 3) * f, w * f, max(1, f // 2), band)
		fx0, fy0, fw0, _ = faces["front"]
		draw = ImageDraw.Draw(im)
		draw.line((fx0 * f + f, fy0 * f + f, (fx0 + fw0 - 1) * f, fy0 * f + 3 * f), fill=shade(color, 30), width=max(1, f // 2))
		red = (190, 48, 42, 255) if tier < 4 else (220, 58, 48, 255)
		for lx, ly in ((35, 9), (36, 8), (37, 7), (38, 6), (39, 5), (55, 9), (54, 8), (53, 7)):
			fill(im, lx * f, ly * f, max(1, f // 2), f, red)
			put(im, lx * f + max(1, f // 2), ly * f, shade(red, 28))
		return
	if job.hat_style == "brim":
		# straw / felt texture on the brim disc
		for y in range(48 * f, 64 * f):
			for x in range(31 * f, 47 * f):
				cx, cy = 31 * f + 8 * f - 0.5, 48 * f + 8 * f - 0.5
				dx, dy = x - cx, y - cy
				r2 = dx * dx + dy * dy
				rad = 8 * f
				if r2 <= rad * rad:
					edge = r2 >= (6.2 * f) * (6.2 * f)
					tone = -24 if edge else (6 if (x + y) % max(2, f) == 0 else 0)
					put(im, x, y, shade(color, tone))
	if job.hat_style in {"beanie", "chef", "helmet", "brim"}:
		tx, ty, tw, th = faces["top"]
		fill_weave(im, tx * f, ty * f, tw * f, th * f, shade(color, 16), max(1, f // 2))
		for side in ("right", "front", "left", "back"):
			x, y, w, h = faces[side]
			fill_weave(im, x * f, y * f, w * f, 4 * f, color, max(1, f // 2))


def write_scaled_overlays(scale: int, names: list[str] | None = None) -> None:
	selected = names or list(ALL_JOBS)
	for name in selected:
		job = ALL_JOBS[name]
		for kind in ("villager", "zombie_villager"):
			for tier in (1, 2, 3, 4):
				image = scaled_job(scale, job, tier)
				filename = f"{name}.png" if tier == 1 else f"{name}_tier{tier}.png"
				path = dest(scale, kind, "profession", filename)
				image.save(path)
				meta = path.with_suffix(path.suffix + ".mcmeta")
				if job.hat_style not in ("none", ""):
					meta.write_text(HAT_MCMETA)
				else:
					meta.unlink(missing_ok=True)


def upscale_vanilla(scale: int) -> None:
	factor = scale // 64
	with zipfile.ZipFile(JAR) as archive:
		for name in archive.namelist():
			if not name.startswith(VANILLA_ENTITY_PREFIX) or not name.endswith(".png"):
				continue
			rest = name[len(VANILLA_ENTITY_PREFIX) :]
			if not (
				rest.startswith("villager/")
				or rest.startswith("zombie_villager/")
				or rest.startswith("illager/")
			):
				continue
			if rest.startswith("illager/") and Path(rest).stem not in ILLAGERS:
				continue
			if "/profession/" in rest:
				continue
			image = Image.open(archive.open(name)).convert("RGBA")
			hd = image.resize((image.width * factor, image.height * factor), Image.NEAREST)
			# add a light skin-pore dither so faces are not flat 4x4 blocks
			if rest.startswith("villager/type/") or rest.startswith("zombie_villager/type/") or rest.endswith("villager.png"):
				px = hd.load()
				for y in range(hd.height):
					for x in range(hd.width):
						r, g, b, a = px[x, y]
						if a == 0:
							continue
						tone = 4 if (x // max(1, factor // 2) + y // max(1, factor // 2)) % 2 == 0 else -3
						px[x, y] = (
							max(0, min(255, r + tone)),
							max(0, min(255, g + tone)),
							max(0, min(255, b + tone)),
							a,
						)
			hd.save(dest(scale, rest))


def write_illager_tiers(scale: int) -> None:
	f = scale // 64
	for name in ("pillager", "vindicator", "evoker"):
		base_path = dest(scale, "illager", f"{name}.png")
		if not base_path.exists():
			continue
		source = Image.open(base_path).convert("RGBA")
		for tier, shift in ((2, 8), (3, 16), (4, 24)):
			tinted = source.copy()
			px = tinted.load()
			trim = SILVER if tier == 3 else GOLD
			for y in range(tinted.height):
				for x in range(tinted.width):
					r, g, b, a = px[x, y]
					if a == 0:
						continue
					px[x, y] = (
						min(255, r + shift // 2),
						min(255, g + shift // 3),
						min(255, b + shift // 6),
						a,
					)
			fill(tinted, 20 * f, 26 * f, 6 * f, 4 * f, trim)
			stitch_rect(tinted, 20 * f, 26 * f, 6 * f, 4 * f, shade(trim, -40), f)
			tinted.save(dest(scale, "illager", f"{name}_tier{tier}.png"))


def main() -> None:
	if not JAR.exists():
		raise SystemExit(f"missing minecraft jar: {JAR}")
	names = sys.argv[1:] or list(ALL_JOBS)
	unknown = [name for name in names if name not in ALL_JOBS]
	if unknown:
		raise SystemExit(f"unknown jobs: {', '.join(unknown)}")
	print(f"refreshing 64x64 overlays for {', '.join(names)}")
	for name in names:
		job = ALL_JOBS[name]
		write_job(name, job)
	for scale in (128, 256):
		scale_root = ROOT / f"scale{scale}"
		if len(names) == len(ALL_JOBS) and scale_root.exists():
			shutil.rmtree(scale_root)
		write_scaled_overlays(scale, names)
		if len(names) == len(ALL_JOBS):
			upscale_vanilla(scale)
			write_illager_tiers(scale)
		print(f"wrote native-detail scale {scale}")


if __name__ == "__main__":
	main()
