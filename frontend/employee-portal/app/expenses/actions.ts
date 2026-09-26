"use server";

import { revalidatePath } from "next/cache";
import { apiClient, ApiError } from "@/lib/api/client";
import type { ExpenseLineItem } from "@/lib/api/types";

export interface ActionState {
  status: "idle" | "success" | "error";
  message?: string;
}

function normalize(value: FormDataEntryValue | null): string {
  return typeof value === "string" ? value.trim() : "";
}

export async function createExpenseClaim(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const currency = normalize(formData.get("currency")) || "INR";
  const rowCount = Number.parseInt(normalize(formData.get("rowCount")) || "1", 10);

  const lines: ExpenseLineItem[] = [];
  for (let i = 0; i < rowCount; i += 1) {
    const date = normalize(formData.get(`lines[${i}].date`));
    const category = normalize(formData.get(`lines[${i}].category`));
    const amountRaw = normalize(formData.get(`lines[${i}].amount`));
    const description = normalize(formData.get(`lines[${i}].description`));
    if (!date && !category && !amountRaw) continue;
    const amount = Number.parseFloat(amountRaw);
    if (!date || !category || !Number.isFinite(amount) || amount <= 0) {
      return { status: "error", message: `Line ${i + 1} needs a date, category, and a positive amount.` };
    }
    lines.push({ date, category, amount, description: description || null });
  }

  if (lines.length === 0) {
    return { status: "error", message: "Add at least one expense line." };
  }

  try {
    await apiClient.post("/api/v1/expenses/claims", { currency, lines });
  } catch (error) {
    if (error instanceof ApiError) {
      return { status: "error", message: error.detail ?? error.title ?? "The claim could not be created." };
    }
    return { status: "error", message: "Something went wrong. Please try again." };
  }
  revalidatePath("/expenses");
  return { status: "success", message: "Expense claim created as a draft." };
}

export async function submitExpenseClaim(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const id = normalize(formData.get("id"));
  try {
    await apiClient.post(`/api/v1/expenses/claims/${id}/submit`, {});
  } catch (error) {
    if (error instanceof ApiError) {
      return { status: "error", message: error.detail ?? error.title ?? "The claim could not be submitted." };
    }
    return { status: "error", message: "Something went wrong. Please try again." };
  }
  revalidatePath("/expenses");
  return { status: "success", message: "Expense claim submitted." };
}

export async function cancelExpenseClaim(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const id = normalize(formData.get("id"));
  try {
    await apiClient.patch(`/api/v1/expenses/claims/${id}`, { status: "CANCELLED" });
  } catch (error) {
    if (error instanceof ApiError) {
      return { status: "error", message: error.detail ?? error.title ?? "The claim could not be cancelled." };
    }
    return { status: "error", message: "Something went wrong. Please try again." };
  }
  revalidatePath("/expenses");
  return { status: "success", message: "Expense claim cancelled." };
}
