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
	"carpenter": JobLook(rgb(176, 128, 72), LEATHER, apron=True, sash_row=None),
	"forester": JobLook(rgb(92, 60, 34), rgb(68, 112, 48), sash_row=10),
	"gardener": JobLook(rgb(78, 122, 68), rgb(186, 86, 110), sash_row=12),
	"guard": JobLook(rgb(38, 86, 52), rgb(70, 58, 40), sash_row=10),
	"miner": JobLook(rgb(72, 78, 84), COPPER, hat=rgb(64, 68, 74), hat_style="helmet", sash_row=12),
	"portmaster": JobLook(NAVY, BRASS, hat=NAVY, hat_style="brim", sash_row=12),
	"roadwright": JobLook(rgb(156, 140, 96), rgb(92, 82, 62), sash_row=11),
	"scribe": JobLook(rgb(222, 210, 184), rgb(48, 70, 122), sash_row=11),
	"trademaster": JobLook(rgb(28, 112, 70), GOLD, sash_row=11),
	"farmer": JobLook(rgb(118, 82, 52), STRAW, hat=STRAW, hat_style="brim", sash_row=12, namespace="minecraft"),
	"butcher": JobLook(rgb(92, 58, 42), rgb(140, 42, 48), apron=True, sash_row=None, apron_until=2, namespace="minecraft"),
	"fisherman": JobLook(rgb(46, 122, 132), rgb(164, 140, 96), hat=rgb(46, 48, 50), hat_style="beanie", sash_row=13, namespace="minecraft"),
	"shepherd": JobLook(rgb(214, 204, 176), rgb(118, 84, 52), sash_row=11, namespace="minecraft"),
	"mason": JobLook(rgb(126, 122, 116), rgb(88, 86, 82), sash_row=12, namespace="minecraft"),
	"fletcher": JobLook(rgb(92, 88, 48), rgb(120, 74, 40), sash_row=12, namespace="minecraft"),
	"leatherworker": JobLook(rgb(164, 116, 70), LEATHER, apron=True, sash_row=None, namespace="minecraft"),
	"armorer": JobLook(rgb(72, 72, 76), rgb(48, 48, 50), sash_row=12, namespace="minecraft"),
	"toolsmith": JobLook(rgb(90, 90, 96), rgb(124, 82, 42), sash_row=11, namespace="minecraft"),
	"weaponsmith": JobLook(rgb(64, 70, 76), rgb(148, 40, 40), sash_row=11, namespace="minecraft"),
	"cleric": JobLook(rgb(112, 58, 142), GOLD, sash_row=13, namespace="minecraft"),
	"librarian": JobLook(rgb(116, 40, 52), rgb(92, 58, 36), sash_row=12, namespace="minecraft"),
	"cartographer": JobLook(rgb(188, 166, 124), rgb(92, 70, 42), sash_row=12, namespace="minecraft"),
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


def main() -> None:
	for name, job in JOBS.items():
		write_job(name, job)
	print(f"wrote {len(JOBS)} jobs x 4 tiers x 2 entity kinds")


if __name__ == "__main__":
	main()
