import * as React from "react";

import { cn } from "@/lib/utils";

type Props = {
  /** Label text above the field. */
  label?: string;
  /** If true, shows a required indicator (*). */
  required?: boolean;
  /** Helper text below the field. */
  hint?: string;
  /** Error message — overrides hint when present. */
  error?: string;
  /** Children (typically Input, Select, Textarea). */
  children: React.ReactNode;
  /** Extra wrapper class names. */
  className?: string;
};

export function FormField({ label, required, hint, error, children, className }: Props) {
  const generatedId = React.useId();
  const hintId = `${generatedId}-hint`;
  const errorId = `${generatedId}-error`;

  let fieldId: string | undefined;
  let content = children;

  if (React.isValidElement(children)) {
    const childProps = children.props as {
      id?: string;
      "aria-describedby"?: string;
      "aria-invalid"?: boolean;
    };

    fieldId = childProps.id ?? `${generatedId}-control`;
    const describedBy = [childProps["aria-describedby"], error ? errorId : hint ? hintId : undefined]
      .filter(Boolean)
      .join(" ") || undefined;

    content = React.cloneElement(children as React.ReactElement<Record<string, unknown>>, {
      id: fieldId,
      "aria-invalid": error ? true : childProps["aria-invalid"],
      "aria-describedby": describedBy
    });
  }

  return (
    <div className={cn("space-y-1.5", className)}>
      {label ? (
        <label htmlFor={fieldId} className="block text-xs uppercase tracking-[0.12em] text-muted">
          {label}
          {required ? <span className="ml-0.5 text-rose-400">*</span> : null}
        </label>
      ) : null}

      <div
        className={cn(
          "transition-colors duration-150",
          error && "[&>input]:border-rose-500/70 [&>select]:border-rose-500/70 [&>textarea]:border-rose-500/70 [&>input]:focus:border-rose-500 [&>select]:focus:border-rose-500 [&>textarea]:focus:border-rose-500"
        )}
      >
        {content}
      </div>

      {error ? (
        <p id={errorId} className="animate-in fade-in slide-in-from-top-1 text-xs text-rose-400 duration-200">
          {error}
        </p>
      ) : hint ? (
        <p id={hintId} className="text-xs text-muted/70">{hint}</p>
      ) : null}
    </div>
  );
}
