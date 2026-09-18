export function NotFoundNotice({ message }: { message: string }) {
  return (
    <section aria-labelledby="not-found-heading" className="notice">
      <h2 id="not-found-heading">Not found</h2>
      <p>{message}</p>
    </section>
  );
}
