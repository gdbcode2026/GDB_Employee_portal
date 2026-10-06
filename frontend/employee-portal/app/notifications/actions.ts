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

export async function markNotificationRead(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const id = normalize(formData.get("id"));
  try {
    await apiClient.post(`/api/v1/notifications/${id}/read`, {});
  } catch (error) {
    if (error instanceof ApiError) {
      return { status: "error", message: error.detail ?? error.title ?? "The notification could not be marked as read." };
    }
    return { status: "error", message: "Something went wrong. Please try again." };
  }
  revalidatePath("/notifications");
  return { status: "success" };
}

export async function markAllNotificationsRead(_previous: ActionState, _formData: FormData): Promise<ActionState> {
  try {
    await apiClient.post("/api/v1/notifications/read-all", {});
  } catch (error) {
    if (error instanceof ApiError) {
      return { status: "error", message: error.detail ?? error.title ?? "Notifications could not be marked as read." };
    }
    return { status: "error", message: "Something went wrong. Please try again." };
  }
  revalidatePath("/notifications");
  return { status: "success" };
}
