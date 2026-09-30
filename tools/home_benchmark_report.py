"""Summarise PopulatedHomeBenchmark's metrics and main-thread CPU, never emulator frame latency.

    python tools/home_benchmark_report.py --input harness-out/home-before \
        --trace-processor <trace_processor_shell> --output harness-out/home-before/report.json
"""

from __future__ import annotations

import argparse
import json
import re
import statistics
import subprocess
import tempfile
from pathlib import Path


PRESSES = {"alongPosterRow": 30, "acrossEightRows": 14, "browseWithHeroArtwork": 12}
SQL = """
WITH main AS (
  SELECT t.utid FROM thread t JOIN process p USING (upid)
  WHERE p.name = 'com.streammate.tv' AND t.tid = p.pid
), frames AS (
  SELECT s.ts, s.dur FROM slice s JOIN thread_track tt ON s.track_id = tt.id
  WHERE tt.utid IN (SELECT utid FROM main) AND s.name LIKE 'Choreographer#doFrame%' AND s.dur > 0
)
SELECT 'Running' AS state,
  ROUND(SUM(MIN(ts.ts + ts.dur, f.ts + f.dur) - MAX(ts.ts, f.ts)) / 1e6, 4) AS ms
FROM thread_state ts JOIN frames f ON ts.ts < f.ts + f.dur AND ts.ts + ts.dur > f.ts
WHERE ts.utid IN (SELECT utid FROM main) AND ts.state = 'Running';
"""
TOTAL_SQL = """
SELECT 'Running' AS state, ROUND(SUM(ts.dur) / 1e6, 4) AS ms
FROM thread_state ts JOIN thread t USING (utid) JOIN process p USING (upid)
WHERE p.name = 'com.streammate.tv' AND t.tid = p.pid AND ts.state = 'Running' AND ts.dur > 0;
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--trace-processor", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report: dict = {"scope": "API 30 emulator, relative CPU only; file-backed synthetic artwork", "benchmarks": {}}
    for path in args.input.rglob("*benchmarkData.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        report["context"] = data.get("context", {})
        for bench in data["benchmarks"]:
            if "PopulatedHomeBenchmark" in bench["className"]:
                report["benchmarks"][bench["name"]] = {"metrics": bench["metrics"]}
    if len(report["benchmarks"]) != 5:
        raise SystemExit("Expected all five populated-Home benchmarks, including the default-layout control")
    with tempfile.TemporaryDirectory(prefix="sohva-home-cpu-") as temp:
        for metric, sql in (("mainFrameCpuMsPerPress", SQL), ("mainCpuMsPerPress", TOTAL_SQL)):
            query = Path(temp) / "cpu.sql"
            query.write_text(sql, encoding="utf-8")
            for name, presses in PRESSES.items():
                values = []
                for trace in sorted(args.input.rglob(f"PopulatedHomeBenchmark_{name}_iter*.perfetto-trace")):
                    result = subprocess.run([str(args.trace_processor), "-q", str(query), str(trace)],
                                            capture_output=True, text=True, check=True)
                    match = re.search(r'"Running",([\d.]+)', result.stdout)
                    if not match:
                        raise SystemExit(f"No measured main-thread CPU in {trace}")
                    values.append(float(match.group(1)) / presses)
                if len(values) != 5:
                    raise SystemExit(f"Expected five traces for {name}, found {len(values)}; keep each run in its own folder")
                report["benchmarks"][name][metric] = {
                    "median": statistics.median(values), "minimum": min(values), "maximum": max(values), "runs": values,
                }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    for name, bench in report["benchmarks"].items():
        print(name)
        for key in ("timeToInitialDisplayMs", "timeToFullDisplayMs", "memoryHeapSizeMaxKb", "memoryRssAnonMaxKb", "Home:ScreenCount"):
            if key in bench["metrics"]:
                print(f"  {key}: {bench['metrics'][key]['median']:.2f}")
        if "mainFrameCpuMsPerPress" in bench:
            print(f"  main-frame CPU ms/press: {bench['mainFrameCpuMsPerPress']['median']:.2f}")
            print(f"  all main-thread CPU ms/press: {bench['mainCpuMsPerPress']['median']:.2f}")
    print(f"Saved {args.output}")


if __name__ == "__main__":
    main()
