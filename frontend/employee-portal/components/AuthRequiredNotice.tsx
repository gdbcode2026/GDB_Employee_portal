import { Icon } from "@/components/icons";

export function AuthRequiredNotice() {
  return (
    <section aria-labelledby="auth-required-heading" className="notice">
      <span className="stat-icon" style={{ marginBottom: "0.6rem" }}>
        <Icon name="signIn" size={18} />
      </span>
      <h2 id="auth-required-heading">Sign-in required</h2>
      <p>Your session has ended or is no longer valid. Sign in again to continue.</p>
      <a className="btn btn-primary" href="/employees/api/auth/login">
        Sign in
      </a>
    </section>
  );
}
