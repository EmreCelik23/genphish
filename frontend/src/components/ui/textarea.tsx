import * as React from "react";

import { cn } from "@/lib/utils";

export const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => {
    return (
      <textarea
        ref={ref}
        className={cn(
          "min-h-[112px] w-full rounded-lg border border-border bg-panel px-3 py-2 text-sm text-text outline-none transition-[border-color,box-shadow] placeholder:text-muted focus:border-accent/70 focus:shadow-[0_0_0_3px_rgba(56,189,248,0.12)]",
          className
        )}
        {...props}
      />
    );
  }
);

Textarea.displayName = "Textarea";
