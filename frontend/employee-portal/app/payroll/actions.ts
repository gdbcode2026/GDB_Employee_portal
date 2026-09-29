"use server";

import { apiClient, ApiError } from "@/lib/api/client";
import type { PayslipDownloadResponse } from "@/lib/api/types";

export interface DownloadActionState {
  status: "idle" | "success" | "error";
  message?: string;
  reference?: PayslipDownloadResponse;
}

/**
 * Resolves the secure download reference via the same access-controlled Document Service flow
 * the backend uses (ownership checked server-side on every call, never a public URL). Document
 * Service currently tracks only an opaque object reference for stored files - no object storage
 * provider is wired up anywhere in this platform yet - so this can only return that reference,
 * not the file's bytes.
 */
export async function resolvePayslipDownload(id: string, _previous: DownloadActionState): Promise<DownloadActionState> {
  try {
    const reference = await apiClient.get<PayslipDownloadResponse>(`/api/v1/payroll/payslips/${id}/download`);
    return { status: "success", reference };
  } catch (error) {
    if (error instanceof ApiError) {
      return { status: "error", message: error.detail ?? error.title ?? "The download reference could not be resolved." };
    }
    return { status: "error", message: "Something went wrong. Please try again." };
  }
}
