"use client";

import { useActionState } from "react";
import { updatePhone, type ActionState } from "./actions";

const initialState: ActionState = { status: "idle" };

export function PhoneForm({ currentPhone }: { currentPhone: string | null }) {
  const [state, formAction, isPending] = useActionState(updatePhone, initialState);

  return (
    <form action={formAction}>
      <label htmlFor="phone">Phone number</label>
      <input id="phone" name="phone" type="tel" defaultValue={currentPhone ?? ""} autoComplete="tel" />
      <button type="submit" disabled={isPending}>
        {isPending ? "Saving…" : "Save phone"}
      </button>
      <p role="status" aria-live="polite">
        {state.status === "success" && state.message}
        {state.status === "error" && <span className="field-error">{state.message}</span>}
      </p>
    </form>
  );
}
