import json
import math
import os
import sys
from collections import defaultdict

# 스크립트 위치(docs/benchmark-results/scripts/) 기준 프로젝트 루트 탐색
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "..", ".."))

# Read golden cases
golden = {}
with open(os.path.join(REPO_ROOT, "backend/src/test/resources/ai/golden/cases.jsonl"), encoding="utf-8") as f:
    for line in f:
        c = json.loads(line.strip())
        golden[c["id"]] = c

# Read raw results for each model
# raw 서브디렉토리 이름은 인자로 받거나 기본값(2026-04-17-v2-10runs) 사용
models = ["claude", "openai", "gemini"]
raw_label = sys.argv[1] if len(sys.argv) > 1 else "2026-04-17-v2-10runs"
base = os.path.join(REPO_ROOT, "docs/benchmark-results/raw", raw_label)

# data[model][caseId] = list of run dicts
data = {m: defaultdict(list) for m in models}

for m in models:
    with open(f"{base}/{m}/raw-results.jsonl", encoding="utf-8") as f:
        for line in f:
            r = json.loads(line.strip())
            data[m][r["caseId"]].append(r)

# Ordered case IDs from golden
case_ids = list(golden.keys())

# Wilson CI
def wilson_ci(p, n, z=1.96):
    if n == 0: return (0, 0)
    denom = 1 + z*z/n
    center = (p + z*z/(2*n)) / denom
    spread = z * math.sqrt((p*(1-p) + z*z/(4*n)) / n) / denom
    return (max(0, center - spread), min(1, center + spread))

output = []

# Per-model overall stats
output.append("=== OVERALL ===")
for m in models:
    all_runs = []
    for cid in case_ids:
        all_runs.extend(data[m][cid])
    n = len(all_runs)
    strict = sum((r["strictPass"] or 0) for r in all_runs) / n
    score = sum((r["score100"] or 0) for r in all_runs) / n
    iou = sum((r["iou"] or 0) for r in all_runs) / n
    lat = sum(r["latencyMs"] for r in all_runs) / n
    exc = sum(1 for r in all_runs if r["exceptionType"])
    lo, hi = wilson_ci(strict, n)
    output.append(f"{m}: strict={strict:.1%} score={score:.1f} iou={iou:.3f} lat={lat:.0f}ms exc={exc} CI=[{lo:.1%},{hi:.1%}]")

# Per-case stats
output.append("")
output.append("=== PER CASE ===")
for cid in case_ids:
    g = golden[cid]
    cat = g["category"]
    elo = g["expected"]["low"]
    ehi = g["expected"]["high"]
    output.append(f"")
    output.append(f"### {cid} ({cat})")
    output.append(f"Expected: {elo:,} ~ {ehi:,}")

    for m in models:
        runs = data[m][cid]
        if not runs:
            output.append(f"  {m}: NO DATA")
            continue
        n = len(runs)
        passes = sum((r["strictPass"] or 0) for r in runs)
        pass_rate = passes / n
        avg_score = sum((r["score100"] or 0) for r in runs) / n
        avg_iou = sum((r["iou"] or 0) for r in runs) / n
        non_exc_with_mid = [r for r in runs if not r["exceptionType"] and r["recMid"] is not None]
        avg_mid_val = sum(r["recMid"] for r in non_exc_with_mid) / len(non_exc_with_mid) if non_exc_with_mid else 0
        avg_lat = sum(r["latencyMs"] for r in runs) / n
        exc_count = sum(1 for r in runs if r["exceptionType"])

        # Determine note
        exc_msgs = [r["exceptionMessage"] for r in runs if r["exceptionMessage"]]

        note = "-"
        if exc_count > 0:
            img_keywords = ["image", "photo", "사진", "이미지", "색상", "일치", "확인", "mismatch", "해당", "제품"]
            img_exc = sum(1 for msg in exc_msgs if any(k in (msg or "").lower() for k in img_keywords))
            if img_exc > 0:
                note = f"image mismatch ({img_exc} exc)"
            else:
                sample_msg = exc_msgs[0] if exc_msgs else ""
                if len(sample_msg) > 80:
                    sample_msg = sample_msg[:80] + "..."
                note = f"exception ({exc_count}): {sample_msg}"

        if pass_rate == 1.0 and exc_count == 0:
            note = "-"
        elif exc_count == 0:
            if 0 < pass_rate < 1.0:
                note = f"borderline ({int(passes)}/10 pass)"
            # Check underpriced/overpriced
            if non_exc_with_mid:
                ne_avg_mid = sum(r["recMid"] for r in non_exc_with_mid) / len(non_exc_with_mid)
                if ne_avg_mid < elo:
                    gap = elo - ne_avg_mid
                    extra = f"underpriced (avg {ne_avg_mid/1000:.0f}k, {gap/1000:.0f}k below low)"
                    if "borderline" in note:
                        note = f"{note}, {extra}"
                    elif pass_rate < 1.0:
                        note = extra
                elif ne_avg_mid > ehi:
                    gap = ne_avg_mid - ehi
                    extra = f"overpriced (avg {ne_avg_mid/1000:.0f}k, {gap/1000:.0f}k above high)"
                    if "borderline" in note:
                        note = f"{note}, {extra}"
                    elif pass_rate < 1.0:
                        note = extra
        elif exc_count > 0 and exc_count < n:
            # mixed: some exceptions + some normal runs that may be under/over
            if non_exc_with_mid:
                ne_avg_mid = sum(r["recMid"] for r in non_exc_with_mid) / len(non_exc_with_mid)
                if ne_avg_mid < elo:
                    gap = elo - ne_avg_mid
                    note += f" + underpriced (avg {ne_avg_mid/1000:.0f}k)"
                elif ne_avg_mid > ehi:
                    gap = ne_avg_mid - ehi
                    note += f" + overpriced (avg {ne_avg_mid/1000:.0f}k)"

        output.append(f"  {m}: pass={int(passes)}/10 score={avg_score:.1f} iou={avg_iou:.3f} avgMid={avg_mid_val:,.0f} lat={avg_lat:.0f}ms exc={exc_count} note={note}")

