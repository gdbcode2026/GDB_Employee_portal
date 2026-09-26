import { Icon, type IconName } from "@/components/icons";

export function EmptyState({ message, icon = "inbox" }: { message: string; icon?: IconName }) {
  return (
    <div className="empty-panel" role="status">
      <span className="stat-icon">
        <Icon name={icon} size={20} />
      </span>
      <h3>Nothing here yet</h3>
      <p className="muted">{message}</p>
    </div>
  );
}
