import type { IconName } from "@/components/icons";

export interface NavItem {
  href: string;
  label: string;
  icon: IconName;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

export const NAV_GROUPS: NavGroup[] = [
  {
    label: "Overview",
    items: [
      { href: "/", label: "Dashboard", icon: "dashboard" },
      { href: "/team-overview", label: "Team Overview", icon: "organization" },
      { href: "/workforce-summary", label: "Workforce Summary", icon: "organization" },
      { href: "/profile", label: "My Profile", icon: "profile" },
      { href: "/directory", label: "Directory", icon: "directory" },
      { href: "/organization", label: "Organization", icon: "organization" },
    ],
  },
  {
    label: "Work",
    items: [
      { href: "/attendance", label: "Attendance", icon: "attendance" },
      { href: "/leave", label: "Leave", icon: "leave" },
      { href: "/leave-summary", label: "Leave Summary", icon: "leave" },
      { href: "/payroll", label: "Payroll", icon: "payroll" },
      { href: "/expenses", label: "Expenses", icon: "expenses" },
      { href: "/expense-summary", label: "Expense Summary", icon: "expenses" },
      { href: "/projects", label: "Projects & Tasks", icon: "projects" },
      { href: "/performance", label: "Performance", icon: "performance" },
    ],
  },
  {
    label: "Resources",
    items: [
      { href: "/documents", label: "Documents & Policies", icon: "documents" },
      { href: "/assets", label: "Assets", icon: "assets" },
      { href: "/notifications", label: "Notifications", icon: "notifications" },
    ],
  },
];

export const NAV_ITEMS: NavItem[] = NAV_GROUPS.flatMap((group) => group.items);

export function labelForPath(pathname: string): string {
  const exact = NAV_ITEMS.find((item) => item.href === pathname);
  if (exact) return exact.label;
  const match = NAV_ITEMS.filter((item) => item.href !== "/" && pathname.startsWith(item.href)).sort(
    (a, b) => b.href.length - a.href.length,
  )[0];
  return match?.label ?? "";
}
