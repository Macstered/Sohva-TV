"""Synthetic owner-scale dictionary for emulator tests only; never a production review source."""

import argparse
import json
import random
import tempfile
import uuid
from pathlib import Path

from team_dictionary.compiler import compile_snapshot
from team_dictionary.store import normalise

ROOT = Path(__file__).resolve().parents[1]


def build(output: Path, participants: int = 40_369, extra_names: int = 12_568) -> dict:
    output = output.resolve()
    if not output.is_relative_to(ROOT / "app/build"):
        raise ValueError("Stress assets belong only under the app's ignored build directory.")
    random_ids = random.Random(60_2026)
    identities, bindings, aliases, memberships = [], [], [], []
    source = "https://provider.example/synthetic-dictionary"

    def add(product: str, kind: str, external: str, name: str, category: str = "senior-men") -> str:
        # Reproducible synthetic UUIDs, unrelated to IDs or names. Production UUIDs are seeded
        # once and persisted in Registry; this generator never touches that registry.
        identity = str(uuid.UUID(int=random_ids.getrandbits(128), version=4))
        identities.append(dict(uuid=identity, sport="basketball" if product == "nba" else product,
                               kind=kind, name=name, country="", category=category, status="accepted", evidence="Synthetic fixture"))
        bindings.append(dict(provider="fixture-provider", product=product, kind=kind, external_id=external,
                             uuid=identity, status="accepted", evidence="Synthetic fixture"))
        alias(identity, name)
        return identity

    def alias(identity: str, name: str) -> None:
        aliases.append(dict(uuid=identity, spelling=name, normalised=normalise(name), language="en", source=source,
                            valid_from="", valid_to="", status="accepted", evidence="Synthetic fixture"))

    cup = add("football", "competition", "990001", "Fixture Cup", "open")
    league = add("football", "competition", "990002", "Fixture League", "open")
    for i in range(participants):
        if i < 1_024:
            name = f"{'Northbridge' if i % 2 == 0 else 'Harbor'} {i // 2}"
        else:
            name = f"Fixture Club {i}"
        identity = add("football", "team", str(i + 1), name)
        if i < extra_names:
            alias(identity, f"Fixture Alternate {i}")
        if i < 1_024:
            memberships.append(dict(participant=identity, competition=cup if i % 2 == 0 else league, season="2026",
                                    valid_from="", valid_to="", source=source, status="accepted", evidence="Synthetic roster"))
    snapshot = dict(format=1, normalisation=1, identities=identities, bindings=bindings, aliases=aliases,
                    memberships=memberships, collisions=[], credits=[dict(source=source, attribution="Fictional emulator fixture; no collected data.",
                    licence="CC0-1.0", status="accepted")])
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="dictionary-fixture-", dir=output.parent) as temporary:
        path = Path(temporary) / "snapshot.json"
        path.write_text(json.dumps(snapshot, sort_keys=True), encoding="utf-8")
        return compile_snapshot(path, output)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "app/build/generated/sportDictionaryFixture/testDictionary")
    args = parser.parse_args()
    print(json.dumps(build(args.output), sort_keys=True))


if __name__ == "__main__":
    main()
