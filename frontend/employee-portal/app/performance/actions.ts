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

export async function createGoal(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const title = normalize(formData.get("title"));
  const description = normalize(formData.get("description"));
  const target = normalize(formData.get("target"));

  if (!title) {
    return { status: "error", message: "A title is required." };
  }

  try {
    await apiClient.post("/api/v1/performance/goals", {
      title,
      description: description || undefined,
      target: target || undefined,
    });
  } catch (error) {
    if (error instanceof ApiError) {
      return { status: "error", message: error.detail ?? error.title ?? "The goal could not be created." };
    }
    return { status: "error", message: "Something went wrong. Please try again." };
  }
  revalidatePath("/performance");
  return { status: "success", message: "Goal created." };
}

export async function updateGoalStatus(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const id = normalize(formData.get("id"));
  const status = normalize(formData.get("status"));

  try {
    await apiClient.patch(`/api/v1/performance/goals/${id}`, { status });
  } catch (error) {
    if (error instanceof ApiError) {
      return { status: "error", message: error.detail ?? error.title ?? "The goal could not be updated." };
    }
    return { status: "error", message: "Something went wrong. Please try again." };
  }
  revalidatePath("/performance");
  return { status: "success", message: "Goal updated." };
}
