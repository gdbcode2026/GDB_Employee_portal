export function formatDate(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("en-IN", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
}

export function formatTime(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" });
}

export function formatCurrency(amount: number, currency: string): string {
  try {
    return new Intl.NumberFormat("en-IN", { style: "currency", currency, maximumFractionDigits: 2 }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(2)}`;
  }
}

const MONTH_NAMES = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];

export function formatPeriodLabel(year: number, month: number): string {
  return `${MONTH_NAMES[month - 1] ?? month} ${year}`;
}

export function titleCase(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

export function badgeToneForStatus(status: string): "success" | "warning" | "danger" | "info" | "neutral" {
  const positive = new Set(["APPROVED", "FINALIZED", "DONE", "COMPLETED", "PUBLISHED", "AVAILABLE", "ACTIVE", "REIMBURSED", "DECIDED"]);
  const negative = new Set(["REJECTED", "CANCELLED", "RETIRED", "INACTIVE"]);
  const warning = new Set(["PENDING", "SUBMITTED", "DRAFT", "ASSIGNED", "IN_PROGRESS", "TODO"]);
  if (positive.has(status)) return "success";
  if (negative.has(status)) return "danger";
  if (warning.has(status)) return "warning";
  return "neutral";
}
