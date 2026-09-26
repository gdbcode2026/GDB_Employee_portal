"use client";

import { useActionState } from "react";
import { createGoal, type ActionState } from "./actions";

const initialState: ActionState = { status: "idle" };

export function GoalForm() {
  const [state, formAction, isPending] = useActionState(createGoal, initialState);

  return (
    <form action={formAction} className="inline-form">
      <div className="form-row">
        <div>
          <label htmlFor="title">Title</label>
          <input id="title" name="title" type="text" required maxLength={200} />
        </div>
        <div>
          <label htmlFor="target">Target</label>
          <input id="target" name="target" type="text" maxLength={500} />
        </div>
      </div>
      <div>
        <label htmlFor="description">Description</label>
        <textarea id="description" name="description" maxLength={2000} />
      </div>
      <button type="submit" className="btn btn-primary" disabled={isPending}>
        {isPending ? "Creating…" : "Add goal"}
      </button>
      <p role="status" aria-live="polite">
        {state.status === "success" && <span className="field-success">{state.message}</span>}
        {state.status === "error" && <span className="field-error">{state.message}</span>}
      </p>
    </form>
  );
}
