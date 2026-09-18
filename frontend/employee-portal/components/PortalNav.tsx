"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const LINKS = [
  { href: "/", label: "Dashboard" },
  { href: "/profile", label: "Profile" },
  { href: "/directory", label: "Directory" },
  { href: "/organization", label: "Organization" },
] as const;

export function PortalNav() {
  const pathname = usePathname();

  return (
    <nav aria-label="Primary" className="portal-nav">
      <ul>
        {LINKS.map((link) => {
          const isActive = link.href === "/" ? pathname === "/" : pathname?.startsWith(link.href);
          return (
            <li key={link.href}>
              <Link href={link.href} aria-current={isActive ? "page" : undefined}>
                {link.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
