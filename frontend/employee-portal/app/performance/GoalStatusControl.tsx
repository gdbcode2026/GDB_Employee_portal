"use client";

import { useActionState } from "react";
import { updateGoalStatus, type ActionState } from "./actions";
import type { GoalStatus } from "@/lib/api/types";

const initialState: ActionState = { status: "idle" };
const OPTIONS: GoalStatus[] = ["OPEN", "IN_PROGRESS", "COMPLETED", "CANCELLED"];

export function GoalStatusControl({ id, status }: { id: string; status: GoalStatus }) {
  const [state, formAction, isPending] = useActionState(updateGoalStatus, initialState);

  return (
    <form action={formAction} style={{ margin: 0 }}>
      <input type="hidden" name="id" value={id} />
      <select
        name="status"
        defaultValue={status}
        disabled={isPending}
        onChange={(event) => event.currentTarget.form?.requestSubmit()}
        aria-label="Update goal status"
        style={{ maxWidth: "10rem" }}
      >
        {OPTIONS.map((option) => (
          <option key={option} value={option}>
            {option.replace("_", " ")}
          </option>
        ))}
      </select>
      {state.status === "error" && (
        <span className="field-error" style={{ marginLeft: "0.5rem" }}>
          {state.message}
        </span>
      )}
    </form>
  );
}
