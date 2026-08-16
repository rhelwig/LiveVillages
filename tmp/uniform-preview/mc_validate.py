#!/usr/bin/env python3
"""Drive the running Minecraft window: wait, look around, screenshot, quit."""

from __future__ import annotations

import subprocess
import sys
import time
from pathlib import Path

from Xlib import X, XK, display
from Xlib.ext import xtest

LOG = Path("/home/rhelwig/Projects/LiveVillages/run/logs/latest.log")
SHOTS = Path("/home/rhelwig/Projects/LiveVillages/run/screenshots/outfits/validate")
D = display.Display()
ROOT = D.screen().root


def keysym(name: str) -> int:
	ks = XK.string_to_keysym(name)
	if not ks:
		raise RuntimeError(f"bad keysym {name}")
	return D.keysym_to_keycode(ks)


def tap(name: str, hold: float = 0.05) -> None:
	code = keysym(name)
	xtest.fake_input(D, X.KeyPress, code)
	D.sync()
	time.sleep(hold)
	xtest.fake_input(D, X.KeyRelease, code)
	D.sync()
	time.sleep(0.08)


def type_text(text: str) -> None:
	for ch in text:
		if ch == " ":
			tap("space")
			continue
		if ch == "/":
			tap("slash")
			continue
		if ch == "-":
			tap("minus")
			continue
		if ch.isdigit():
			tap(ch)
			continue
		if ch == "_":
			# underscore
			shift = keysym("Shift_L")
			minus = keysym("minus")
			xtest.fake_input(D, X.KeyPress, shift)
			xtest.fake_input(D, X.KeyPress, minus)
			D.sync()
			time.sleep(0.03)
			xtest.fake_input(D, X.KeyRelease, minus)
			xtest.fake_input(D, X.KeyRelease, shift)
			D.sync()
			time.sleep(0.04)
			continue
		if ch == "@":
			shift = keysym("Shift_L")
			two = keysym("2")
			xtest.fake_input(D, X.KeyPress, shift)
			xtest.fake_input(D, X.KeyPress, two)
			D.sync()
			time.sleep(0.03)
			xtest.fake_input(D, X.KeyRelease, two)
			xtest.fake_input(D, X.KeyRelease, shift)
			D.sync()
			time.sleep(0.04)
			continue
		if ch == "[":
			tap("bracketleft")
			continue
		if ch == "]":
			tap("bracketright")
			continue
		if ch == "=":
			tap("equal")
			continue
		if ch == ":":
			shift = keysym("Shift_L")
			semi = keysym("semicolon")
			xtest.fake_input(D, X.KeyPress, shift)
			xtest.fake_input(D, X.KeyPress, semi)
			D.sync()
			time.sleep(0.03)
			xtest.fake_input(D, X.KeyRelease, semi)
			xtest.fake_input(D, X.KeyRelease, shift)
			D.sync()
			time.sleep(0.04)
			continue
		if ch == ",":
			tap("comma")
			continue
		if ch == '"':
			shift = keysym("Shift_L")
			q = keysym("quotedbl") or keysym("apostrophe")
			xtest.fake_input(D, X.KeyPress, shift)
			xtest.fake_input(D, X.KeyPress, q)
			D.sync()
			time.sleep(0.03)
			xtest.fake_input(D, X.KeyRelease, q)
			xtest.fake_input(D, X.KeyRelease, shift)
			D.sync()
			time.sleep(0.04)
			continue
		name = ch
		tap(name)


def look(dx: int, dy: int = 0) -> None:
	xtest.fake_input(D, X.MotionNotify, 0, True, dx, dy)
	D.sync()
	time.sleep(0.2)


def find_mc_window() -> int | None:
	out = subprocess.check_output(["xwininfo", "-root", "-tree", "-display", ":1"], text=True)
	for line in out.splitlines():
		if "Minecraft" in line:
			# "     0x123456 "Minecraft* 26.1.1": ..."
			part = line.strip().split()[0]
			return int(part, 16)
	return None


def screenshot(name: str) -> Path:
	SHOTS.mkdir(parents=True, exist_ok=True)
	path = SHOTS / name
	wid = find_mc_window()
	if wid:
		subprocess.check_call(
			["import", "-display", ":1", "-window", hex(wid), str(path)]
		)
	else:
		subprocess.check_call(["import", "-display", ":1", "-window", "root", str(path)])
	print("shot", path, path.stat().st_size)
	return path


def log_has(*needles: str) -> bool:
	if not LOG.exists():
		return False
	text = LOG.read_text(errors="ignore")
	return any(n in text for n in needles)


def wait_for_world(timeout: float = 240.0) -> None:
	start = time.time()
	while time.time() - start < timeout:
		if log_has("joined the game", "Saving and pausing game"):
			# joined is what we want; ignore pause
			text = LOG.read_text(errors="ignore")
			if "joined the game" in text:
				print("world joined")
				return
		if log_has("Failed to start", "ERROR", "Crash"):
			# keep waiting unless it's a hard crash report
			if "---- Minecraft Crash Report ----" in LOG.read_text(errors="ignore"):
				raise RuntimeError("client crashed")
		time.sleep(2)
		print("waiting for join...", int(time.time() - start), "s")
	raise TimeoutError("world did not load")


def chat(cmd: str) -> None:
	tap("t")
	time.sleep(0.25)
	type_text(cmd)
	time.sleep(0.1)
	tap("Return")
	time.sleep(0.4)


def focus_mc() -> None:
	wid = find_mc_window()
	if not wid:
		print("no mc window yet")
		return
	# click center of window to capture mouse
	geom = subprocess.check_output(
		["xwininfo", "-id", hex(wid), "-display", ":1"], text=True
	)
	x = y = w = h = 0
	for line in geom.splitlines():
		if "Absolute upper-left X" in line:
			x = int(line.split(":")[1])
		elif "Absolute upper-left Y" in line:
			y = int(line.split(":")[1])
		elif "Width:" in line and "border" not in line.lower():
			w = int(line.split(":")[1])
		elif "Height:" in line and "border" not in line.lower():
			h = int(line.split(":")[1])
	cx, cy = x + w // 2, y + h // 2
	xtest.fake_input(D, X.MotionNotify, 0, False, cx, cy)
	D.sync()
	xtest.fake_input(D, X.ButtonPress, 1)
	D.sync()
	time.sleep(0.05)
	xtest.fake_input(D, X.ButtonRelease, 1)
	D.sync()
	time.sleep(0.3)
	print("focused", hex(wid), "at", cx, cy)


def main() -> None:
	action = sys.argv[1] if len(sys.argv) > 1 else "wait"
	if action == "wait":
		wait_for_world()
		return
	if action == "focus":
		focus_mc()
		return
	if action == "shot":
		screenshot(sys.argv[2] if len(sys.argv) > 2 else "shot.png")
		return
	if action == "look":
		look(int(sys.argv[2]), int(sys.argv[3]) if len(sys.argv) > 3 else 0)
		return
	if action == "chat":
		chat(sys.argv[2])
		return
	if action == "key":
		tap(sys.argv[2])
		return
	if action == "f2":
		tap("F2")
		return
	raise SystemExit(f"unknown {action}")


if __name__ == "__main__":
	main()
