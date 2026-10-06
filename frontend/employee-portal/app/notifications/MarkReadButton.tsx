"use client";

import { useActionState } from "react";
import { markNotificationRead, type ActionState } from "./actions";

const initialState: ActionState = { status: "idle" };

export function MarkReadButton({ id }: { id: string }) {
  const [state, formAction, isPending] = useActionState(markNotificationRead, initialState);

  return (
    <form action={formAction}>
      <input type="hidden" name="id" value={id} />
      <button type="submit" className="btn btn-ghost btn-sm" disabled={isPending} aria-label="Mark notification as read">
        {isPending ? "Marking…" : "Mark as read"}
      </button>
      {state.status === "error" && (
        <p role="alert" className="field-error">
          {state.message}
        </p>
      )}
    </form>
  );
}
