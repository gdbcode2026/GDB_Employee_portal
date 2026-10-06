"use client";

import { useActionState } from "react";
import { markAllNotificationsRead, type ActionState } from "./actions";

const initialState: ActionState = { status: "idle" };

export function MarkAllReadButton() {
  const [state, formAction, isPending] = useActionState(markAllNotificationsRead, initialState);

  return (
    <form action={formAction}>
      <button type="submit" className="btn btn-secondary btn-sm" disabled={isPending}>
        {isPending ? "Marking all…" : "Mark all as read"}
      </button>
      {state.status === "error" && (
        <p role="alert" className="field-error">
          {state.message}
        </p>
      )}
    </form>
  );
}
