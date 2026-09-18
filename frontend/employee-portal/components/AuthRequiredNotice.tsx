export function AuthRequiredNotice() {
  return (
    <section aria-labelledby="auth-required-heading" className="notice notice-info">
      <h2 id="auth-required-heading">Sign-in required</h2>
      <p>
        This page needs a signed-in session. Authentication will be available once GDB selects
        an identity provider; no login is implemented in this foundation.
      </p>
    </section>
  );
}
