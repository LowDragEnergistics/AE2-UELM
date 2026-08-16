#!/usr/bin/env python3
"""Plan-compliance checker for the TunnelPattern port.

Validates the constraints defined in agents.md and todo.md:

agents.md:
  - required top-level sections exist (Roles, Rules, File Ownership, DOD, CI)

todo.md:
  - milestones use '## M{n}' headers
  - items use '- [ ]' / '- [x]' with a unique 'TP-\\d+' id
  - a milestone marked complete (trailing '✅') must not contain unchecked items
  - item ids must be unique across the file

Exit code 0 on success, 1 with a report on any violation.
Usage: check_todo.py <agents.md> <todo.md>
"""

import re
import sys

AGENTS_REQUIRED_SECTIONS = [
    ("1. 角色与职责", "Roles"),
    ("2. 硬性规则", "Rules"),
    ("3. 文件所有权映射", "File ownership"),
    ("4. 完成定义", "DOD"),
    ("5. CI/CD 说明", "CI/CD"),
    ("6. 开发规范", "Dev standards"),
    ("7. 版本管理", "Version management"),
]

MILESTONE_RE = re.compile(r"^## M\d+ .+")
CHECKBOX_RE = re.compile(r"^- \[([ xX])\] ")
ITEM_ID_RE = re.compile(r"TP-\d+")
DONE_MILESTONE_MARK_RE = re.compile(r"✅\s*$")


def check_agents(path: str) -> list[str]:
    errors = []
    try:
        with open(path, encoding="utf-8") as f:
            content = f.read()
    except OSError as e:
        return [f"[agents.md] cannot read {path}: {e}"]

    for marker, _label in AGENTS_REQUIRED_SECTIONS:
        if marker not in content:
            errors.append(f"[agents.md] missing required section marker: '{marker}'")
    return errors


def check_todo(path: str) -> list[str]:
    errors = []
    try:
        with open(path, encoding="utf-8") as f:
            lines = f.readlines()
    except OSError as e:
        return [f"[todo.md] cannot read {path}: {e}"]

    ids = {}
    current_milestone = None
    milestone_complete = False
    unchecked_in_done = []

    for lineno, raw in enumerate(lines, start=1):
        line = raw.rstrip("\n")

        if MILESTONE_RE.match(line):
            current_milestone = line
            milestone_complete = bool(DONE_MILESTONE_MARK_RE.search(line))
            continue

        m = CHECKBOX_RE.match(line)
        if m:
            checked = m.group(1).lower() == "x"
            id_match = ITEM_ID_RE.search(line)
            item_id = id_match.group(0) if id_match else None
            if item_id is None:
                errors.append(f"[todo.md:{lineno}] checkbox item without TP-xxx id: '{line.strip()}'")
                continue
            if item_id in ids:
                errors.append(
                    f"[todo.md:{lineno}] duplicate item id {item_id} "
                    f"(first seen at line {ids[item_id]})"
                )
            else:
                ids[item_id] = lineno

            if milestone_complete and not checked:
                unchecked_in_done.append(f"{item_id} (line {lineno}, milestone: {current_milestone!r})")
            continue

        stripped = line.strip()
        if stripped and not stripped.startswith(("#", "-", "|", ">", "```")):
            # Unknown non-table text: allowed (free text), but flag item-ish typos.
            if re.match(r"^- \[\S*\]", stripped):
                errors.append(f"[todo.md:{lineno}] malformed checkbox item: '{stripped}'")

    if unchecked_in_done:
        errors.append(
            "[todo.md] completed milestone (✅) contains unchecked items: "
            + "; ".join(unchecked_in_done)
        )

    if not ids:
        # A fresh feature branch legitimately starts with an empty plan (no items yet).
        # Print a notice but do not fail: structure is validated once items exist.
        print("[todo.md] notice: no TP-xxx items found (empty/fresh plan is allowed)")
    return errors


def main() -> int:
    if len(sys.argv) != 3:
        print(f"usage: {sys.argv[0]} <agents.md> <todo.md>", file=sys.stderr)
        return 2

    errors = check_agents(sys.argv[1]) + check_todo(sys.argv[2])

    if errors:
        print("plan-check FAILED:")
        for e in errors:
            print(f"  - {e}")
        return 1

    print("plan-check OK: agents.md and todo.md constraints satisfied")
    return 0


if __name__ == "__main__":
    sys.exit(main())
