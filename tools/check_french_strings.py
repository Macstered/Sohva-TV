"""Check complete French UI coverage and Android formatting before building.

French is an owner-requested extension to the original seven languages. Unlike the
older drafts, it must cover every translatable UI resource, including setup pages
and newly added errors. Android otherwise silently falls back to English.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "ui/design/src/main/res"
# Java/Android Formatter conversions, with explicit positions and escaped percent signs.
FORMAT = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?(?:[tT][A-Za-z]|[bBhHsScCdoxXeEfgGaAn%])")


def formats(text: str) -> Counter[str]:
    return Counter(match.group(0) for match in FORMAT.finditer(text))


def resources(folder: Path) -> dict[str, ET.Element]:
    result: dict[str, ET.Element] = {}
    for path in sorted(folder.glob("*.xml")):
        for element in ET.parse(path).getroot():
            if element.tag not in {"string", "plurals", "string-array"} or element.get("translatable") == "false":
                continue
            name = element.attrib["name"]
            if name in result:
                raise ValueError(f"Duplicate resource {name} in {folder}")
            result[name] = element
    return result


def check_pair(name: str, english: ET.Element, french: ET.Element) -> list[str]:
    errors: list[str] = []
    if english.tag != french.tag:
        return [f"{name}: resource type differs"]
    if english.tag == "string":
        pairs = [(name, english, french)]
    elif english.tag == "plurals":
        originals = {item.attrib["quantity"]: item for item in english}
        translated = {item.attrib["quantity"]: item for item in french}
        # French needs 'many' in newer CLDR versions, as well as zero/one and other.
        if set(translated) != set(originals) | {"one", "many", "other"}:
            errors.append(f"{name}: French plural quantities are incomplete")
        pairs = [(f"{name}/{quantity}", originals.get(quantity, originals["other"]), item)
                 for quantity, item in translated.items()]
    else:
        if len(english) != len(french):
            errors.append(f"{name}: array length differs")
        pairs = [(f"{name}/{index}", a, b) for index, (a, b) in enumerate(zip(english, french))]
    for label, original, translated in pairs:
        source = "".join(original.itertext())
        text = "".join(translated.itertext())
        if not text.strip():
            errors.append(f"{label}: empty translation")
        if formats(source) != formats(text):
            errors.append(f"{label}: format arguments differ: {formats(source)} vs {formats(text)}")
        if source.count(r"\n") != text.count(r"\n"):
            errors.append(f"{label}: line breaks differ")
        if [child.tag for child in original] != [child.tag for child in translated]:
            errors.append(f"{label}: inline markup differs")
    return errors


def main() -> None:
    english, french = resources(RES / "values"), resources(RES / "values-fr")
    errors = [f"Missing French resource: {name}" for name in sorted(english.keys() - french.keys())]
    errors += [f"French resource has no English key: {name}" for name in sorted(french.keys() - english.keys())]
    for name in sorted(english.keys() & french.keys()):
        errors.extend(check_pair(name, english[name], french[name]))
    advertised = [item.attrib["{http://schemas.android.com/apk/res/android}name"]
                  for item in ET.parse(ROOT / "app/src/main/res/xml/locales_config.xml").getroot()]
    for path, field in [("app/src/main/java/com/sohva/tv/app/AppLocales.kt", "SUPPORTED"),
                        ("build-logic/src/main/kotlin/SohvaBuild.kt", "LOCALES"),
                        ("feature/settings/src/main/kotlin/com/sohva/tv/feature/settings/GeneralSettings.kt", "LANGUAGES")]:
        match = re.search(rf"val {field}: List<String> = listOf\(([^)]+)\)", (ROOT / path).read_text(encoding="utf-8"))
        choices = re.findall(r'"([a-z]+)"', match.group(1)) if match else []
        if choices != advertised or "fr" not in choices:
            errors.append(f"{path}: interface choices differ from Android's locale configuration or omit French")
    if errors:
        sys.exit("French resource check failed:\n" + "\n".join(errors))
    print(f"French: {len(french)} resources, complete coverage, matching format arguments and plurals; locale choices aligned")


if __name__ == "__main__":
    main()
