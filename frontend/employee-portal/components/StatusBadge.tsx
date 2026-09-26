import { badgeToneForStatus, titleCase } from "@/lib/format";

export function StatusBadge({ status }: { status: string }) {
  const tone = badgeToneForStatus(status);
  return <span className={`badge badge-${tone}`}>{titleCase(status)}</span>;
}
