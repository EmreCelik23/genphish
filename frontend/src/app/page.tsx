"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-context";

export default function HomePage() {
  const router = useRouter();
  const { auth } = useAuth();

  useEffect(() => {
    if (auth.isAuthenticated) {
      router.replace("/dashboard");
    } else {
      router.replace("/access");
    }
  }, [auth.isAuthenticated, router]);

  return null;
}
