import { Icon } from "@/components/icons";

export function PermissionDeniedNotice({ message }: { message?: string }) {
  return (
    <section aria-labelledby="permission-denied-heading" className="notice notice-warning">
      <span className="stat-icon" style={{ marginBottom: "0.6rem", background: "transparent", color: "inherit" }}>
        <Icon name="alertTriangle" size={18} />
      </span>
      <h2 id="permission-denied-heading">You don&apos;t have access to this</h2>
      <p>{message ?? "Your account does not have permission to view this page."}</p>
    </section>
  );
}
