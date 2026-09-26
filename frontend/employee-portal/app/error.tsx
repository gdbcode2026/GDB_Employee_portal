"use client";

import { Icon } from "@/components/icons";

export default function Error({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <section aria-labelledby="error-heading" role="alert" className="notice notice-danger">
      <span className="stat-icon" style={{ marginBottom: "0.6rem", background: "transparent", color: "inherit" }}>
        <Icon name="alertTriangle" size={18} />
      </span>
      <h2 id="error-heading">Something went wrong</h2>
      <p>We couldn&apos;t load this page. Please try again.</p>
      <button type="button" className="btn btn-secondary" onClick={() => reset()}>
        Try again
      </button>
    </section>
  );
}
