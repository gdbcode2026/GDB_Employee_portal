"use server";

import { apiClient, ApiError } from "@/lib/api/client";

export interface ActionState {
  status: "idle" | "success" | "error";
  message?: string;
}

function normalize(value: FormDataEntryValue | null): string {
  return typeof value === "string" ? value.trim() : "";
}

export async function requestAsset(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const type = normalize(formData.get("type"));
  const justification = normalize(formData.get("justification"));

  if (!type) {
    return { status: "error", message: "Asset type is required." };
  }

  try {
    await apiClient.post("/api/v1/asset-requests", { type, justification: justification || undefined });
  } catch (error) {
    if (error instanceof ApiError) {
      return { status: "error", message: error.detail ?? error.title ?? "The request could not be submitted." };
    }
    return { status: "error", message: "Something went wrong. Please try again." };
  }
  return { status: "success", message: "Asset request submitted to IT/Admin." };
}
