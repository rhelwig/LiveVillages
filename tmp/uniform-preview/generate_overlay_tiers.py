#!/usr/bin/env python3
"""Derive civic-tier villager profession overlays from existing atlases."""

from __future__ import annotations

import shutil
from pathlib import Path

from PIL import Image

ROOT = Path("/home/rhelwig/Projects/LiveVillages/src/main/resources/assets")
CUSTOM_VILLAGER = ROOT / "live-villages/textures/entity/villager/profession"
CUSTOM_ZOMBIE = ROOT / "live-villages/textures/entity/zombie_villager/profession"
VANILLA_VILLAGER = ROOT / "minecraft/textures/entity/villager/profession"
VANILLA_ZOMBIE = ROOT / "minecraft/textures/entity/zombie_villager/profession"
JAR = Path(
	"/home/rhelwig/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/"
	"minecraft-clientonly-deobf/26.1.1/minecraft-clientonly-deobf-26.1.1.jar"
)

CUSTOM_ROLES = [
	"baker",
	"beekeeper",
	"carpenter",
	"forester",
	"gardener",
	"guard",
	"miner",
	"portmaster",
	"roadwright",
	"scribe",
	"trademaster",
]
VANILLA_ROLES = [
	"farmer",
	"butcher",
	"fisherman",
	"shepherd",
	"mason",
	"fletcher",
	"leatherworker",
	"armorer",
	"toolsmith",
	"weaponsmith",
	"cleric",
	"librarian",
	"cartographer",
]

TIER_TRIM = {
	2: (122, 118, 112, 255),
	3: (176, 180, 188, 255),
	4: (212, 168, 58, 255),
}
TIER_SHIFT = {
	2: (8, 6, 2),
	3: (16, 14, 12),
	4: (22, 16, 4),
}


def clamp(value: int) -> int:
	return max(0, min(255, value))


def neighbors(x: int, y: int, width: int, height: int) -> list[tuple[int, int]]:
	points = []
	for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
		nx, ny = x + dx, y + dy
		if 0 <= nx < width and 0 <= ny < height:
			points.append((nx, ny))
	return points


def derive(source: Image.Image, tier: int) -> Image.Image:
	src = source.convert("RGBA")
	pixels = src.load()
	width, height = src.size
	out = src.copy()
	dest = out.load()
	trim = TIER_TRIM[tier]
	shift = TIER_SHIFT[tier]

	for y in range(height):
		for x in range(width):
			r, g, b, a = pixels[x, y]
			if a < 16:
				continue
			edge = any(pixels[nx, ny][3] < 16 for nx, ny in neighbors(x, y, width, height))
			if edge:
				dest[x, y] = trim
			else:
				dest[x, y] = (
					clamp(r + shift[0]),
					clamp(g + shift[1]),
					clamp(b + shift[2]),
					a,
				)
	return out


def copy_mcmeta(source: Path, dest: Path) -> None:
	meta = source.with_suffix(source.suffix + ".mcmeta")
	if meta.exists():
		shutil.copy2(meta, dest.with_suffix(dest.suffix + ".mcmeta"))


def write_tiers(source: Path, dest_dir: Path, role: str) -> None:
	if not source.exists():
		return
	image = Image.open(source)
	dest_dir.mkdir(parents=True, exist_ok=True)
	for tier in (2, 3, 4):
		out_path = dest_dir / f"{role}_tier{tier}.png"
		derive(image, tier).save(out_path)
		copy_mcmeta(source, out_path)


def extract_vanilla(role: str, kind: str) -> Path | None:
	import zipfile

	inner = f"assets/minecraft/textures/entity/{kind}/profession/{role}.png"
	with zipfile.ZipFile(JAR) as archive:
		if inner not in archive.namelist():
			return None
		target_dir = VANILLA_VILLAGER if kind == "villager" else VANILLA_ZOMBIE
		# Keep extracted bases only in a temp cache; variants go next to overrides.
		cache = Path("/tmp/livevillages-vanilla-overlays") / kind
		cache.mkdir(parents=True, exist_ok=True)
		target = cache / f"{role}.png"
		target.write_bytes(archive.read(inner))
		meta_name = inner + ".mcmeta"
		if meta_name in archive.namelist():
			(cache / f"{role}.png.mcmeta").write_bytes(archive.read(meta_name))
		return target


def main() -> None:
	for role in CUSTOM_ROLES:
		write_tiers(CUSTOM_VILLAGER / f"{role}.png", CUSTOM_VILLAGER, role)
		write_tiers(CUSTOM_ZOMBIE / f"{role}.png", CUSTOM_ZOMBIE, role)

	for role in VANILLA_ROLES:
		existing = VANILLA_VILLAGER / f"{role}.png"
		source = existing if existing.exists() else extract_vanilla(role, "villager")
		if source is not None:
			write_tiers(source, VANILLA_VILLAGER, role)
		existing_zombie = VANILLA_ZOMBIE / f"{role}.png"
		zombie_source = existing_zombie if existing_zombie.exists() else extract_vanilla(role, "zombie_villager")
		if zombie_source is not None:
			write_tiers(zombie_source, VANILLA_ZOMBIE, role)


if __name__ == "__main__":
	main()
