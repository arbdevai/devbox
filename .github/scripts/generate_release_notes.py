#!/usr/bin/env python3
import os
import re
import subprocess

def main():
    commit_msg = os.environ.get("COMMIT_MSG", "").strip()
    repo = os.environ.get("REPO_NAME", "")
    tag = os.environ.get("TAG_NAME", "")

    lines = commit_msg.splitlines()
    title = lines[0] if lines else ""
    desc = re.sub(r"^Release\s+v[0-9.]+[:\s-]*", "", title).strip()
    body = "\n".join(lines[1:]).strip()

    out = ["## What's Changed", ""]

    if body:
        out.append(body)
        out.append("")
    elif desc:
        parts = re.split(r",\s*(?:and\s+)?|\s+and\s+", desc)
        bullets = [p.strip() for p in parts if p.strip()]
        if len(bullets) > 1:
            for b in bullets:
                out.append(f"- {b[0].upper() + b[1:]}")
        else:
            out.append(f"- {desc}")
        out.append("")

    try:
        prev_tag = subprocess.check_output(
            ["git", "describe", "--tags", "--abbrev=0", "HEAD^"],
            stderr=subprocess.DEVNULL
        ).decode().strip()
        if prev_tag:
            commits = subprocess.check_output(
                ["git", "log", f"{prev_tag}..HEAD", "--oneline", "--no-merges"],
                stderr=subprocess.DEVNULL
            ).decode().strip()
            filtered = [
                f"- {re.sub(r'^[a-f0-9]+\s+', '', c)}"
                for c in commits.splitlines()
                if not re.match(r"^[a-f0-9]+\s+Release\s+v", c, re.IGNORECASE)
            ]
            if filtered:
                out.append("### Commits")
                out.extend(filtered)
                out.append("")
    except Exception:
        pass

    out.append("---")
    out.append("### Download")
    out.append(f"- Root Flavor (Native Chroot): [app-root-release.apk](https://github.com/{repo}/releases/download/{tag}/app-root-release.apk)")
    out.append(f"- No-Root Flavor (PRoot Userspace): [app-noroot-release.apk](https://github.com/{repo}/releases/download/{tag}/app-noroot-release.apk)")

    with open("release_notes.md", "w") as f:
        f.write("\n".join(out) + "\n")

if __name__ == "__main__":
    main()
