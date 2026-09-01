#!/usr/bin/env python3
"""Cloud-only screenshot comparison gate for the locked Android UI V2 mockups."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter, ImageStat

CASES = {
    "home": ("home.png", "home-light.png"),
    "scan": ("scan.png", "scan-light.png"),
    "workspace": ("workspace.png", "workspace-light.png"),
}
TARGET_SIZE = (390, 844)
MIN_VISUAL_SIMILARITY = 0.48
MIN_EDGE_SIMILARITY = 0.42
MAX_BLANK_RATIO = 0.82


def normalize_reference(image: Image.Image) -> Image.Image:
    return image.convert("RGB").resize(TARGET_SIZE, Image.Resampling.LANCZOS)


def normalize_actual(image: Image.Image) -> Image.Image:
    rgb = image.convert("RGB")
    width, height = rgb.size
    top = int(height * 0.025)
    bottom = int(height * 0.955)
    if bottom <= top:
        raise ValueError("Screenshot crop is invalid")
    return rgb.crop((0, top, width, bottom)).resize(
        TARGET_SIZE, Image.Resampling.LANCZOS
    )


def similarity(left: Image.Image, right: Image.Image) -> float:
    diff = ImageChops.difference(left, right)
    mean = sum(ImageStat.Stat(diff).mean) / 3.0
    return max(0.0, 1.0 - mean / 255.0)


def edge_similarity(left: Image.Image, right: Image.Image) -> float:
    a = left.convert("L").filter(ImageFilter.FIND_EDGES)
    b = right.convert("L").filter(ImageFilter.FIND_EDGES)
    return similarity(a.convert("RGB"), b.convert("RGB"))


def blank_ratio(image: Image.Image) -> float:
    pixels = image.resize((98, 211), Image.Resampling.BILINEAR).getdata()
    blank = sum(1 for r, g, b in pixels if r > 242 and g > 240 and b > 246)
    return blank / (98 * 211)


def run_case(name: str, baseline: Path, actual: Path) -> dict:
    if not baseline.is_file():
        raise FileNotFoundError(f"Missing baseline: {baseline}")
    if not actual.is_file():
        raise FileNotFoundError(f"Missing screenshot: {actual}")

    reference = normalize_reference(Image.open(baseline))
    captured = normalize_actual(Image.open(actual))
    visual = similarity(reference, captured)
    edges = edge_similarity(reference, captured)
    blank = blank_ratio(captured)
    passed = (
        visual >= MIN_VISUAL_SIMILARITY
        and edges >= MIN_EDGE_SIMILARITY
        and blank <= MAX_BLANK_RATIO
    )
    diff = ImageChops.difference(reference, captured)
    diff.save(actual.with_name(f"{name}-diff.png"))
    reference.save(actual.with_name(f"{name}-reference-normalized.png"))
    captured.save(actual.with_name(f"{name}-actual-normalized.png"))
    return {
        "case": name,
        "baseline": str(baseline),
        "actual": str(actual),
        "visual_similarity": round(visual, 4),
        "edge_similarity": round(edges, 4),
        "blank_ratio": round(blank, 4),
        "status": "PASSED" if passed else "FAILED",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline-dir", type=Path, required=True)
    parser.add_argument("--actual-dir", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--json-report", type=Path, required=True)
    args = parser.parse_args()

    results = []
    for name, (baseline_name, actual_name) in CASES.items():
        results.append(
            run_case(
                name,
                args.baseline_dir / baseline_name,
                args.actual_dir / actual_name,
            )
        )

    passed = all(result["status"] == "PASSED" for result in results)
    payload = {
        "final_result": "passed" if passed else "failed",
        "method": "same-state normalized pixel and edge comparison",
        "thresholds": {
            "minimum_visual_similarity": MIN_VISUAL_SIMILARITY,
            "minimum_edge_similarity": MIN_EDGE_SIMILARITY,
            "maximum_blank_ratio": MAX_BLANK_RATIO,
        },
        "results": results,
        "evidence_boundary": (
            "This gate detects major layout, palette, blank-screen, and hierarchy drift. "
            "It does not establish mathematical pixel identity across Android system chrome, "
            "font rasterization, or dynamic document content."
        ),
    }
    args.json_report.parent.mkdir(parents=True, exist_ok=True)
    args.json_report.write_text(json.dumps(payload, indent=2), encoding="utf-8")

    lines = [
        "# Design QA",
        "",
        f"final result: {'passed' if passed else 'failed'}",
        "",
        "| Screen | Visual similarity | Edge similarity | Blank ratio | Status |",
        "|---|---:|---:|---:|---|",
    ]
    for result in results:
        lines.append(
            f"| {result['case']} | {result['visual_similarity']:.4f} | "
            f"{result['edge_similarity']:.4f} | {result['blank_ratio']:.4f} | "
            f"{result['status']} |"
        )
    lines.extend(
        [
            "",
            "Method: same-state normalized pixel and edge comparison against the three "
            "product-owner-locked mockups.",
            "",
            "Evidence boundary: this gate detects major layout, palette, blank-screen, "
            "and hierarchy drift. It does not establish mathematical pixel identity "
            "across Android system chrome, font rasterization, or dynamic document content.",
        ]
    )
    args.report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
