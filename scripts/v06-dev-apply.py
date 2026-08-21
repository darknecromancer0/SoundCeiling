from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count == 0 and new in text:
        print(f"already applied: {path}")
        return
    if count != 1:
        raise SystemExit(f"expected exactly one match in {path}, found {count}: {old!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"patched: {path}")


replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    "LoudnessControlPolicy.Result normal = LoudnessControlPolicy.decide(now,\n"
    "                            loud.lufsLike, blockPeak, false, current,",
    "LoudnessControlPolicy.Result normal = LoudnessControlPolicy.decide(now,\n"
    "                            loud.controlLoudnessDb, blockPeak, false, current,",
)
