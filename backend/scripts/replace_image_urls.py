"""
기존 cases.jsonl(위키 HTTPS URL) → 신규 golden/cases.jsonl 의 image_url 필드 치환.
기존 이름 접미사(-a/-b/-s, -ml-a 등) 제거 + 토큰 Jaccard 유사도로 매칭.
"""
import json
import re
from pathlib import Path

OLD = Path("backend/src/test/resources/ai/cases.jsonl")
NEW = Path("backend/src/test/resources/ai/golden/cases.jsonl")


def norm_tokens(s):
    s = s.lower()
    s = re.sub(r"[_.]", "-", s)
    s = re.sub(r"-(\d+ml|\d+oz|\d+g|zoomx|detect|booster|facial|creme|cream|travel|align|am-std|10294)", "", s)
    s = re.sub(r"-(a|b|s)$", "", s)
    return set(t for t in s.split("-") if t)


def load_jsonl(path):
    out = []
    with path.open(encoding="utf-8") as f:
        for line in f:
            if line.strip():
                out.append(json.loads(line))
    return out


def main():
    old_cases = load_jsonl(OLD)
    new_cases = load_jsonl(NEW)

    # 자동 매칭 실패 케이스 수동 매핑 (new id → old id)
    manual = {
        "casio-g-shock-b": "gshock-5600",
        "la-mer-creme-60ml-a": "lamer-cream-60",
        "sk-ii-facial-230ml-a": "sk2-essence-230",
    }
    old_by_id = {o["id"]: o for o in old_cases}

    used = set()
    unmatched = []

    for new in new_cases:
        # 수동 매핑 우선
        if new["id"] in manual:
            old = old_by_id.get(manual[new["id"]])
            if old and old.get("imageUrls"):
                new["image_url"] = old["imageUrls"][0]
                used.add(old["id"])
                print(f"[MANUAL  ] {new['id']:40} <- {old['id']}")
                continue
        nt = norm_tokens(new["id"])
        best, best_score = None, 0.0
        for old in old_cases:
            if old["id"] in used:
                continue
            ot = norm_tokens(old["id"])
            common = nt & ot
            union = nt | ot
            score = len(common) / max(len(union), 1)
            if score > best_score:
                best_score, best = score, old
        if best and best_score >= 0.3:
            url = best.get("imageUrls", [None])[0]
            if url:
                new["image_url"] = url
                used.add(best["id"])
                print(f"[MATCH {best_score:.2f}] {new['id']:40} <- {best['id']}")
            else:
                unmatched.append(new["id"])
        else:
            unmatched.append(new["id"])

    print(f"\nMatched: {len(new_cases) - len(unmatched)}/{len(new_cases)}")
    if unmatched:
        print("Unmatched (left as-is):", unmatched)

    # Rewrite new cases
    with NEW.open("w", encoding="utf-8") as f:
        for case in new_cases:
            # 키 순서 유지: id, category, memo, image_url, expected, tags
            ordered = {}
            for k in ("id", "category", "memo", "image_url", "expected", "tags"):
                if k in case:
                    ordered[k] = case[k]
            # 혹시 다른 키가 있다면 뒤에
            for k, v in case.items():
                if k not in ordered:
                    ordered[k] = v
            f.write(json.dumps(ordered, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()
