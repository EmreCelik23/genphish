import * as React from "react";

import { cn } from "@/lib/utils";

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => {
    return (
      <input
        ref={ref}
        className={cn(
          "h-10 w-full rounded-lg border border-border bg-panel px-3 text-sm text-text outline-none transition-[border-color,box-shadow] placeholder:text-muted focus:border-accent/70 focus:shadow-[0_0_0_3px_rgba(56,189,248,0.12)]",
          className
        )}
        {...props}
      />
    );
  }
);

Input.displayName = "Input";
