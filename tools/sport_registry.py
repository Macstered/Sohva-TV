"""Offline stable-identity review. This tool never requests keys or makes network calls."""

import argparse
import json
from pathlib import Path

from team_dictionary.registry import Registry
from team_dictionary.store import Store, collection_lock


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=Path(__file__).resolve().parents[1] / ".local/team-dictionary")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("seed", help="Assign persistent pending UUIDs from saved collection records")
    export = commands.add_parser("export", help="Export only reviewed source data")
    export.add_argument("--output", type=Path, required=True)
    for name in ("identity", "binding", "alias", "credit", "membership"):
        review = commands.add_parser("review-" + name)
        review.add_argument("--decision", choices=("pending", "accepted", "rejected"), required=True)
        review.add_argument("--evidence", required=True)
        if name in ("identity", "binding", "alias"):
            review.add_argument("--uuid", required=True)
        if name == "identity":
            review.add_argument("--category", required=True)
        if name == "binding":
            for argument in ("provider", "product", "kind", "external-id"):
                review.add_argument("--" + argument, required=True)
        if name in ("alias", "credit", "membership"):
            review.add_argument("--source", required=True)
        if name == "alias":
            review.add_argument("--spelling", required=True)
        if name == "credit":
            review.add_argument("--licence", required=True)
        if name == "membership":
            review.add_argument("--participant", required=True)
            review.add_argument("--competition", required=True)
            review.add_argument("--season", required=True)
        if name in ("alias", "membership"):
            review.add_argument("--valid-from", default="")
            review.add_argument("--valid-to", default="")
    args = parser.parse_args()
    with collection_lock(args.data_dir):
        store = Store(args.data_dir / "research.sqlite")
        try:
            registry = Registry(store.db)
            if args.command == "seed":
                print(json.dumps(registry.seed(), sort_keys=True))
            elif args.command == "export":
                print(json.dumps(registry.export(args.output), sort_keys=True))
            elif args.command == "review-identity":
                registry.review_identity(args.uuid, args.category, args.decision, args.evidence)
            elif args.command == "review-binding":
                registry.review_binding(args.provider, args.product, args.kind, args.external_id, args.uuid, args.decision, args.evidence)
            elif args.command == "review-alias":
                registry.review_alias(args.uuid, args.spelling, args.source, args.decision, args.evidence, args.valid_from, args.valid_to)
            elif args.command == "review-credit":
                registry.credit(args.source, args.evidence, args.licence, args.decision)
            elif args.command == "review-membership":
                registry.membership(args.participant, args.competition, args.season, args.source, args.evidence,
                                    args.decision, args.valid_from, args.valid_to)
        finally:
            store.close()


if __name__ == "__main__":
    main()
