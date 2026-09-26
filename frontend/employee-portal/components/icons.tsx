// Minimal hand-rolled inline SVG icon set. No icon library dependency is added; every icon is a
// tiny stroked path sized via `size` so the sidebar/header/cards can stay dependency-free.

export type IconName =
  | "dashboard"
  | "profile"
  | "directory"
  | "organization"
  | "attendance"
  | "leave"
  | "payroll"
  | "expenses"
  | "projects"
  | "performance"
  | "documents"
  | "assets"
  | "notifications"
  | "search"
  | "bell"
  | "menu"
  | "close"
  | "chevronRight"
  | "checkCircle"
  | "clock"
  | "calendar"
  | "plus"
  | "download"
  | "print"
  | "arrowRight"
  | "building"
  | "alertTriangle"
  | "info"
  | "logout"
  | "signIn"
  | "inbox"
  | "target";

const PATHS: Record<IconName, string> = {
  dashboard: "M3 3h8v8H3V3Zm10 0h8v5h-8V3ZM3 13h8v8H3v-8Zm10 3h8v5h-8v-5Z",
  profile: "M12 12a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9Zm-8 9c0-3.9 3.58-7 8-7s8 3.1 8 7",
  directory: "M4 4h16v4H4V4Zm0 6h10v4H4v-4Zm0 6h16v4H4v-4Z",
  organization: "M12 3v5m0 0H6a2 2 0 0 0-2 2v3m8-5h6a2 2 0 0 1 2 2v3M6 13v5h4v-5H6Zm8 0v5h4v-5h-4Z",
  attendance: "M12 22a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-14v5l3 2",
  leave: "M8 3v3m8-3v3M4 8h16M6 5h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Zm2 8 2.5 2.5L16 10",
  payroll: "M4 6h16v12H4V6Zm0 4h16M8 16h4",
  expenses: "M12 2v20m5-17H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6",
  projects: "M4 5h11v14H4V5Zm11 4h5v10h-5M7 9h5m-5 4h5m-5 4h3",
  performance: "M4 20V10m6 10V4m6 16v-7m6 7V8",
  documents: "M7 3h7l5 5v13H7V3Zm6 0v5h5M9 13h6m-6 4h6",
  assets: "M4 8 12 4l8 4-8 4-8-4Zm0 0v8l8 4m0-8v8m0-8 8-4v8l-8 4",
  notifications: "M6 8a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6Zm4.5 10a1.5 1.5 0 0 0 3 0",
  search: "M11 19a8 8 0 1 1 0-16 8 8 0 0 1 0 16Zm10 2-5.5-5.5",
  bell: "M6 8a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6Zm4.5 10a1.5 1.5 0 0 0 3 0",
  menu: "M4 6h16M4 12h16M4 18h16",
  close: "M6 6l12 12M18 6 6 18",
  chevronRight: "m9 6 6 6-6 6",
  checkCircle: "M12 22a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm-4-9 2.5 2.5L16 10",
  clock: "M12 22a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-14v5l3 2",
  calendar: "M7 3v3m10-3v3M4 8h16M5 5h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z",
  plus: "M12 5v14M5 12h14",
  download: "M12 3v12m0 0 4-4m-4 4-4-4M5 19h14",
  print: "M6 9V3h12v6M6 18h12v4H6v-4Zm-2-9h16a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-6a1 1 0 0 1 1-1Z",
  arrowRight: "M5 12h14m0 0-6-6m6 6-6 6",
  building: "M4 21V5a1 1 0 0 1 1-1h8a1 1 0 0 1 1 1v16M4 21h16M9 8h.01M9 12h.01M9 16h.01M14 8h.01M14 12h.01M14 16h.01M17 21v-8h3v8",
  alertTriangle: "M12 9v4m0 4h.01M10.3 3.9 2.6 18a1 1 0 0 0 .9 1.5h17a1 1 0 0 0 .9-1.5L13.7 3.9a1 1 0 0 0-1.7 0Z",
  info: "M12 22a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-9.5v5M12 8h.01",
  logout: "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4m6 14 5-5-5-5m5 5H9",
  signIn: "M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4m-3-14-5 5 5 5m-5-5h13",
  inbox: "M4 12h4l2 3h4l2-3h4M4 12 5.5 5h13L20 12m-16 0v6a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-6",
  target: "M12 22a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-4a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm0-3.5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Z",
};

export function Icon({
  name,
  size = 20,
  className,
  strokeWidth = 1.8,
}: {
  name: IconName;
  size?: number;
  className?: string;
  strokeWidth?: number;
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <path d={PATHS[name]} />
    </svg>
  );
}
