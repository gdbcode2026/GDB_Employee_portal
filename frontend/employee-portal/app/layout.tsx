import type { Metadata } from "next";
import "./styles.css";
import { PortalNav } from "@/components/PortalNav";

export const metadata: Metadata = {
  title: "GDB Employee Portal",
  description: "Grow Digital Bridge employee portal",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <a className="skip-link" href="#main">
          Skip to content
        </a>
        <header>
          <p className="brand">Grow Digital Bridge</p>
          <h1>Employee Portal</h1>
          <PortalNav />
        </header>
        <main id="main">{children}</main>
      </body>
    </html>
  );
}
