"""Compile a reviewed dictionary snapshot; the default output is ignored build storage."""

import argparse
import json
from pathlib import Path

from team_dictionary.compiler import compile_snapshot


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "feature/sport/build/generated/dictionary")
    args = parser.parse_args()
    print(json.dumps(compile_snapshot(args.source, args.output), sort_keys=True))


if __name__ == "__main__":
    main()
