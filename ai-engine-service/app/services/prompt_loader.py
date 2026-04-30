from pathlib import Path


class PromptLoader:
    def __init__(self, prompt_dir: Path) -> None:
        self._prompt_dir = prompt_dir

    def load(self, name: str) -> str:
        path = self._prompt_dir / name
        if not path.exists() or not path.is_file():
            raise FileNotFoundError(f"Prompt file not found: {path}")
        return path.read_text(encoding="utf-8").strip()
