# AGENTS.md

This repository uses `known-issues.md` at the repo root as a temporary issue tracker. Once we are using a git repository, if it has issue tracking we will move over to that.

Instructions for future Codex/agent runs:

- Read `known-issues.md` before making changes when the task touches an existing bug, visual issue, or parked problem.
- If the user decides to defer a bug or investigation, add or update an entry in `known-issues.md`.
- Keep issue notes concise and action-oriented: include current behavior, expected behavior, files involved, and what has already been tried.
- Do not delete issue notes unless the user confirms the issue is resolved.
- If we make changes to the code that conflict with the specification (SPECS.md file), ask the user if the changes should be incorporated into the spec or rejected.
- If we add functionality to the code that isn't inlcuded in the specification but should be, and it doesn't conflict with existing specifications, then add it to the spec.
- Watch server performance when adding loaded-world simulation. Prefer cached scans, bounded per-tick work queues, and measurable timing logs over repeated full-area scans in tick handlers. If a feature needs broad world analysis, cache results and invalidate or refresh them gradually.
- Keep track of what you are doing in the IMPLEMENTATION-PLAN.md file.

Current project-specific note:

- The Trade Board item texture is working.
- The placed Trade Board still has an unresolved cosmetic display issue documented in `known-issues.md`.
