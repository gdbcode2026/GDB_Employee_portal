import { Icon, type IconName } from "@/components/icons";

export function StatCard({
  label,
  value,
  hint,
  icon,
}: {
  label: string;
  value: string;
  hint?: string;
  icon: IconName;
}) {
  return (
    <div className="stat-card">
      <span className="stat-label">
        <span className="stat-icon">
          <Icon name={icon} size={17} />
        </span>
        {label}
      </span>
      <span className="stat-value">{value}</span>
      {hint && <span className="stat-hint">{hint}</span>}
    </div>
  );
}
