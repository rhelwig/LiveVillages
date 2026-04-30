# AGENTS.md

This repository now uses the GitHub issues tracker as the primary issue tracker. `known-issues.md` is legacy migration material and should be phased out.

Instructions for future Codex/agent runs:

- Check the GitHub issues tracker before making changes when the task touches an existing bug, visual issue, or parked problem.
- If the user decides to defer a bug or investigation, create or update a GitHub issue instead of adding a new `known-issues.md` entry.
- Keep issue notes concise and action-oriented: include current behavior, expected behavior, files involved, and what has already been tried.
- Treat `known-issues.md` as legacy reference material during migration. Do not add new issues there unless the user explicitly asks.
- If you touch a still-relevant legacy `known-issues.md` item, prefer migrating it into the GitHub issue tracker and then mark or remove the legacy note only after the user confirms the migration/result is correct.
- If we make changes to the code that conflict with the specification (SPECS.md file), ask the user if the changes should be incorporated into the spec or rejected.
- If we add functionality to the code that isn't inlcuded in the specification but should be, and it doesn't conflict with existing specifications, then add it to the spec.
- Watch server performance when adding loaded-world simulation. Prefer cached scans, bounded per-tick work queues, and measurable timing logs over repeated full-area scans in tick handlers. If a feature needs broad world analysis, cache results and invalidate or refresh them gradually.
- Keep track of what you are doing in the IMPLEMENTATION-PLAN.md file.

Current project-specific note:

- The Trade Board item texture is working.
- The placed Trade Board still has an unresolved cosmetic display issue; use the GitHub issue tracker for the active record while `known-issues.md` is being retired.
