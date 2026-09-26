"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Icon } from "@/components/icons";
import { labelForPath } from "@/lib/nav";

export function Breadcrumbs() {
  const pathname = usePathname() ?? "/";

  if (pathname === "/") {
    return null;
  }

  const label = labelForPath(pathname) || "Details";

  return (
    <nav aria-label="Breadcrumb" className="breadcrumbs">
      <Link href="/">Home</Link>
      <Icon name="chevronRight" size={14} className="sep" />
      <span className="current">{label}</span>
    </nav>
  );
}
