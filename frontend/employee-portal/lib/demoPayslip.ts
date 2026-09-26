// DEMO DATA ONLY. The Payroll Service has no backend implementation yet (see
// docs/PAYROLL_REQUIREMENTS.md). This fixture exists purely so the payslip layout can be built
// and reviewed ahead of that implementation. Every figure below is a fictional placeholder and
// must never be presented as, or mistaken for, a real employee's compensation.

export interface DemoPayslip {
  financialYear: string;
  payPeriod: string;
  paymentDate: string;
  employeeName: string;
  employeeNumber: string;
  department: string;
  designation: string;
  panMasked: string;
  bankAccountMasked: string;
  earnings: { label: string; amount: number }[];
  deductions: { label: string; amount: number }[];
  employerContributions: { label: string; amount: number }[];
  ytdGross: number;
  ytdTax: number;
  amountInWords: string;
}

export const DEMO_PAYSLIP: DemoPayslip = {
  financialYear: "FY 2025-26",
  payPeriod: "March 2026",
  paymentDate: "2026-04-01",
  employeeName: "Demo Employee",
  employeeNumber: "GDB-DEMO-0001",
  department: "Engineering",
  designation: "Software Engineer",
  panMasked: "XXXXX0000X",
  bankAccountMasked: "XXXXXXXX0000",
  earnings: [
    { label: "Basic", amount: 40000 },
    { label: "House Rent Allowance", amount: 16000 },
    { label: "Special Allowance", amount: 14000 },
  ],
  deductions: [
    { label: "Provident Fund", amount: 4800 },
    { label: "Professional Tax", amount: 200 },
    { label: "Income Tax (TDS)", amount: 5000 },
  ],
  employerContributions: [{ label: "Employer PF Contribution", amount: 4800 }],
  ytdGross: 700000,
  ytdTax: 45000,
  amountInWords: "Rupees Sixty Thousand Only",
};

export const DEMO_PAYSLIP_HISTORY = [
  { period: "February 2026", netPay: 60000, status: "PAID" as const },
  { period: "January 2026", netPay: 60000, status: "PAID" as const },
  { period: "December 2025", netPay: 58500, status: "PAID" as const },
];
