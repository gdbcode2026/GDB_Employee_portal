"use server";

import { revalidatePath } from "next/cache";
import { apiClient, ApiError } from "@/lib/api/client";
import type { EmergencyContactInput, EmployeeResponse } from "@/lib/api/types";

export interface ActionState {
  status: "idle" | "success" | "error";
  message?: string;
}

function normalize(value: FormDataEntryValue | null): string {
  return typeof value === "string" ? value.trim() : "";
}

async function fetchCurrentContacts(): Promise<EmergencyContactInput[]> {
  const employee = await apiClient.get<EmployeeResponse>("/api/v1/employees/me");
  return employee.emergencyContacts.map(({ name, phone, relationship }) => ({ name, phone, relationship }));
}

async function submitUpdate(body: { phone?: string | null; emergencyContacts?: EmergencyContactInput[] }, successMessage: string): Promise<ActionState> {
  try {
    await apiClient.patch("/api/v1/employees/me", body);
  } catch (error) {
    if (error instanceof ApiError) {
      return { status: "error", message: error.detail ?? error.title ?? "The update could not be completed." };
    }
    return { status: "error", message: "Something went wrong. Please try again." };
  }
  revalidatePath("/profile");
  return { status: "success", message: successMessage };
}

export async function updatePhone(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const phone = normalize(formData.get("phone"));
  return submitUpdate({ phone: phone || null }, "Phone number updated.");
}

export async function addEmergencyContact(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const name = normalize(formData.get("name"));
  const phone = normalize(formData.get("phone"));
  const relationship = normalize(formData.get("relationship"));

  if (!name || !phone || !relationship) {
    return { status: "error", message: "Name, phone, and relationship are all required." };
  }

  const current = await fetchCurrentContacts();
  return submitUpdate({ emergencyContacts: [...current, { name, phone, relationship }] }, "Emergency contact added.");
}

export async function removeEmergencyContact(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const contactId = normalize(formData.get("contactId"));
  const employee = await apiClient.get<EmployeeResponse>("/api/v1/employees/me");
  const updated = employee.emergencyContacts
    .filter((contact) => contact.id !== contactId)
    .map(({ name, phone, relationship }) => ({ name, phone, relationship }));

  return submitUpdate({ emergencyContacts: updated }, "Emergency contact removed.");
}
