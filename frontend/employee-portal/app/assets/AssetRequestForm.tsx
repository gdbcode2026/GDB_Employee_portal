"use client";

import { useActionState } from "react";
import { requestAsset, type ActionState } from "./actions";

const initialState: ActionState = { status: "idle" };

export function AssetRequestForm() {
  const [state, formAction, isPending] = useActionState(requestAsset, initialState);

  return (
    <form action={formAction} className="inline-form">
      <div>
        <label htmlFor="type">Asset type</label>
        <input id="type" name="type" type="text" required maxLength={120} placeholder="e.g. Laptop, Monitor" />
      </div>
      <div>
        <label htmlFor="justification">Justification</label>
        <textarea id="justification" name="justification" maxLength={2000} />
      </div>
      <button type="submit" className="btn btn-primary" disabled={isPending}>
        {isPending ? "Submitting…" : "Request asset"}
      </button>
      <p role="status" aria-live="polite">
        {state.status === "success" && <span className="field-success">{state.message}</span>}
        {state.status === "error" && <span className="field-error">{state.message}</span>}
      </p>
    </form>
  );
}
