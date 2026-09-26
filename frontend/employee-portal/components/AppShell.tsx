"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { NAV_GROUPS } from "@/lib/nav";
import { Icon } from "@/components/icons";

function isActive(pathname: string, href: string): boolean {
  return href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
}

function initialsFor(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  return (parts[0][0] + (parts[1]?.[0] ?? "")).toUpperCase();
}

interface AppShellProps {
  children: React.ReactNode;
  displayName: string | null;
  jobTitle: string | null;
}

export function AppShell({ children, displayName, jobTitle }: AppShellProps) {
  const pathname = usePathname() ?? "/";
  const router = useRouter();
  const [navOpen, setNavOpen] = useState(false);

  function handleSearchSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = new FormData(event.currentTarget).get("query");
    const search = typeof query === "string" && query.trim() ? `?query=${encodeURIComponent(query.trim())}` : "";
    router.push(`/directory${search}`);
  }

  useEffect(() => {
    setNavOpen(false);
  }, [pathname]);

  return (
    <div className={`app-shell${navOpen ? " nav-open" : ""}`}>
      <div className="sidebar-backdrop" onClick={() => setNavOpen(false)} aria-hidden="true" />
      <aside className="app-sidebar" aria-label="Primary navigation">
        <div className="app-sidebar-brand">
          <span className="mark">GDB</span>
          <div>
            <div className="name">Grow Digital Bridge</div>
            <div className="sub">Employee Portal</div>
          </div>
          <button
            type="button"
            className="icon-btn app-sidebar-close"
            style={{ color: "#fff" }}
            aria-label="Close navigation"
            onClick={() => setNavOpen(false)}
          >
            <Icon name="close" size={18} />
          </button>
        </div>
        <nav className="sidebar-nav" aria-label="Primary">
          {NAV_GROUPS.map((group) => (
            <div key={group.label}>
              <div className="sidebar-group-label">{group.label}</div>
              <ul>
                {group.items.map((item) => {
                  const active = isActive(pathname, item.href);
                  return (
                    <li key={item.href}>
                      <Link href={item.href} aria-current={active ? "page" : undefined} className="sidebar-link">
                        <Icon name={item.icon} size={18} />
                        <span>{item.label}</span>
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>
        <div className="app-sidebar-footer">GDB Employee Portal &middot; internal use only</div>
      </aside>

      <div className="app-main-region">
        <header className="app-header">
          <button
            type="button"
            className="app-header-menu-btn"
            aria-label="Open navigation"
            onClick={() => setNavOpen(true)}
          >
            <Icon name="menu" size={22} />
          </button>

          <div className="app-search">
            <form role="search" aria-label="Search employee directory" onSubmit={handleSearchSubmit}>
              <Icon name="search" size={16} />
              <input type="search" name="query" placeholder="Search the directory…" aria-label="Search employees" />
            </form>
          </div>

          <div className="app-header-actions">
            <Link href="/notifications" className="icon-btn" aria-label="Notifications">
              <Icon name="bell" size={19} />
            </Link>
            <Link href="/profile" className="header-profile">
              <span className="avatar">{displayName ? initialsFor(displayName) : "?"}</span>
              <span className="header-profile-meta">
                <span className="name">{displayName ?? "My account"}</span>
                <span className="role">{jobTitle ?? "View profile"}</span>
              </span>
            </Link>
          </div>
        </header>

        <div className="app-content" id="main">
          {children}
        </div>
      </div>
    </div>
  );
}
