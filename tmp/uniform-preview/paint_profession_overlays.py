#!/usr/bin/env python3
"""Paint civic-tier villager profession overlays onto the real 64x64 UVs.

Hat rule (vanilla farmer / librarian):
  The profession hat cube wraps the whole head. Painting its front face
  covers the eyes. Only the crown (top + the first 4 side rows) may be
  opaque. Hoods may frame the face, but the eye window stays empty.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import sys

from PIL import Image

ROOT = Path("/home/rhelwig/Projects/LiveVillages/src/main/resources/assets")
CUSTOM = ROOT / "live-villages/textures/entity/villager/profession"
CUSTOM_Z = ROOT / "live-villages/textures/entity/zombie_villager/profession"
VANILLA = ROOT / "minecraft/textures/entity/villager/profession"
VANILLA_Z = ROOT / "minecraft/textures/entity/zombie_villager/profession"

RGBA = tuple[int, int, int, int]

# Hat cube at texOffs(32, 0), 8x10x8. Front is (40, 8) 8x10.
# Vanilla farmer paints only y=8..11 of those sides (4 rows).
HAT_U, HAT_V, HAT_W, HAT_H, HAT_D = 32, 0, 8, 10, 8
HAT_FRONT = (HAT_U + HAT_D, HAT_V + HAT_D)  # (40, 8)
CROWN_ROWS = 4
# Eye window on hat front: skip the lower 6 rows (y=12..17).
EYE_ROW0 = HAT_V + HAT_D + CROWN_ROWS  # 12


def rgb(r: int, g: int, b: int, a: int = 255) -> RGBA:
	return (r, g, b, a)


def shade(color: RGBA, delta: int) -> RGBA:
	return (
		max(0, min(255, color[0] + delta)),
		max(0, min(255, color[1] + delta)),
		max(0, min(255, color[2] + delta)),
		color[3],
	)


def mix(a: RGBA, b: RGBA, t: float) -> RGBA:
	return (
		int(a[0] + (b[0] - a[0]) * t),
		int(a[1] + (b[1] - a[1]) * t),
		int(a[2] + (b[2] - a[2]) * t),
		255,
	)


COBBLE = rgb(122, 118, 112)
SILVER = rgb(176, 180, 188)
GOLD = rgb(212, 168, 58)
BRICK = rgb(148, 58, 42)
LEATHER = rgb(118, 74, 42)
STRAW = rgb(196, 164, 64)
WHITE = rgb(244, 244, 238)
NAVY = rgb(36, 72, 104)
BRASS = rgb(204, 156, 52)
COPPER = rgb(184, 112, 54)
CREAM = rgb(228, 222, 208)
VEIL = rgb(214, 216, 212)


@dataclass(frozen=True)
class JobLook:
	robe: RGBA
	accent: RGBA
	hat: RGBA | None = None
	# crown: top + 4 side rows
	# brim: crown + straw/felt brim disc (farmer, portmaster)
	# chef: taller-reading white crown, optional band
	# beanie: dark crown, no brim
	# helmet: crown + lamp on the front
	# hood: wrap sides/back, open face window
	hat_style: str = "none"
	apron: bool = False
	sash_row: int | None = 11
	namespace: str = "live-villages"
	# drop the apron at this tier and above (dress-coat look)
	apron_until: int = 4


JOBS: dict[str, JobLook] = {
	"baker": JobLook(WHITE, rgb(130, 176, 214), hat=WHITE, hat_style="chef", sash_row=None, apron=True),
	"beekeeper": JobLook(WHITE, rgb(188, 190, 186), hat=VEIL, hat_style="hood", sash_row=None),
	"carpenter": JobLook(rgb(176, 128, 72), LEATHER, hat=rgb(120, 78, 42), hat_style="beanie", apron=True, sash_row=None),
	"forester": JobLook(rgb(118, 82, 48), rgb(68, 112, 48), hat=None, hat_style="none", sash_row=10),
	"gardener": JobLook(rgb(86, 132, 64), rgb(186, 86, 110), hat=None, hat_style="none", sash_row=12),
	"guard": JobLook(rgb(38, 86, 52), LEATHER, hat=None, hat_style="none", sash_row=11),
	"miner": JobLook(rgb(72, 78, 84), COPPER, hat=rgb(64, 68, 74), hat_style="helmet", sash_row=12),
	"portmaster": JobLook(NAVY, BRASS, hat=NAVY, hat_style="brim", sash_row=12),
	"roadwright": JobLook(rgb(156, 140, 96), rgb(92, 82, 62), hat=rgb(92, 82, 62), hat_style="beanie", sash_row=11),
	"scribe": JobLook(rgb(222, 210, 184), rgb(48, 70, 122), hat=rgb(48, 70, 122), hat_style="beanie", sash_row=11),
	"trademaster": JobLook(rgb(28, 118, 72), GOLD, hat=None, hat_style="none", sash_row=11),
	"farmer": JobLook(rgb(118, 82, 52), STRAW, hat=STRAW, hat_style="brim", sash_row=12, namespace="minecraft"),
	"butcher": JobLook(rgb(92, 58, 42), rgb(140, 42, 48), hat=rgb(92, 58, 42), hat_style="beanie", apron=True, sash_row=None, apron_until=2, namespace="minecraft"),
	"fisherman": JobLook(rgb(46, 122, 132), rgb(164, 140, 96), hat=rgb(46, 48, 50), hat_style="beanie", sash_row=13, namespace="minecraft"),
	"shepherd": JobLook(rgb(214, 204, 176), rgb(118, 84, 52), hat=rgb(214, 204, 176), hat_style="brim", sash_row=11, namespace="minecraft"),
	"mason": JobLook(rgb(126, 122, 116), rgb(88, 86, 82), hat=rgb(126, 122, 116), hat_style="helmet", sash_row=12, namespace="minecraft"),
	"fletcher": JobLook(rgb(92, 88, 48), rgb(120, 74, 40), hat=rgb(92, 88, 48), hat_style="pointed", sash_row=12, namespace="minecraft"),
	"leatherworker": JobLook(rgb(164, 116, 70), LEATHER, hat=LEATHER, hat_style="beanie", apron=True, sash_row=None, namespace="minecraft"),
	"armorer": JobLook(rgb(72, 72, 76), rgb(48, 48, 50), hat=rgb(72, 72, 76), hat_style="helmet", sash_row=12, namespace="minecraft"),
	"toolsmith": JobLook(rgb(90, 90, 96), rgb(124, 82, 42), hat=rgb(90, 90, 96), hat_style="beanie", sash_row=11, namespace="minecraft"),
	"weaponsmith": JobLook(rgb(64, 70, 76), rgb(148, 40, 40), hat=rgb(64, 70, 76), hat_style="helmet", sash_row=11, namespace="minecraft"),
	"cleric": JobLook(rgb(112, 58, 142), GOLD, hat=rgb(80, 40, 110), hat_style="hood", sash_row=13, namespace="minecraft"),
	"librarian": JobLook(rgb(132, 46, 62), rgb(188, 166, 110), hat=None, hat_style="none", sash_row=12, namespace="minecraft"),
	"cartographer": JobLook(rgb(188, 166, 124), rgb(92, 70, 42), hat=rgb(188, 166, 124), hat_style="brim", sash_row=12, namespace="minecraft"),
}

HAT_MCMETA = '{"villager":{"hat":"full"}}\n'


def new_atlas() -> Image.Image:
	return Image.new("RGBA", (64, 64), (0, 0, 0, 0))


def put(im: Image.Image, x: int, y: int, color: RGBA) -> None:
	if 0 <= x < 64 and 0 <= y < 64:
		im.putpixel((x, y), color)


def fill_rect(im: Image.Image, x: int, y: int, w: int, h: int, color: RGBA) -> None:
	for yy in range(y, y + h):
		for xx in range(x, x + w):
			put(im, xx, yy, color)


def cube_faces(u: int, v: int, w: int, h: int, d: int) -> dict[str, tuple[int, int, int, int]]:
	return {
		"top": (u + d, v, w, d),
		"bottom": (u + d + w, v, w, d),
		"right": (u, v + d, d, h),
		"front": (u + d, v + d, w, h),
		"left": (u + d + w, v + d, d, h),
		"back": (u + d + w + d, v + d, w, h),
	}


def fill_cube_faces(im: Image.Image, u: int, v: int, w: int, h: int, d: int, colors: dict[str, RGBA]) -> None:
	for name, (x, y, fw, fh) in cube_faces(u, v, w, h, d).items():
		fill_rect(im, x, y, fw, fh, colors.get(name, colors["fill"]))


def paint_crown(im: Image.Image, color: RGBA, rows: int = CROWN_ROWS, band: RGBA | None = None) -> None:
	"""Hat top + the first `rows` pixels of each side. Face stays open."""
	dark = shade(color, -24)
	light = shade(color, 18)
	faces = cube_faces(HAT_U, HAT_V, HAT_W, HAT_H, HAT_D)
	tx, ty, tw, th = faces["top"]
	fill_rect(im, tx, ty, tw, th, light)
	side_colors = {
		"right": shade(color, -10),
		"front": color,
		"left": shade(color, -10),
		"back": dark,
	}
	for name in ("right", "front", "left", "back"):
		x, y, fw, fh = faces[name]
		fill_rect(im, x, y, fw, min(rows, fh), side_colors[name])
	if band:
		band_y = HAT_V + HAT_D + max(0, rows - 2)
		for name in ("right", "front", "left", "back"):
			x, y, fw, fh = faces[name]
			fill_rect(im, x, band_y, fw, 2, band)


def paint_brim(im: Image.Image, color: RGBA) -> None:
	"""hat_rim front disc at texOffs(30, 47) → front face (31, 48) 16x16."""
	dark = shade(color, -28)
	cx, cy = 31 + 7.5, 48 + 7.5
	for y in range(48, 64):
		for x in range(31, 47):
			dx, dy = x - cx, y - cy
			r2 = dx * dx + dy * dy
			if r2 <= 8.0 * 8.0:
				edge = r2 >= 6.4 * 6.4
				put(im, x, y, dark if edge else color)


def paint_pointed_cap(im: Image.Image, color: RGBA, tier: int) -> None:
	"""Suggest a forward-pointed cap and red feather on the fixed villager hat UV."""
	paint_crown(im, color, CROWN_ROWS, SILVER if tier == 3 else GOLD if tier == 4 else None)
	fx, fy = HAT_FRONT
	# A descending highlight makes the front read as a folded point.
	for offset, width in enumerate((6, 4, 2)):
		fill_rect(im, fx + 1, fy + offset, width, 1, shade(color, 18))
	# The model cannot add feather geometry, so paint a bold feather across
	# the side/back crown faces where it remains visible in normal views.
	red = rgb(176, 46, 42) if tier < 4 else rgb(204, 54, 46)
	for x, y in ((35, 9), (36, 8), (37, 7), (38, 6), (39, 5), (55, 9), (54, 8), (53, 7)):
		put(im, x, y, red)
		if y + 1 < EYE_ROW0:
			put(im, x, y + 1, shade(red, -28))


def paint_hood(im: Image.Image, color: RGBA, frame: RGBA) -> None:
	"""Wrap the head on top/sides/back; leave a face window for eyes."""
	dark = shade(color, -20)
	light = shade(color, 14)
	faces = cube_faces(HAT_U, HAT_V, HAT_W, HAT_H, HAT_D)
	tx, ty, tw, th = faces["top"]
	fill_rect(im, tx, ty, tw, th, light)
	for name, col in (("right", shade(color, -6)), ("left", shade(color, -6)), ("back", dark)):
		x, y, fw, fh = faces[name]
		fill_rect(im, x, y, fw, fh, col)
	fx, fy, fw, fh = faces["front"]
	# top bar, chin bar, side posts — window is columns 1..6, rows 2..7
	fill_rect(im, fx, fy, fw, 2, frame)
	fill_rect(im, fx, fy + fh - 2, fw, 2, frame)
	fill_rect(im, fx, fy, 1, fh, frame)
	fill_rect(im, fx + fw - 1, fy, 1, fh, frame)


def paint_helmet_lamp(im: Image.Image, tier: int) -> None:
	fx, fy = HAT_FRONT
	lamp = rgb(220, 210, 160) if tier < 3 else rgb(236, 228, 176)
	rim = COPPER if tier < 4 else GOLD
	put(im, fx + 3, fy + 1, rim)
	put(im, fx + 4, fy + 1, rim)
	put(im, fx + 3, fy + 2, lamp)
	put(im, fx + 4, fy + 2, lamp)


def paint_hat_for_job(im: Image.Image, job: JobLook, tier: int) -> None:
	if not job.hat or job.hat_style in ("none", ""):
		return
	color = shade(job.hat, -12 if tier == 1 else 0)
	if job.hat_style == "hood":
		frame = shade(job.accent, 8 if tier >= 3 else -8)
		if tier >= 3:
			frame = SILVER if tier == 3 else mix(SILVER, GOLD, 0.45)
		paint_hood(im, color, frame)
		return
	if job.hat_style == "pointed":
		paint_pointed_cap(im, color, tier)
		return
	band = None
	if job.hat_style == "brim" and job == JOBS["farmer"]:
		band = LEATHER
	elif job.hat_style == "brim" and job == JOBS["portmaster"]:
		band = BRASS if tier >= 2 else shade(BRASS, -20)
	elif job.hat_style == "chef" and tier >= 2:
		band = job.accent
	elif job.hat_style == "beanie" and tier >= 3:
		band = SILVER if tier == 3 else GOLD
	paint_crown(im, color, CROWN_ROWS, band)
	if job.hat_style == "brim":
		paint_brim(im, shade(job.hat, -8 if tier == 1 else 4))
	if job.hat_style == "helmet":
		paint_helmet_lamp(im, tier)


def jacket_colors(job: JobLook, tier: int) -> tuple[RGBA, RGBA, RGBA, RGBA]:
	robe = job.robe
	accent = job.accent
	# butcher T3+ shifts toward the dress-coat maroon from the infographic
	if job == JOBS["butcher"] and tier >= 3:
		robe = rgb(128, 48, 62)
		accent = rgb(92, 36, 46)
	if tier == 1:
		robe = shade(robe, -18)
		accent = shade(accent, -14)
		trim = shade(robe, -28)
		collar = robe
	elif tier == 2:
		trim = COBBLE
		collar = shade(robe, 8)
	elif tier == 3:
		robe = shade(robe, 10)
		trim = SILVER
		collar = SILVER
	else:
		robe = shade(robe, 16)
		accent = shade(accent, 10)
		trim = GOLD
		collar = GOLD
	return robe, accent, trim, collar


def uses_apron(job: JobLook, tier: int) -> bool:
	return job.apron and tier <= job.apron_until


def paint_jacket(im: Image.Image, job: JobLook, tier: int) -> None:
	robe, accent, trim, collar = jacket_colors(job, tier)
	fill_cube_faces(
		im,
		0,
		38,
		8,
		20,
		6,
		{
			"fill": robe,
			"top": shade(robe, 12),
			"bottom": shade(robe, -16),
			"front": robe,
			"back": shade(robe, -10),
			"left": shade(robe, -6),
			"right": shade(robe, -6),
		},
	)

	fx, fy, fw, fh = 6, 44, 8, 20
	collar_h = 3 if tier >= 3 else 2
	fill_rect(im, fx, fy, fw, collar_h, collar)
	if tier >= 3:
		put(im, fx + 3, fy + 1, shade(collar, -25))
		put(im, fx + 4, fy + 1, shade(collar, -25))
		fill_rect(im, fx, fy, fw, 1, shade(collar, 20))

	if uses_apron(job, tier):
		apron = accent if accent != robe else shade(robe, -30)
		if job == JOBS["baker"]:
			apron = CREAM if tier > 1 else rgb(210, 204, 188)
		if job == JOBS["butcher"]:
			apron = job.accent
		ay = fy + collar_h
		fill_rect(im, fx + 1, ay, fw - 2, fh - collar_h - 2, apron)
		if job == JOBS["baker"] and tier >= 3:
			btn = SILVER if tier == 3 else GOLD
			put(im, fx + 2, ay + 2, btn)
			put(im, fx + 5, ay + 2, btn)
			put(im, fx + 2, ay + 6, btn)
			put(im, fx + 5, ay + 6, btn)
		if tier == 2:
			for x in range(fx + 1, fx + fw - 1):
				put(im, x, ay, COBBLE)
				put(im, x, fy + fh - 3, COBBLE)
			for y in range(ay, fy + fh - 2):
				put(im, fx + 1, y, COBBLE)
				put(im, fx + fw - 2, y, COBBLE)
		elif tier >= 3:
			rivet = SILVER if tier == 3 else GOLD
			if job == JOBS["carpenter"]:
				rivet = COPPER
			for x in (fx + 1, fx + fw - 2):
				for y in (ay, ay + 4, fy + fh - 4):
					put(im, x, y, rivet)

	if job == JOBS["guard"] and tier >= 3:
		plate = SILVER if tier == 3 else GOLD
		fill_rect(im, fx + 2, fy + collar_h, fw - 4, 6, plate)
		put(im, fx + 3, fy + collar_h + 6, rgb(48, 48, 50))
		put(im, fx + 4, fy + collar_h + 6, rgb(48, 48, 50))

	if job == JOBS["portmaster"] and tier >= 3:
		btn = BRASS if tier == 3 else GOLD
		for y in (fy + 5, fy + 8, fy + 11):
			put(im, fx + 2, y, btn)
			put(im, fx + 5, y, btn)

	if job.sash_row is not None:
		sy = fy + job.sash_row
		fill_rect(im, fx, sy, fw, 2, accent)
		if tier >= 3:
			buckle = GOLD if tier >= 3 else accent
			put(im, fx + 3, sy, buckle)
			put(im, fx + 4, sy, shade(buckle, -30))
			put(im, fx + 3, sy + 1, shade(buckle, -30))
			put(im, fx + 4, sy + 1, buckle)

	paint_concept_marks(im, job, tier, fx, fy, fw, fh)

	if tier == 2:
		for y in range(fy, fy + fh):
			put(im, fx, y, trim)
			put(im, fx + fw - 1, y, trim)
	elif tier >= 3:
		for y in range(fy, fy + fh):
			put(im, fx, y, trim)
			put(im, fx + fw - 1, y, trim)
		if tier == 4:
			for y in range(fy + collar_h, fy + fh - 1):
				put(im, fx + 1, y, BRICK)
				put(im, fx + fw - 2, y, BRICK)

	for side_x, side_w in ((0, 6), (14, 6)):
		fill_rect(im, side_x, fy, side_w, collar_h, collar)
		if tier >= 2:
			fill_rect(im, side_x, fy, side_w, 1, trim)


def job_name(job: JobLook) -> str:
	for name, look in JOBS.items():
		if look is job:
			return name
	return ""


def paint_concept_marks(im: Image.Image, job: JobLook, tier: int, fx: int, fy: int, fw: int, fh: int) -> None:
	"""Large, job-readable marks that survive in-game viewing distance."""
	name = job_name(job)
	if name == "trademaster":
		fill_rect(im, fx, fy, fw, 3, COBBLE)
		fill_rect(im, fx + 1, fy + 4, 6, 4, GOLD)
		fill_rect(im, fx + 2, fy + 5, 4, 2, shade(GOLD, -40))
		put(im, fx + 3, fy + 6, shade(GOLD, 20))
		put(im, fx + 4, fy + 6, shade(GOLD, 20))
	elif name == "gardener":
		put(im, fx + 1, fy + 14, rgb(220, 52, 58))
		put(im, fx + 2, fy + 15, rgb(236, 196, 48))
		put(im, fx + 1, fy + 16, rgb(186, 70, 168))
		put(im, fx + 5, fy + 14, rgb(236, 196, 48))
		put(im, fx + 6, fy + 15, rgb(220, 52, 58))
		put(im, fx + 5, fy + 16, rgb(90, 176, 72))
	elif name == "beekeeper":
		for y in (fy + 5, fy + 8, fy + 11):
			put(im, fx + 2, y, GOLD)
			put(im, fx + 5, y, GOLD)
	elif name == "guard":
		fill_rect(im, fx + 1, fy + 3, fw - 2, 7, LEATHER)
		put(im, fx + 3, fy + 6, GOLD)
		put(im, fx + 4, fy + 6, shade(GOLD, -30))
		fill_rect(im, fx, fy, fw, 1, COBBLE)
	elif name == "librarian":
		fill_rect(im, fx, fy, fw, 3, rgb(188, 166, 110))
		fill_rect(im, fx + 2, fy + 8, 4, 4, GOLD)
		put(im, fx + 3, fy + 9, shade(GOLD, -40))
		put(im, fx + 4, fy + 10, shade(GOLD, -40))
	elif name == "forester":
		put(im, fx + 2, fy + 4, GOLD)
		put(im, fx + 5, fy + 4, GOLD)
		put(im, fx + 2, fy + 6, GOLD)
		put(im, fx + 5, fy + 6, GOLD)
	elif name == "farmer":
		put(im, fx + 3, fy + job.sash_row, GOLD)
		put(im, fx + 4, fy + job.sash_row, shade(GOLD, -30))
	elif name == "portmaster":
		fill_rect(im, fx, fy, fw, 2, NAVY)
		put(im, fx + 2, fy + 6, BRASS)
		put(im, fx + 5, fy + 6, BRASS)
	elif name == "baker":
		fill_rect(im, fx, fy, fw, 2, rgb(90, 168, 210))
	elif name == "miner":
		put(im, fx + 2, fy + 8, COPPER)
		put(im, fx + 5, fy + 8, COPPER)
		put(im, fx + 3, fy + 10, COPPER)
		put(im, fx + 4, fy + 10, COPPER)
	elif name in {"carpenter", "fletcher"}:
		fill_rect(im, fx + 3, fy + 6, 2, 6, LEATHER)
		fill_rect(im, fx + 1, fy + 6, 6, 1, accent_for_mark(job, tier))
	elif name in {"armorer", "toolsmith", "weaponsmith"}:
		fill_rect(im, fx + 1, fy + 8, 6, 1, accent_for_mark(job, tier))
		fill_rect(im, fx + 3, fy + 6, 2, 5, accent_for_mark(job, tier))
	elif name == "roadwright":
		fill_rect(im, fx + 1, fy + 9, 6, 1, shade(job.accent, 12))
		fill_rect(im, fx + 2, fy + 8, 4, 1, job.accent)
	elif name == "scribe":
		fill_rect(im, fx + 2, fy + 7, 4, 4, CREAM)
		put(im, fx + 3, fy + 8, job.accent)
		put(im, fx + 4, fy + 9, job.accent)
	elif name == "cartographer":
		fill_rect(im, fx + 2, fy + 7, 4, 4, CREAM)
		put(im, fx + 2, fy + 8, job.accent)
		put(im, fx + 5, fy + 9, job.accent)
	elif name in {"butcher", "leatherworker"}:
		fill_rect(im, fx + 2, fy + 8, 4, 3, LEATHER)
		put(im, fx + 2, fy + 8, shade(LEATHER, 30))
		put(im, fx + 5, fy + 10, shade(LEATHER, 30))
	elif name == "cleric":
		fill_rect(im, fx + 3, fy + 7, 2, 5, GOLD)
		fill_rect(im, fx + 2, fy + 9, 4, 1, GOLD)


def accent_for_mark(job: JobLook, tier: int) -> RGBA:
	if tier == 3:
		return SILVER
	if tier == 4:
		return GOLD
	return job.accent


def paint_arms(im: Image.Image, job: JobLook, tier: int) -> None:
	robe, _, trim, _ = jacket_colors(job, tier)
	sleeve = shade(robe, -8)
	fill_cube_faces(
		im,
		44,
		22,
		4,
		8,
		4,
		{"fill": sleeve, "front": sleeve, "back": shade(sleeve, -12), "top": shade(sleeve, 10)},
	)
	fill_cube_faces(
		im,
		40,
		38,
		8,
		4,
		4,
		{"fill": sleeve, "front": sleeve, "top": shade(sleeve, 8)},
	)
	if tier >= 2:
		fill_rect(im, 48, 32, 4, 1, trim)
		fill_rect(im, 44, 44, 8, 1, trim)


def paint_tier_one_wear(im: Image.Image, job: JobLook) -> None:
	"""Visible founding-tier repairs, scuffs, and missing hem pixels."""
	name = job_name(job)
	patch = shade(job.robe, -38)
	thread = shade(job.accent, 30)
	# Front-body patch with two bright repair stitches.
	fill_rect(im, 7, 56, 3, 3, patch)
	put(im, 7, 56, thread)
	put(im, 9, 58, thread)
	# Abraded sleeve and asymmetric frayed lower hem.
	put(im, 49, 27, shade(job.robe, -48))
	put(im, 50, 28, thread)
	for x, y in ((6, 63), (8, 62), (11, 63), (13, 62)):
		put(im, x, y, (0, 0, 0, 0))
	# Forester gets an earth-stained hem; portmaster salt wear; Trademaster
	# a second repaired tear beside the trade-board plaque.
	if name in {"baker"}:
		stain = rgb(174, 158, 126)
	elif name in {"farmer", "forester", "gardener", "shepherd"}:
		stain = rgb(82, 68, 42)
	elif name in {"miner", "mason", "armorer", "toolsmith", "weaponsmith"}:
		stain = rgb(58, 58, 56)
	elif name in {"fisherman", "portmaster"}:
		stain = rgb(108, 116, 118)
	elif name in {"butcher", "leatherworker"}:
		stain = rgb(92, 48, 38)
	else:
		stain = shade(job.robe, -46)
	put(im, 10, 60, stain)
	put(im, 12, 59, stain)
	if name == "trademaster":
		put(im, 12, 53, patch)
		put(im, 13, 54, thread)
	if uses_apron(job, 1):
		put(im, 8, 52, stain)
		put(im, 11, 55, shade(stain, 12))


def paint_tier_one_hat_wear(im: Image.Image, job: JobLook) -> None:
	if not job.hat or job.hat_style in {"none", "", "hood"}:
		return
	# Tiny repaired seam and abrasion on the crown, clear of the face window.
	put(im, 42, 9, shade(job.hat, 28))
	put(im, 43, 10, shade(job.hat, -36))
	put(im, 44, 9, shade(job.hat, 28))


def paint_body(im: Image.Image, job: JobLook, tier: int) -> None:
	robe, accent, _, _ = jacket_colors(job, tier)
	fill_cube_faces(
		im,
		16,
		20,
		8,
		12,
		6,
		{"fill": shade(robe, -6), "front": shade(robe, -6)},
	)
	if uses_apron(job, tier):
		apron = accent if job != JOBS["baker"] else CREAM
		fill_rect(im, 23, 28, 6, 8, apron)


def paint_job(job: JobLook, tier: int) -> Image.Image:
	im = new_atlas()
	paint_jacket(im, job, tier)
	paint_arms(im, job, tier)
	paint_body(im, job, tier)
	paint_hat_for_job(im, job, tier)
	if tier == 1:
		paint_tier_one_wear(im, job)
		paint_tier_one_hat_wear(im, job)
	if job == JOBS["miner"] and tier >= 2:
		put(im, 9, 54, COPPER)
		put(im, 10, 54, COPPER)
	if job == JOBS["guard"] and tier >= 3:
		fill_rect(im, 8, 50, 4, 3, SILVER if tier == 3 else GOLD)
	return im


def assert_face_open(im: Image.Image, name: str, job: JobLook) -> None:
	"""Hat front eye rows must stay transparent so the base face shows."""
	px = im.load()
	fx, fy = HAT_FRONT
	if job.hat_style == "hood":
		# window columns 1..6, rows 2..7
		for y in range(fy + 2, fy + 8):
			for x in range(fx + 1, fx + 7):
				if px[x, y][3] != 0:
					raise AssertionError(f"{name}: hood window opaque at {(x, y)}")
		return
	# any hat (or no hat): lower 6 front rows must be empty
	for y in range(EYE_ROW0, fy + HAT_H):
		for x in range(fx, fx + HAT_W):
			if px[x, y][3] != 0:
				raise AssertionError(f"{name}: hat covers face at {(x, y)} style={job.hat_style}")


def write_job(name: str, job: JobLook) -> None:
	villager_dir = CUSTOM if job.namespace == "live-villages" else VANILLA
	zombie_dir = CUSTOM_Z if job.namespace == "live-villages" else VANILLA_Z
	villager_dir.mkdir(parents=True, exist_ok=True)
	zombie_dir.mkdir(parents=True, exist_ok=True)
	for tier in (1, 2, 3, 4):
		image = paint_job(job, tier)
		assert_face_open(image, f"{name} t{tier}", job)
		filename = f"{name}.png" if tier == 1 else f"{name}_tier{tier}.png"
		path = villager_dir / filename
		image.save(path)
		zpath = zombie_dir / filename
		image.save(zpath)
		meta_path = path.with_suffix(path.suffix + ".mcmeta")
		zmeta_path = zpath.with_suffix(zpath.suffix + ".mcmeta")
		if job.hat_style not in ("none", ""):
			meta_path.write_text(HAT_MCMETA)
			zmeta_path.write_text(HAT_MCMETA)
		else:
			meta_path.unlink(missing_ok=True)
			zmeta_path.unlink(missing_ok=True)


def main() -> None:
	names = sys.argv[1:] or list(JOBS)
	unknown = [name for name in names if name not in JOBS]
	if unknown:
		raise SystemExit(f"unknown jobs: {', '.join(unknown)}")
	for name in names:
		job = JOBS[name]
		write_job(name, job)
	print(f"wrote {len(names)} jobs x 4 tiers x 2 entity kinds")


if __name__ == "__main__":
	main()
