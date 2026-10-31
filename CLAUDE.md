# Memories - Gallery App

Privacy-first Android gallery with on-device memories, search, and widgets. No internet permission, no cloud.

## Project type

**External project. Use public domains only** (github.com, Maven Central, Google Maven, pypi.org, registry.npmjs.org). Never use Apple internal domains or registries.

## Committed v1 scope

The v1 feature set is committed. It is exactly what these documents describe:

- `PRD.md`: the spec (requirements, rules, decisions).
- `EXECUTION_PLAN.md`: tasks, validation, and the tracker (section E9).
- `FEATURES.md`: the user-facing feature list, kept in step with the plan.

Avoid scope creep:

- Do not add features, settings, options, screens, or polish beyond what a task's requirements say.
- New ideas go into PRD section 7a "Later (not in v1)". They enter v1 only with the owner's explicit approval, recorded in the PRD Decisions log, followed by matching updates to the plan and feature list.
- If a task cannot be done as written, stop and ask (plan E2.5). Do not widen or reinterpret it.

## Working rules

- Before changing the spec, plan, or code, show the plan and get approval.
- Mark tracker items done as tasks finish. Commit the tracker only at phase gates (plan E2.3).
- `FEATURES.md` must match the plan (`tools/check-features.py`, once T0.2 creates it).
- User-facing text: no em dashes or en dashes.
- License: PolyForm Strict 1.0.0, source-available. Never describe the project as "open source".

## Owner

Mhonn Czedrick Ybañez, mhonn.ybanez@gmail.com. Commits use the GitHub no-reply address from the global git config.
