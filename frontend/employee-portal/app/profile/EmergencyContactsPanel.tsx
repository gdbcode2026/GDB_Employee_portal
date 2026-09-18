"use client";

import { useActionState } from "react";
import { addEmergencyContact, removeEmergencyContact, type ActionState } from "./actions";
import type { EmergencyContact } from "@/lib/api/types";

const initialState: ActionState = { status: "idle" };

function RemoveContactForm({ contactId, contactName }: { contactId: string; contactName: string }) {
  const [state, formAction, isPending] = useActionState(removeEmergencyContact, initialState);

  return (
    <form action={formAction}>
      <input type="hidden" name="contactId" value={contactId} />
      <button type="submit" disabled={isPending} aria-label={`Remove emergency contact ${contactName}`}>
        {isPending ? "Removing…" : "Remove"}
      </button>
      {state.status === "error" && (
        <p role="alert" className="field-error">
          {state.message}
        </p>
      )}
    </form>
  );
}

function AddContactForm() {
  const [state, formAction, isPending] = useActionState(addEmergencyContact, initialState);

  return (
    <form action={formAction} className="add-contact-form">
      <h4>Add emergency contact</h4>
      <label htmlFor="contact-name">Name</label>
      <input id="contact-name" name="name" type="text" required />
      <label htmlFor="contact-phone">Phone</label>
      <input id="contact-phone" name="phone" type="tel" required />
      <label htmlFor="contact-relationship">Relationship</label>
      <input id="contact-relationship" name="relationship" type="text" required />
      <button type="submit" disabled={isPending}>
        {isPending ? "Adding…" : "Add contact"}
      </button>
      <p role="status" aria-live="polite">
        {state.status === "success" && state.message}
        {state.status === "error" && <span className="field-error">{state.message}</span>}
      </p>
    </form>
  );
}

export function EmergencyContactsPanel({ contacts }: { contacts: EmergencyContact[] }) {
  return (
    <div>
      {contacts.length === 0 ? (
        <p className="muted">No emergency contacts on file.</p>
      ) : (
        <ul className="contact-list">
          {contacts.map((contact) => (
            <li key={contact.id}>
              <span>
                {contact.name} ({contact.relationship}) &ndash; {contact.phone}
              </span>
              <RemoveContactForm contactId={contact.id} contactName={contact.name} />
            </li>
          ))}
        </ul>
      )}
      <AddContactForm />
    </div>
  );
}
