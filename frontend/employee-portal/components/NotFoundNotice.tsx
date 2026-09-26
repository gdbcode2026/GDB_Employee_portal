import { Icon } from "@/components/icons";

export function NotFoundNotice({ message }: { message: string }) {
  return (
    <section aria-labelledby="not-found-heading" className="notice notice-neutral">
      <span className="stat-icon" style={{ marginBottom: "0.6rem" }}>
        <Icon name="info" size={18} />
      </span>
      <h2 id="not-found-heading">Not found</h2>
      <p>{message}</p>
    </section>
  );
}
