export function PermissionDeniedNotice({ message }: { message?: string }) {
  return (
    <section aria-labelledby="permission-denied-heading" className="notice notice-warning">
      <h2 id="permission-denied-heading">You don&apos;t have access to this</h2>
      <p>{message ?? "Your account does not have permission to view this page."}</p>
    </section>
  );
}
