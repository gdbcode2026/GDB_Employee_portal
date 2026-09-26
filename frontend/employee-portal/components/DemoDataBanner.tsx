import { Icon } from "@/components/icons";

export function DemoDataBanner({ message }: { message?: string }) {
  return (
    <div className="demo-banner" role="note">
      <Icon name="alertTriangle" size={17} />
      <span>{message ?? "DEMO DATA — for layout preview only. Figures shown are not real payroll values."}</span>
    </div>
  );
}
