"use client";

export default function Error({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <section aria-labelledby="error-heading" role="alert">
      <h2 id="error-heading">Something went wrong</h2>
      <p>We couldn&apos;t load this page. Please try again.</p>
      <button type="button" onClick={() => reset()}>
        Try again
      </button>
    </section>
  );
}
