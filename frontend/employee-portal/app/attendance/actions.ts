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

async function afterMutation(successMessage: string): Promise<ActionState> {
  revalidatePath("/");
  revalidatePath("/attendance");
  return { status: "success", message: successMessage };
}

export async function checkIn(_previous: ActionState): Promise<ActionState> {
  try {
    await apiClient.post("/api/v1/attendance/check-ins", {});
  } catch (error) {
    if (error instanceof ApiError) {
      return { status: "error", message: error.detail ?? error.title ?? "Check-in could not be recorded." };
    }
    return { status: "error", message: "Something went wrong. Please try again." };
  }
  return afterMutation("Checked in.");
}

export async function checkOut(_previous: ActionState): Promise<ActionState> {
  try {
    await apiClient.post("/api/v1/attendance/check-outs", {});
  } catch (error) {
    if (error instanceof ApiError) {
      return { status: "error", message: error.detail ?? error.title ?? "Check-out could not be recorded." };
    }
    return { status: "error", message: "Something went wrong. Please try again." };
  }
  return afterMutation("Checked out.");
}

export async function submitRegularization(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const workDate = normalize(formData.get("workDate"));
  const reason = normalize(formData.get("reason"));
  const requestedCheckInAt = normalize(formData.get("requestedCheckInAt"));
  const requestedCheckOutAt = normalize(formData.get("requestedCheckOutAt"));

  if (!workDate || !reason) {
    return { status: "error", message: "Work date and reason are required." };
  }

  try {
    await apiClient.post("/api/v1/attendance/regularizations", {
      workDate,
      reason,
      requestedCheckInAt: requestedCheckInAt ? new Date(requestedCheckInAt).toISOString() : undefined,
      requestedCheckOutAt: requestedCheckOutAt ? new Date(requestedCheckOutAt).toISOString() : undefined,
    });
  } catch (error) {
    if (error instanceof ApiError) {
      return { status: "error", message: error.detail ?? error.title ?? "The request could not be submitted." };
    }
    return { status: "error", message: "Something went wrong. Please try again." };
  }
  return afterMutation("Regularization request submitted.");
}
