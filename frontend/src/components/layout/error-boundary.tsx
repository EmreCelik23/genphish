"use client";

import { Component, type ErrorInfo, type ReactNode } from "react";
import { AlertTriangle, RefreshCw } from "lucide-react";

type Props = {
  children: ReactNode;
  /** Custom fallback. If omitted, default ErrorFallback is shown. */
  fallback?: ReactNode;
  title?: string;
  retryLabel?: string;
  unknownErrorLabel?: string;
};

type State = {
  hasError: boolean;
  error: Error | null;
};

/**
 * React class-based error boundary.
 * Catches render/lifecycle errors in the subtree and shows a recovery UI.
 */
export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  override componentDidCatch(error: Error, info: ErrorInfo) {
    // In production you'd send this to an error tracking service (Sentry, etc.)
    console.error("[ErrorBoundary] Caught:", error, info.componentStack);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  override render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback;

      return (
        <div className="flex min-h-[320px] flex-col items-center justify-center gap-4 rounded-2xl border border-rose-500/20 bg-rose-500/5 p-8 text-center">
          <div className="rounded-full border border-rose-500/30 bg-rose-500/10 p-3">
            <AlertTriangle className="h-6 w-6 text-rose-400" />
          </div>
          <div>
            <p className="text-sm font-medium text-text">{this.props.title ?? "Something went wrong"}</p>
            <p className="mt-1 font-mono text-xs text-muted">
              {this.state.error?.message ?? this.props.unknownErrorLabel ?? "Unknown error"}
            </p>
          </div>
          <button
            onClick={this.handleReset}
            className="flex items-center gap-2 rounded-lg border border-border bg-surface px-4 py-2 text-sm text-text transition-colors hover:border-accent hover:text-accent"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            {this.props.retryLabel ?? "Try Again"}
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
