"use client";

import { Icon } from "@/components/icons";

export function PrintButton() {
  return (
    <button type="button" className="btn btn-secondary" onClick={() => window.print()}>
      <Icon name="print" size={16} />
      Print
    </button>
  );
}
