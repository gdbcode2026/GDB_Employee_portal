import { apiClient, ApiError } from "@/lib/api/client";
import type { PayslipDetail } from "@/lib/api/types";
import { AuthRequiredNotice } from "@/components/AuthRequiredNotice";
import { NotFoundNotice } from "@/components/NotFoundNotice";
import { PageHeader } from "@/components/PageHeader";
import { PayslipDocument } from "@/components/PayslipDocument";
import { formatPeriodLabel } from "@/lib/format";
import { DownloadPayslipButton } from "../DownloadPayslipButton";

export default async function PayslipDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  let payslip: PayslipDetail;
  try {
    payslip = await apiClient.get<PayslipDetail>(`/api/v1/payroll/payslips/${id}`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return (
        <section aria-labelledby="payslip-heading">
          <PageHeader title="Payslip" />
          <AuthRequiredNotice />
        </section>
      );
    }
    if (error instanceof ApiError && (error.status === 404 || error.status === 403)) {
      return (
        <section aria-labelledby="payslip-heading">
          <PageHeader title="Payslip" />
          <NotFoundNotice message="This payslip was not found or is not visible to you." />
        </section>
      );
    }
    throw error;
  }

  return (
    <section aria-labelledby="payslip-heading">
      <PageHeader
        title={`Payslip - ${formatPeriodLabel(payslip.periodYear, payslip.periodMonth)}`}
        description="Your payslip, generated from the finalized payroll run for this period."
      />
      <div className="card">
        <PayslipDocument payslip={payslip} actions={<DownloadPayslipButton payslipId={payslip.id} />} />
      </div>
    </section>
  );
}
