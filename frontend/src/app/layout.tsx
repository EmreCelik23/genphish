import { Providers } from "@/app/providers";

import "./globals.css";

export const metadata = {
  title: "GenPhish Console",
  description: "Premium security operations workspace for phishing simulations"
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body className="bg-surface text-text antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
