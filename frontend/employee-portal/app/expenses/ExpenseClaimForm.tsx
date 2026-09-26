"use client";

import { useActionState, useState } from "react";
import { createExpenseClaim, type ActionState } from "./actions";
import { Icon } from "@/components/icons";

const initialState: ActionState = { status: "idle" };

export function ExpenseClaimForm() {
  const [state, formAction, isPending] = useActionState(createExpenseClaim, initialState);
  const [rowCount, setRowCount] = useState(1);

  return (
    <form action={formAction} className="inline-form" id="new">
      <input type="hidden" name="rowCount" value={rowCount} />
      <div className="form-row">
        <div>
          <label htmlFor="currency">Currency</label>
          <input id="currency" name="currency" type="text" defaultValue="INR" maxLength={8} required />
        </div>
      </div>

      {Array.from({ length: rowCount }).map((_, index) => (
        <div className="form-row" key={index} style={{ borderTop: index > 0 ? "1px solid var(--color-border)" : undefined, paddingTop: index > 0 ? "0.6rem" : undefined }}>
          <div>
            <label htmlFor={`line-date-${index}`}>Date</label>
            <input id={`line-date-${index}`} name={`lines[${index}].date`} type="date" />
          </div>
          <div>
            <label htmlFor={`line-category-${index}`}>Category</label>
            <input id={`line-category-${index}`} name={`lines[${index}].category`} type="text" placeholder="e.g. Travel" maxLength={120} />
          </div>
          <div>
            <label htmlFor={`line-amount-${index}`}>Amount</label>
            <input id={`line-amount-${index}`} name={`lines[${index}].amount`} type="number" step="0.01" min="0.01" />
          </div>
          <div>
            <label htmlFor={`line-description-${index}`}>Description</label>
            <input id={`line-description-${index}`} name={`lines[${index}].description`} type="text" maxLength={2000} />
          </div>
        </div>
      ))}

      <button type="button" className="btn btn-secondary btn-sm" onClick={() => setRowCount((count) => count + 1)}>
        <Icon name="plus" size={14} /> Add another line
      </button>

      <button type="submit" className="btn btn-primary" disabled={isPending}>
        {isPending ? "Creating…" : "Create draft claim"}
      </button>
      <p role="status" aria-live="polite">
        {state.status === "success" && <span className="field-success">{state.message}</span>}
        {state.status === "error" && <span className="field-error">{state.message}</span>}
      </p>
    </form>
  );
}
