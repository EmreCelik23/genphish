/**
 * Composable validator functions.
 *
 * Each validator returns `undefined` when valid and an error message string
 * when invalid. They can be combined freely via arrays and passed to
 * `useFormValidation`.
 */

export type ValidatorFn = (value: unknown) => string | undefined;

// ── Helpers ────────────────────────────────────────────────────────

function toString(value: unknown): string {
  if (typeof value === "string") return value;
  if (value == null) return "";
  return String(value);
}

// ── Built-in validators ────────────────────────────────────────────

export function required(message = "This field is required"): ValidatorFn {
  return (value) => {
    const str = toString(value).trim();
    if (!str) return message;
    return undefined;
  };
}

export function minLength(min: number, message?: string): ValidatorFn {
  return (value) => {
    const str = toString(value);
    if (str.length > 0 && str.length < min) {
      return message ?? `At least ${min} characters required`;
    }
    return undefined;
  };
}

export function maxLength(max: number, message?: string): ValidatorFn {
  return (value) => {
    const str = toString(value);
    if (str.length > max) {
      return message ?? `Maximum ${max} characters allowed`;
    }
    return undefined;
  };
}

export function email(message = "Invalid email address"): ValidatorFn {
  return (value) => {
    const str = toString(value).trim();
    if (!str) return undefined; // Let `required` handle empty
    // Simple but effective email regex
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(str)) {
      return message;
    }
    return undefined;
  };
}

export function url(message = "Invalid URL"): ValidatorFn {
  return (value) => {
    const str = toString(value).trim();
    if (!str) return undefined;
    try {
      new URL(str);
      return undefined;
    } catch {
      return message;
    }
  };
}

export function domain(message = "Invalid domain"): ValidatorFn {
  return (value) => {
    const str = toString(value).trim();
    if (!str) return undefined;
    if (!/^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\.[a-zA-Z]{2,})+$/.test(str)) {
      return message;
    }
    return undefined;
  };
}

export function pattern(regex: RegExp, message = "Invalid format"): ValidatorFn {
  return (value) => {
    const str = toString(value).trim();
    if (!str) return undefined;
    if (!regex.test(str)) {
      return message;
    }
    return undefined;
  };
}

export function custom(fn: (value: unknown) => string | undefined): ValidatorFn {
  return fn;
}
