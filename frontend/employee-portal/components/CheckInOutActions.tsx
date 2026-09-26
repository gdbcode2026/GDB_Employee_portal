"use client";

import { useActionState } from "react";
import { checkIn, checkOut, type ActionState } from "@/app/attendance/actions";
import { Icon } from "@/components/icons";

const initialState: ActionState = { status: "idle" };

export function CheckInOutActions({ canCheckOut }: { canCheckOut: boolean }) {
  const [inState, inAction, inPending] = useActionState(checkIn, initialState);
  const [outState, outAction, outPending] = useActionState(checkOut, initialState);

  return (
    <>
      <form action={inAction}>
        <button type="submit" className="quick-action" disabled={inPending}>
          <span className="stat-icon">
            <Icon name="clock" size={18} />
          </span>
          {inPending ? "Checking in…" : "Check In"}
        </button>
      </form>
      <form action={outAction}>
        <button type="submit" className="quick-action" disabled={outPending || !canCheckOut}>
          <span className="stat-icon">
            <Icon name="clock" size={18} />
          </span>
          {outPending ? "Checking out…" : "Check Out"}
        </button>
      </form>
      {(inState.status === "error" || outState.status === "error") && (
        <p role="alert" className="field-error" style={{ gridColumn: "1 / -1" }}>
          {inState.status === "error" ? inState.message : outState.message}
        </p>
      )}
    </>
  );
}
