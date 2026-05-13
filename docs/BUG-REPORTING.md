# Bug Reporting Guide

This guide is for playtesters, artists, and coders who want to report a problem with Live Villages on GitHub.

GitHub issues for this project live here:

<https://github.com/rhelwig/LiveVillages/issues>

Before making a new bug report, take a quick look through the open issues to see if someone already reported the same problem.

- If you find the same bug, add a comment with your screenshot, video, or extra details instead of making a duplicate issue.
- If you are not sure whether it is the same bug, it is still okay to make a new issue.

## Super Short Version

If something looks wrong or feels broken, open a GitHub issue and tell us:

- what you were doing
- what went wrong
- what you expected instead
- whether it happens every time or only sometimes
- a screenshot or short video if you have one

That is enough to help.

## For Young Playtesters

You do not need fancy computer words.

Good bug report:

1. Look through the open issues for a minute to see if your bug is already there.
2. If it is already there, add a comment with anything extra you found.
3. If it is not there, click `New issue` on GitHub.
4. Give it a short title.
5. Write 3 to 5 short lines:

```text
I put down a Trade Board in a desert village.
The village name had Oak in it.
I expected a desert name instead.
It happened right away.
Singleplayer, newest test build.
```

6. Add a picture or video if that is easy.
7. Click `Create issue`.

Good titles:

- `Desert village got an Oak name`
- `Roadwright built road into water`
- `Trade Board screen did not update after donation`

Bad titles:

- `bug`
- `help`
- `it broke`

If taking a real screenshot is annoying, a phone picture of your screen is still useful.

## What Helps The Most

- Where were you: village, harbor, outpost, mine, road, farm, Trade Board, Surveyor's Table, etc.
- What did you do right before the bug happened.
- What did you think should happen.
- What actually happened.
- Can you make it happen again.
- Was this singleplayer or multiplayer.
- If you know it: Minecraft version, mod version, and whether other mods were installed.

## For Texture And Art Contributors

If the problem is visual, please include:

- what texture or model looks wrong
- where it shows up in game
- what side is wrong if it matters: front, back, top, bottom, north, south, etc.
- whether the problem is the colors, shape, scale, alignment, animation, or lighting
- a screenshot with arrows or circles if possible

Examples:

- `Beekeeper robe is too dark and does not read as a white suit`
- `Trade Board front face is darker on north/south placement`
- `Milepost text is too hard to read at normal play distance`

If you are proposing an improved texture instead of only reporting a bug, attach the image and say what file or asset it should replace.

## For Coders

If you are comfortable with code, please include:

- exact steps to reproduce
- relevant log output or crash report
- branch or commit if you tested a local change
- whether this seems like a regression
- any suspected files or systems

Useful extras:

- copy the exception text instead of summarizing it
- say whether it fails in a fresh world, an old world, or both
- say whether it only happens loaded, unloaded, client-side, or server-side

## When You Are Not Sure

If you are not sure whether something is a bug, report it anyway.

It is better to send a short report than to wait for a perfect one.