# Exception details
output.append("")
output.append("=== EXCEPTION DETAILS ===")
for cid in case_ids:
    for m in models:
        runs = data[m][cid]
        exc_runs = [r for r in runs if r["exceptionType"]]
        if exc_runs:
            sample = exc_runs[0]
            msg = sample["exceptionMessage"] or "null"
            if len(msg) > 120:
                msg = msg[:120] + "..."
            output.append(f"{cid} | {m} | {len(exc_runs)} exc | type={sample['exceptionType']} | msg={msg}")

# Underpricing
output.append("")
output.append("=== UNDERPRICING ===")
for cid in case_ids:
    g = golden[cid]
    elo = g["expected"]["low"]
    for m in models:
        runs = data[m][cid]
        non_exc = [r for r in runs if not r["exceptionType"] and r["recMid"] is not None]
        if non_exc:
            avg_mid = sum(r["recMid"] for r in non_exc) / len(non_exc)
            if avg_mid < elo:
                gap = elo - avg_mid
                pass_rate = sum((r["strictPass"] or 0) for r in runs) / len(runs)
                output.append(f"{cid} | {m} | expected_low={elo:,} | avg_mid={avg_mid:,.0f} | gap={gap:,.0f} | pass={pass_rate:.0%}")

# Overpricing
output.append("")
output.append("=== OVERPRICING ===")
for cid in case_ids:
    g = golden[cid]
    ehi = g["expected"]["high"]
    for m in models:
        runs = data[m][cid]
        non_exc = [r for r in runs if not r["exceptionType"] and r["recMid"] is not None]
        if non_exc:
            avg_mid = sum(r["recMid"] for r in non_exc) / len(non_exc)
            if avg_mid > ehi:
                gap = avg_mid - ehi
                pass_rate = sum((r["strictPass"] or 0) for r in runs) / len(runs)
                output.append(f"{cid} | {m} | expected_high={ehi:,} | avg_mid={avg_mid:,.0f} | gap={avg_mid - ehi:,.0f} | pass={pass_rate:.0%}")

# All-fail cases (all 3 models < 50% strict)
output.append("")
output.append("=== ALL-FAIL CASES ===")
for cid in case_ids:
    rates = {}
    all_fail = True
    for m in models:
        runs = data[m][cid]
        pr = sum((r["strictPass"] or 0) for r in runs) / len(runs) if runs else 0
        rates[m] = pr
        if pr >= 0.5:
            all_fail = False
    if all_fail:
        output.append(f"  {cid}: " + " | ".join(f"{m}={rates[m]:.0%}" for m in models))

# Category analysis
output.append("")
output.append("=== CATEGORY ANALYSIS ===")
categories = defaultdict(list)
for cid in case_ids:
    categories[golden[cid]["category"]].append(cid)

for cat, cids in sorted(categories.items()):
    output.append(f"")
    output.append(f"{cat} ({len(cids)} cases):")
    for m in models:
        total_pass = 0
        total_runs = 0
        total_lat = 0
        for cid in cids:
            runs = data[m][cid]
            total_pass += sum((r["strictPass"] or 0) for r in runs)
            total_runs += len(runs)
            total_lat += sum(r["latencyMs"] for r in runs)
        if total_runs > 0:
            output.append(f"  {m}: pass={total_pass/total_runs:.0%} avg_lat={total_lat/total_runs:.0f}ms")

# Latency comparison
output.append("")
output.append("=== LATENCY PER MODEL ===")
for m in models:
    lats = []
    for cid in case_ids:
        for r in data[m][cid]:
            lats.append(r["latencyMs"])
    lats.sort()
    avg = sum(lats)/len(lats)
    p50 = lats[len(lats)//2]
    p95 = lats[int(len(lats)*0.95)]
    output.append(f"{m}: avg={avg:.0f}ms p50={p50}ms p95={p95}ms min={lats[0]}ms max={lats[-1]}ms")

# Tag analysis
output.append("")
output.append("=== TAG ANALYSIS ===")
tag_stats = defaultdict(lambda: {m: {"pass": 0, "total": 0} for m in models})
for cid in case_ids:
    tags = golden[cid].get("tags", [])
    if not tags:
        tags = ["no_tag"]
    for tag in tags:
        for m in models:
            runs = data[m][cid]
            tag_stats[tag][m]["pass"] += sum((r["strictPass"] or 0) for r in runs)
            tag_stats[tag][m]["total"] += len(runs)

for tag, mstats in sorted(tag_stats.items()):
    parts = []
    for m in models:
        s = mstats[m]
        if s["total"] > 0:
            parts.append(f"{m}={s['pass']/s['total']:.0%}")
    output.append(f"{tag}: " + " | ".join(parts))

# Borderline cases (1-9 pass out of 10)
output.append("")
output.append("=== BORDERLINE CASES ===")
for cid in case_ids:
    for m in models:
        runs = data[m][cid]
        passes = sum((r["strictPass"] or 0) for r in runs)
        if 1 <= passes <= 9:
            output.append(f"{cid} | {m} | {int(passes)}/10")

with open("C:/Users/tkgkd/Desktop/Workspace/FairBid-ai-assist/backend/build/bench_analysis_output.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(output))

print("Done! Output written.")
