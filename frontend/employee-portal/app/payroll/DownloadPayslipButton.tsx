"use client";

import { useActionState } from "react";
import { Icon } from "@/components/icons";
import { resolvePayslipDownload, type DownloadActionState } from "./actions";

const initialState: DownloadActionState = { status: "idle" };

export function DownloadPayslipButton({ payslipId }: { payslipId: string }) {
  const boundAction = resolvePayslipDownload.bind(null, payslipId);
  const [state, formAction, isPending] = useActionState(boundAction, initialState);

  return (
    <form action={formAction} style={{ display: "inline" }}>
      <button type="submit" className="btn btn-secondary" disabled={isPending}>
        <Icon name="download" size={16} />
        {isPending ? "Resolving…" : "Download PDF"}
      </button>
      <div role="status" aria-live="polite" style={{ marginTop: "0.5rem" }}>
        {state.status === "error" && <span className="field-error">{state.message}</span>}
        {state.status === "success" && state.reference && (
          <p className="muted" style={{ fontSize: "0.85rem", maxWidth: "34rem" }}>
            Secure document reference resolved (checksum {state.reference.checksum.slice(0, 12)}…). This
            environment has no object storage provider configured yet, so the file itself cannot be
            streamed to the browser - only the access-controlled reference above.
          </p>
        )}
      </div>
    </form>
  );
}
