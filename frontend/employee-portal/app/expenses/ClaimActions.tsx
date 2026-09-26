"use client";

import { useActionState } from "react";
import { submitExpenseClaim, cancelExpenseClaim, type ActionState } from "./actions";

const initialState: ActionState = { status: "idle" };

export function ClaimActions({ id, status }: { id: string; status: string }) {
  const [submitState, submitAction, submitPending] = useActionState(submitExpenseClaim, initialState);
  const [cancelState, cancelAction, cancelPending] = useActionState(cancelExpenseClaim, initialState);

  if (status !== "DRAFT") {
    return null;
  }

  return (
    <div style={{ display: "flex", gap: "0.4rem" }}>
      <form action={submitAction}>
        <input type="hidden" name="id" value={id} />
        <button type="submit" className="btn btn-primary btn-sm" disabled={submitPending}>
          {submitPending ? "Submitting…" : "Submit"}
        </button>
      </form>
      <form action={cancelAction}>
        <input type="hidden" name="id" value={id} />
        <button type="submit" className="btn btn-ghost btn-sm" disabled={cancelPending}>
          {cancelPending ? "Cancelling…" : "Cancel"}
        </button>
      </form>
      {(submitState.status === "error" || cancelState.status === "error") && (
        <p role="alert" className="field-error">
          {submitState.status === "error" ? submitState.message : cancelState.message}
        </p>
      )}
    </div>
  );
}
