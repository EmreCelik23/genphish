"use client";

import { useCallback, useState } from "react";

import type { ValidatorFn } from "@/lib/hooks/validators";

// ── Types ──────────────────────────────────────────────────────────

/** Map of field names to their validator chains. */
export type ValidationSchema<T extends Record<string, unknown>> = {
  [K in keyof T]?: ValidatorFn[];
};

/** Map of field names to their current error message (or undefined). */
export type FieldErrors<T extends Record<string, unknown>> = {
  [K in keyof T]?: string;
};

// ── Hook ───────────────────────────────────────────────────────────

export function useFormValidation<T extends Record<string, unknown>>(schema: ValidationSchema<T>) {
  const [errors, setErrors] = useState<FieldErrors<T>>({});
  const [touched, setTouched] = useState<Partial<Record<keyof T, boolean>>>({});

  /** Validate a single field. Returns the first error or undefined. */
  const validateField = useCallback(
    (field: keyof T, value: unknown): string | undefined => {
      const validators = schema[field];
      if (!validators) return undefined;

      for (const validator of validators) {
        const error = validator(value);
        if (error) return error;
      }
      return undefined;
    },
    [schema]
  );

  /** Validate a single field and update error state. */
  const validateSingle = useCallback(
    (field: keyof T, value: unknown) => {
      const error = validateField(field, value);
      setErrors((prev) => {
        if (prev[field] === error) return prev;
        return { ...prev, [field]: error };
      });
      return !error;
    },
    [validateField]
  );

  /** Validate all fields at once. Returns true if all fields pass. */
  const validate = useCallback(
    (data: T): boolean => {
      const next: FieldErrors<T> = {};
      let valid = true;

      for (const field of Object.keys(schema) as (keyof T)[]) {
        const error = validateField(field, data[field]);
        if (error) {
          next[field] = error;
          valid = false;
        }
      }

      setErrors(next);

      // Mark all fields as touched after full validation.
      const allTouched: Partial<Record<keyof T, boolean>> = {};
      for (const field of Object.keys(schema) as (keyof T)[]) {
        allTouched[field] = true;
      }
      setTouched(allTouched);

      return valid;
    },
    [schema, validateField]
  );

  /** Clear a single field error. */
  const clearError = useCallback((field: keyof T) => {
    setErrors((prev) => {
      if (!prev[field]) return prev;
      const next = { ...prev };
      delete next[field];
      return next;
    });
  }, []);

  /** Clear all errors. */
  const clearAllErrors = useCallback(() => {
    setErrors({});
    setTouched({});
  }, []);

  /** Mark a field as touched. Use for onBlur validation. */
  const touchField = useCallback(
    (field: keyof T, value: unknown) => {
      setTouched((prev) => (prev[field] ? prev : { ...prev, [field]: true }));
      validateSingle(field, value);
    },
    [validateSingle]
  );

  /** Get the error for a field, but only if it has been touched. */
  const getFieldError = useCallback(
    (field: keyof T): string | undefined => {
      if (!touched[field]) return undefined;
      return errors[field];
    },
    [errors, touched]
  );

  const isValid = Object.values(errors).every((e) => !e);

  return {
    errors,
    touched,
    validate,
    validateSingle,
    clearError,
    clearAllErrors,
    touchField,
    getFieldError,
    isValid
  };
}
