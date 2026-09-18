export function EmptyState({ message }: { message: string }) {
  return (
    <p className="empty-state" role="status">
      {message}
    </p>
  );
}
