"use server";

import { revalidatePath } from "next/cache";
import { apiClient, ApiError } from "@/lib/api/client";

export interface ActionState {
  status: "idle" | "success" | "error";
  message?: string;
}

function normalize(value: FormDataEntryValue | null): string {
  return typeof value === "string" ? value.trim() : "";
}

export async function applyForLeave(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const leaveTypeId = normalize(formData.get("leaveTypeId"));
  const startDate = normalize(formData.get("startDate"));
  const endDate = normalize(formData.get("endDate"));
  const reason = normalize(formData.get("reason"));

  if (!leaveTypeId || !startDate || !endDate) {
    return { status: "error", message: "Leave type, start date, and end date are required." };
  }

  try {
    await apiClient.post("/api/v1/leave/requests", {
      leaveTypeId,
      startDate,
      endDate,
      reason: reason || undefined,
    });
  } catch (error) {
    if (error instanceof ApiError) {
      return { status: "error", message: error.detail ?? error.title ?? "The leave request could not be submitted." };
    }
    return { status: "error", message: "Something went wrong. Please try again." };
  }
  revalidatePath("/leave");
  return { status: "success", message: "Leave request submitted." };
}

export async function cancelLeaveRequest(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const id = normalize(formData.get("id"));
  try {
    await apiClient.post(`/api/v1/leave/requests/${id}/cancel`, {});
  } catch (error) {
    if (error instanceof ApiError) {
      return { status: "error", message: error.detail ?? error.title ?? "The request could not be cancelled." };
    }
    return { status: "error", message: "Something went wrong. Please try again." };
  }
  revalidatePath("/leave");
  return { status: "success", message: "Leave request cancelled." };
}
