import type { Metadata } from "next";
import "./styles.css";
export const metadata: Metadata = { title: "GDB Employee Portal", description: "Grow Digital Bridge employee portal" };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) { return <html lang="en"><body>{children}</body></html>; }
