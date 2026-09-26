"use client";

import { useActionState } from "react";
import { cancelLeaveRequest, type ActionState } from "./actions";

const initialState: ActionState = { status: "idle" };

export function CancelLeaveButton({ id }: { id: string }) {
  const [state, formAction, isPending] = useActionState(cancelLeaveRequest, initialState);

  return (
    <form action={formAction}>
      <input type="hidden" name="id" value={id} />
      <button type="submit" className="btn btn-ghost btn-sm" disabled={isPending}>
        {isPending ? "Cancelling…" : "Cancel"}
      </button>
      {state.status === "error" && (
        <p role="alert" className="field-error">
          {state.message}
        </p>
      )}
    </form>
  );
}
