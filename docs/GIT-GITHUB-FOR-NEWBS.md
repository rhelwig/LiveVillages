# Git And GitHub For Newbs

This guide is for contributors who are new to Git and GitHub, including kids, artists, and people using an agent to help.

It explains the basic words and the safest workflow for getting your changes into the project.

If you forget something, that is normal.

## The Big Picture

There are three different ideas here:

1. `Commit`: save your work in Git on your computer.
2. `Sync` or `update`: bring in newer changes from the main project so your code is not too old.
3. `Pull request`: ask the project to include your changes.

Important:

- A `commit` saves your work, but does not put it into the project by itself.
- A `pull request` is how your changes get reviewed and can be added to the project.

## Very Short Definitions

- `Commit`: a save point for your changes.
- `Branch`: your own line of work.
- `origin`: the GitHub copy of the repository.
- `origin/main`: the latest main version on GitHub.
- `Pull`: download newer changes from GitHub.
- `Push`: upload your branch to GitHub.
- `Pull request` or `PR`: a request asking the project to take your branch and merge it into the real project.

## Why Pull Requests Matter

A pull request is not just a message.

It connects your uploaded branch to the project and says:

`Please review these exact changes and include them in the project if they are good.`

Without a pull request:

- your changes may stay only on your computer
- or only on your own GitHub branch
- and they may never get included in the real project

So if you want your code, textures, or models to actually become part of Live Villages, you usually need a pull request.

## The Safest Normal Workflow

If you are starting new work, this is the safest order:

1. Make sure your code is updated from `origin/main`.
2. Create a new branch for one topic.
3. Make your changes.
4. Test your changes.
5. Commit your changes.
6. Push your branch to GitHub.
7. Open a pull request.

That is the cleanest path.

## One Branch Per Topic

Try to keep one branch for one idea.

Good examples:

- one bug fix
- one texture improvement
- one UI wording cleanup
- one new structure asset

Bad example:

- one branch that mixes texture edits, code changes, experimental rewrites, and unrelated bug fixes

Small focused branches are easier to review and easier to accept.

## What To Do If Your Work Is Old

This is important.

If you started a long time ago and the project has changed a lot since then, do not just keep piling more edits on top and hope it works.

The safer plan is:

1. Save your current work so nothing is lost.
2. Update your code to the latest `origin/main`.
3. Move only the good changes onto a fresh branch.
4. Make a pull request from that cleaner branch.

This is especially helpful if:

- your branch is many commits behind
- some of your changes are good and some should be rejected
- you are not confident about conflict resolution

## Best Rescue Workflow For An Old Mixed Branch

If a branch is old and messy, this is the safest beginner workflow:

1. Make a backup branch from your current work.
2. Update local `main` from `origin/main`.
3. Create a fresh new branch from updated `main`.
4. Copy or cherry-pick only the good changes onto the fresh branch.
5. Test.
6. Commit in small logical pieces.
7. Push.
8. Open one or more pull requests.

Why this is safer:

- it protects the original work
- it avoids forcing every old experiment into one PR
- it makes good changes easier to accept

## What “Sync” Usually Means

People say `sync` in a few different ways.

In this project, it usually means some combination of:

- get the latest changes from GitHub
- make your local code match the latest main project version
- upload your own newest branch changes

So `sync my code` might mean:

- pull the latest `origin/main`
- update your branch with those changes
- push your branch again

## Simple Rules

- Check Discord first if you are not sure what to work on.
- Check GitHub issues for tracked bugs.
- Start from updated code when possible.
- Keep one branch per topic.
- Commit often enough that you do not lose work.
- Open a pull request if you want the project to include your changes.

## Good Workflow For Artists

Artists often need a simpler version:

1. Update your codebase first.
2. Make one art change or one group of closely related art changes.
3. Test the asset in game if possible.
4. Commit the art files.
5. Push the branch.
6. Open a pull request.

If you used Blockbench, keep the `.bbmodel` source file too.

See [ART-ASSET-GUIDE.md](ART-ASSET-GUIDE.md) for where files belong.

## Good Workflow For Coders

Coders usually need to be extra careful about stale branches.

Try to avoid:

- making lots of unrelated changes in one branch
- waiting weeks before updating from `origin/main`
- opening one huge PR for everything

Better:

- update often
- split unrelated work
- make smaller PRs

## Safe Copy/Paste Prompts For An Agent

These are written so a new contributor can paste them to an agent.

### Check Whether My Code Is Old

```text
Please check whether my branch is behind origin/main. If it is, explain how far behind it is and update it safely. Resolve simple conflicts, run tests if possible, and tell me what changed.
```

### Save My Current Work Before Doing Anything Risky

```text
Please make a safe backup branch from my current work before updating anything. Do not delete any local changes. Then tell me what branch names you used.
```

### Refresh My Local Main Branch

```text
Please update my local main branch so it matches the latest origin/main, then switch back to my working branch and tell me whether more updates are needed there.
```

### Help Me Split Good Changes From Bad Changes

```text
Please review my current branch and help me separate the good changes from the changes that should probably be rejected. Put the good changes onto a fresh branch based on the latest origin/main without losing the original branch.
```

### Make Logical Commits

```text
Please review my changed files, group them into logical commits, and create clear commit messages. Do not combine unrelated changes into one commit.
```

### Push My Branch

```text
Please push my current branch to GitHub and tell me the branch name it used.
```

### Open A Pull Request

```text
Please open a pull request for my current branch against main. Use a clear title and a short description of what changed, how it was tested, and any known limits.
```

### Refresh My Working Codebase Before New Work

```text
Please make sure my working codebase is up to date with origin/main before I start new changes. If I should start a fresh branch, do that and tell me the new branch name.
```

### Turn One Big Branch Into Smaller Pull Requests

```text
Please help me turn this large mixed branch into smaller topic branches and separate pull requests based on the latest origin/main. Keep my original branch as a backup.
```

## If You Are Not Sure What Step Comes Next

Use this prompt:

```text
Please look at my current Git status and branch situation, then tell me the safest next step if my goal is to get my good changes included in the Live Villages project.
```

## A Good Beginner Goal

A very good first success looks like this:

1. update from `origin/main`
2. make one small useful change
3. commit it
4. push it
5. open one pull request

That is enough. You do not need to learn every Git trick.
