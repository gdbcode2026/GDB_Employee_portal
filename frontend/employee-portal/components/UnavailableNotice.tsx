import { Icon } from "@/components/icons";

export function UnavailableNotice({ title, message }: { title: string; message: string }) {
  return (
    <section aria-labelledby="unavailable-heading" className="notice notice-neutral">
      <span className="stat-icon" style={{ marginBottom: "0.6rem" }}>
        <Icon name="info" size={18} />
      </span>
      <h2 id="unavailable-heading">{title}</h2>
      <p>{message}</p>
    </section>
  );
}
