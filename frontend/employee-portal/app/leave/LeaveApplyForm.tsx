"use client";

import { useActionState } from "react";
import { applyForLeave, type ActionState } from "./actions";
import type { LeaveType } from "@/lib/api/types";

const initialState: ActionState = { status: "idle" };

export function LeaveApplyForm({ leaveTypes }: { leaveTypes: LeaveType[] }) {
  const [state, formAction, isPending] = useActionState(applyForLeave, initialState);

  return (
    <form action={formAction} className="inline-form" id="apply">
      <div className="form-row">
        <div>
          <label htmlFor="leaveTypeId">Leave type</label>
          <select id="leaveTypeId" name="leaveTypeId" required defaultValue="">
            <option value="" disabled>
              Select a leave type
            </option>
            {leaveTypes.map((type) => (
              <option key={type.id} value={type.id}>
                {type.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="startDate">Start date</label>
          <input id="startDate" name="startDate" type="date" required />
        </div>
        <div>
          <label htmlFor="endDate">End date</label>
          <input id="endDate" name="endDate" type="date" required />
        </div>
      </div>
      <div>
        <label htmlFor="reason">Reason (optional)</label>
        <textarea id="reason" name="reason" maxLength={500} />
      </div>
      <button type="submit" className="btn btn-primary" disabled={isPending}>
        {isPending ? "Submitting…" : "Apply for leave"}
      </button>
      <p role="status" aria-live="polite">
        {state.status === "success" && <span className="field-success">{state.message}</span>}
        {state.status === "error" && <span className="field-error">{state.message}</span>}
      </p>
    </form>
  );
}
