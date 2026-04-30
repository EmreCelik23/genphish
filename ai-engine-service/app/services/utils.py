def infer_department_code(prompt: str | None) -> str:
    """Infers an internal department code from the prompt."""
    if not prompt:
        return "GENERAL"

    normalized = prompt.lower()
    if "finans" in normalized or "finance" in normalized:
        return "FINANCE"
    if "ik" in normalized or "human resources" in normalized or "hr" in normalized:
        return "HR"
    if "it" in normalized or "bilgi" in normalized or "teknoloji" in normalized:
        return "IT"
    return "GENERAL"

def infer_department_label(prompt: str | None) -> str:
    """Infers a human-readable department label from the prompt."""
    code = infer_department_code(prompt)
    mapping = {
        "FINANCE": "Finance",
        "HR": "Human Resources",
        "IT": "IT",
        "GENERAL": "General corporate"
    }
    return mapping[code]