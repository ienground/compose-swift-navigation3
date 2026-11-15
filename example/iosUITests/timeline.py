"""Merges UI test phase markers and the example app's system log into one timeline.

usage: python3 -I timeline.py <run output dir>   (written by run.sh)
"""
import re
import sys

out = sys.argv[1]
events = []
for line in open(f"{out}/xcodebuild.log", errors="ignore"):
    m = re.search(r"\[HARNESS (\d+)\] (.*)", line)
    if m:
        events.append((int(m.group(1)), "TEST ", m.group(2).strip()))
for line in open(f"{out}/app.log", errors="ignore"):
    m = re.search(r"\[ScrollDemo (\d+)\] (.*)", line)
    if m:
        events.append((int(m.group(1)), "APP  ", m.group(2).strip()))
events.sort()
start = events[0][0] if events else 0
for time, source, message in events:
    print(f"{(time - start) / 1000:7.2f} {source}{message[:200]}")
