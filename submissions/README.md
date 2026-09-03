# Submissions

One file per stage, holding the exact text and links submitted. Each doubles as the body of the corresponding GitHub Release, so it gets written once and used twice.

## The five gates

| Stage | Deliverable | Due | Accepts | Tag | Release? |
|---|---|---|---|---|:---:|
| 1 | Idea | Sun 7 Sep | Link / File / Text / **repo** | `stage-1-idea` | no |
| 2 | PRD + hi-fi | Sun 14 Sep | Link / File / Text / **repo** | `stage-2-prd` | no |
| 3 | Version 0 | Sat 19 Sep | **Link only** ⚠️ | `v0.1.0` | **yes** |
| 4 | Version 1 | Fri 25 Sep | Link / Text | `v0.2.0` | yes |
| 5 | MVP | Tue 29 Sep | Link / File / Text / **repo** | `v1.0.0` | yes |

**All five are worth 20 XP.** A missed V0 costs exactly as much as a missed MVP, so completion beats ambition at every gate. Submit whatever exists rather than skipping a stage.

## Documents are uploaded, not linked

`docs/` is deliberately excluded from the repository. The PRD, ideation and architecture documents go up through each stage's **file-upload field**, from `docs/exports/`. Stages 1, 2 and 5 accept a file alongside the repo link; stages 3 and 4 do not, so anything a judge must read at those gates belongs in the release notes.

This means **no link in `README.md` or `design/README.md` may point into `docs/`** — it will 404 on github.com. Check this before each submission.

## Why Stage 3 forces a Release

Stage 3 accepts a **link and nothing else** — no file upload, no text box, no repo option. For a sideloaded Android app that's hostile: you can't attach the APK and you can't explain what works. One URL has to carry everything, and a GitHub Release page is the only thing that does.

**Do a dry run in week 1.** Tag a throwaway `v0.0.1`, cut a release, attach a stub APK, delete it. At the gate it's then five minutes instead of the moment you discover signing is broken.

## Gate-day checklist

1. **Record the demo video the day before.** A gate day spent recording is a gate day not spent fixing.
2. Verify the build in **airplane mode** on real hardware.
3. Update `README.md` — the screenshots and the "Try it" section.
4. Write `submissions/stage-N-*.md`.
5. Commit, then tag: `git tag -a v0.1.0 -m "V0 — offline core"` and `git push --tags`.
6. Cut the Release on GitHub, pasting the submission file as the notes. Attach the APK.
7. Submit the release URL.

## Release notes shape

Always these four, in this order:

1. **Demo video link, at the very top.** Judges will not install your APK; they will watch 60 seconds of video.
2. **The APK**, as a release asset.
3. **What works / what doesn't yet.** Non-negotiable — judges reward honesty about scope and punish finding the gaps themselves.
4. **Links back** to the docs and the design canvas.
