#!/usr/bin/env python3
"""Check that FEATURES.md and the EXECUTION_PLAN.md tracker (E9) list the same task IDs.

Fails, listing the IDs, if a plan task is missing from FEATURES.md or FEATURES.md mentions
an ID that is not in the plan. Standard library only.

Usage: python -I tools/check-features.py [--plan PATH] [--features PATH]
"""

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
TASK_ID = r"(?:T\d+\.\d+|R\d+)"
TRACKER_LINE = re.compile(rf"^- \[[ xX]\] ({TASK_ID})\b")
ANY_ID = re.compile(rf"\b({TASK_ID})\b")


def plan_ids(plan_text):
    """Task IDs from the checkboxes in section E9 of the plan."""
    ids = set()
    in_tracker = False
    for line in plan_text.splitlines():
        if line.startswith("## "):
            in_tracker = line.startswith("## E9.")
            continue
        if in_tracker:
            match = TRACKER_LINE.match(line)
            if match:
                ids.add(match.group(1))
    return ids


def feature_ids(features_text):
    """Every task ID mentioned anywhere in the feature list."""
    return set(ANY_ID.findall(features_text))


def sort_key(task_id):
    return [int(n) for n in re.findall(r"\d+", task_id)] + [task_id[0] == "R"]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--plan", type=Path, default=REPO_ROOT / "EXECUTION_PLAN.md")
    parser.add_argument("--features", type=Path, default=REPO_ROOT / "FEATURES.md")
    args = parser.parse_args(argv)

    in_plan = plan_ids(args.plan.read_text(encoding="utf-8"))
    in_features = feature_ids(args.features.read_text(encoding="utf-8"))

    if not in_plan:
        print(f"No task IDs found in the E9 tracker of {args.plan}.", file=sys.stderr)
        return 1

    missing = sorted(in_plan - in_features, key=sort_key)
    unknown = sorted(in_features - in_plan, key=sort_key)
    if missing:
        print(f"Plan tasks missing from {args.features.name}: {', '.join(missing)}", file=sys.stderr)
    if unknown:
        print(f"IDs in {args.features.name} that are not in the plan: {', '.join(unknown)}", file=sys.stderr)
    if missing or unknown:
        return 1

    print(f"OK: {len(in_plan)} plan tasks, all listed in {args.features.name}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
