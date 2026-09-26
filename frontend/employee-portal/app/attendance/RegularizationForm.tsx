"use client";

import { useActionState } from "react";
import { submitRegularization, type ActionState } from "./actions";

const initialState: ActionState = { status: "idle" };

export function RegularizationForm() {
  const [state, formAction, isPending] = useActionState(submitRegularization, initialState);

  return (
    <form action={formAction} className="inline-form" id="regularize">
      <div className="form-row">
        <div>
          <label htmlFor="workDate">Work date</label>
          <input id="workDate" name="workDate" type="date" required />
        </div>
        <div>
          <label htmlFor="requestedCheckInAt">Correct check-in time</label>
          <input id="requestedCheckInAt" name="requestedCheckInAt" type="datetime-local" />
        </div>
        <div>
          <label htmlFor="requestedCheckOutAt">Correct check-out time</label>
          <input id="requestedCheckOutAt" name="requestedCheckOutAt" type="datetime-local" />
        </div>
      </div>
      <div>
        <label htmlFor="reason">Reason</label>
        <textarea id="reason" name="reason" required maxLength={500} />
      </div>
      <button type="submit" className="btn btn-primary" disabled={isPending}>
        {isPending ? "Submitting…" : "Submit request"}
      </button>
      <p role="status" aria-live="polite">
        {state.status === "success" && <span className="field-success">{state.message}</span>}
        {state.status === "error" && <span className="field-error">{state.message}</span>}
      </p>
    </form>
  );
}
