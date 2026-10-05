# India Payroll V1 — Proposed `statutory_rules` Configuration

This document proposes the exact, versioned records that would be submitted to
`POST /api/v1/payroll/statutory-rules` once GDB Finance/Legal has confirmed every item this
document and `docs/INDIA_PAYROLL_V1_RULE_SOURCES.md` flag as
**PENDING_FINANCE_LEGAL_CONFIRMATION**. **Nothing in this document has been inserted into any
database, and no Flyway seed migration has been written** — these are proposed values for human
review only, exactly as instructed.

Every record below follows the `StatutoryRule`/`StatutoryRuleParameters` shape
(`docs/PAYROLL_REQUIREMENTS.md` Section H, Rule Engine task) and no executable formula or Java
constant appears anywhere — only data. **Updated** following the India Payroll V1 rule-engine
architecture extension: `SLAB_BASED`/`PROGRESSIVE_TAX` and `StatutoryRule.taxRegime` now exist
(Sections 3/4 below reflect this), closing the capability gap this document originally reported
as unresolvable - the *legal-value* sourcing gaps are unchanged. Each cites its source from
`docs/INDIA_PAYROLL_V1_RULE_SOURCES.md` by section number.

## Status legend

- ✅ **PROPOSED — SAFE TO REVIEW**: structurally complete, every value sourced, ready for
  Finance/Legal sign-off before activation.
- ⚠️ **PROPOSED WITH AN OPEN FIELD / OPEN DECISION**: structurally expressible, but not safe to
  activate - either one explicitly-marked value depends on an unresolved source conflict
  (Section 1 of the sources document), or (Sections 3/4, since the architecture extension) the
  shape is now expressible but real values and/or product decisions remain unconfirmed.
- ⛔ **NOT PROPOSED**: no record is given at all, because either the underlying calculation
  cannot be correctly expressed by the rule engine, or proposing illustrative numbers would risk
  being mistaken for a sourced value - proposing a number here would misrepresent the law, not
  merely omit a pending detail.

---

## 1. EPF / PF

### 1.1 `PF_EMPLOYEE_CONTRIBUTION_IN_V1` — ⚠️ PROPOSED WITH AN OPEN FIELD

Wired to the `PF` (`DEDUCTION`) `CompensationComponent`.

```json
{
  "code": "PF_EMPLOYEE_CONTRIBUTION_IN_V1",
  "ruleType": "PF",
  "jurisdiction": null,
  "effectiveFrom": "2026-07-01",
  "effectiveTo": null,
  "calculationType": "THRESHOLD_BASED",
  "parameters": {
    "amount": null,
    "percentage": 12.00,
    "wageBasisComponentCode": "BASIC_SALARY",
    "minWage": null,
    "maxWage": "PENDING_FINANCE_LEGAL_CONFIRMATION — 15000.00 or 25000.00, see Sources §1",
    "cap": null
  }
}
```

- `percentage: 12.00` — Sources §1, employee contribution rate.
- `wageBasisComponentCode: "BASIC_SALARY"` — the only earning component in the current
  Common India Payroll V1 Baseline catalogue that corresponds to "EPF wages"; Sources §1 flags
  that the traditional Basic+DA basis cannot be fully replicated without a DA component, which
  does not exist in this catalogue (PENDING_FINANCE_LEGAL_CONFIRMATION, scope decision not a
  legal fact).
- `maxWage` — **deliberately left unfilled.** Do not activate this record with a guessed number;
  Sources §1 found two conflicting figures (₹15,000 vs ₹25,000) and neither is primary-verified
  in this session.
- `effectiveFrom: "2026-07-01"` — matches the EPF Scheme 2026's own commencement date (Sources
  §1); this is itself only secondary-corroborated and should be re-confirmed before activation.

### 1.2 `PF_EMPLOYER_CONTRIBUTION_IN_V1` — ⚠️ PROPOSED WITH AN OPEN FIELD

Wired to the `EMPLOYER_PF` (`EMPLOYER_CONTRIBUTION`) `CompensationComponent`. Identical shape to
1.1, same open `maxWage` field, same `effectiveFrom`.

```json
{
  "code": "PF_EMPLOYER_CONTRIBUTION_IN_V1",
  "ruleType": "PF",
  "jurisdiction": null,
  "effectiveFrom": "2026-07-01",
  "effectiveTo": null,
  "calculationType": "THRESHOLD_BASED",
  "parameters": {
    "amount": null,
    "percentage": 12.00,
    "wageBasisComponentCode": "BASIC_SALARY",
    "minWage": null,
    "maxWage": "PENDING_FINANCE_LEGAL_CONFIRMATION — same as 1.1",
    "cap": null
  }
}
```

**Known limitation, not resolved by this record**: the real employer contribution is internally
split between EPS (historically 8.33%, itself separately capped) and the EPF/EDLI balance, plus
administrative charges. This codebase has no `EPS`/`EDLI`/admin-charge `PayComponent`, and adding
one is a catalogue change outside this task's scope (which forbids new calculation logic). This
record computes the employer's **gross** 12% contribution only — the internal breakdown for
remittance purposes is not modeled and is flagged PENDING_FINANCE_LEGAL_CONFIRMATION in the
sources document.

## 2. ESI

### 2.1 `ESI_EMPLOYEE_CONTRIBUTION_IN_V1` — ✅ PROPOSED — SAFE TO REVIEW (ceiling needs re-confirmation)

Wired to the `ESI` (`DEDUCTION`) `CompensationComponent`.

```json
{
  "code": "ESI_EMPLOYEE_CONTRIBUTION_IN_V1",
  "ruleType": "ESI",
  "jurisdiction": null,
  "effectiveFrom": "2019-07-01",
  "effectiveTo": null,
  "calculationType": "THRESHOLD_BASED",
  "parameters": {
    "amount": null,
    "percentage": 0.75,
    "wageBasisComponentCode": null,
    "minWage": null,
    "maxWage": 21000.00,
    "cap": null
  }
}
```

- `percentage: 0.75` — Sources §2, **primary-verified** directly from `esic.gov.in/contribution`.
- `wageBasisComponentCode: null` — basis is gross wages (sum of all `EARNING` components), which
  matches ESI's "gross wages" basis more faithfully than a single named component.
- `maxWage: 21000.00` — Sources §2, secondary-corroborated; **re-confirm before activation** given
  the 2026 Labour Code transition noted in the sources document.
- `effectiveFrom: "2019-07-01"` — Sources §2, primary-verified rate-change date.

**Known limitation, not resolved by this record**: the ≤₹176/day average-wage exemption from the
*employee's* share only (employer still pays) cannot be separately expressed — this
`THRESHOLD_BASED` shape has one floor (`minWage`), which would incorrectly zero out *both* sides
if used for this purpose. No `minWage` is set here; the exemption is left unmodeled and flagged in
the sources document.

### 2.2 `ESI_EMPLOYER_CONTRIBUTION_IN_V1` — ✅ PROPOSED — SAFE TO REVIEW (ceiling needs re-confirmation)

Wired to the `EMPLOYER_ESI` (`EMPLOYER_CONTRIBUTION`) `CompensationComponent`. Identical shape to
2.1 except the rate.

```json
{
  "code": "ESI_EMPLOYER_CONTRIBUTION_IN_V1",
  "ruleType": "ESI",
  "jurisdiction": null,
  "effectiveFrom": "2019-07-01",
  "effectiveTo": null,
  "calculationType": "THRESHOLD_BASED",
  "parameters": {
    "amount": null,
    "percentage": 3.25,
    "wageBasisComponentCode": null,
    "minWage": null,
    "maxWage": 21000.00,
    "cap": null
  }
}
```

## 3. Professional Tax — Tamil Nadu (Greater Chennai Corporation)

### ⚠️ Architecturally expressible since the India Payroll V1 rule-engine extension — still not safe to activate (legal values unconfirmed, two product decisions still open).

**Update**: the rule engine now has a `SLAB_BASED` calculation type (`docs/PAYROLL_REQUIREMENTS.md`
Section H) built exactly for "one flat fee per income bracket, non-cumulative" — the shape GCC
Professional Tax needs. The six brackets below are therefore now expressible as a *single*
`StatutoryRule` version's `parameters.brackets`, where before this extension they could not be
loaded as one coherent rule at all. **They are still not loaded, and this document still does not
authorize activating them**, because two issues from the original finding remain open, and the
notification itself remains only secondary-corroborated (Sources §3):

```json
{
  "code": "PT_TN_GCC_V1",
  "ruleType": "PROFESSIONAL_TAX",
  "jurisdiction": "Tamil Nadu - Greater Chennai Corporation",
  "effectiveFrom": "PENDING_FINANCE_LEGAL_CONFIRMATION — see Sources §3 for the notification date",
  "effectiveTo": null,
  "calculationType": "SLAB_BASED",
  "parameters": {
    "brackets": [
      {"order": 1, "lowerBound": 0,     "upperBound": 21000, "fixedAmount": 0,    "percentage": null},
      {"order": 2, "lowerBound": 21000, "upperBound": 30000, "fixedAmount": 180,  "percentage": null},
      {"order": 3, "lowerBound": 30000, "upperBound": 45000, "fixedAmount": 425,  "percentage": null},
      {"order": 4, "lowerBound": 45000, "upperBound": 60000, "fixedAmount": 930,  "percentage": null},
      {"order": 5, "lowerBound": 60000, "upperBound": 75000, "fixedAmount": 1025, "percentage": null},
      {"order": 6, "lowerBound": 75000, "upperBound": null,  "fixedAmount": 1250, "percentage": null}
    ]
  }
}
```

Two decisions remain genuinely open and are **not** resolved by the architecture extension:

1. **Half-yearly-to-monthly apportionment** — the bracket `lowerBound`/`upperBound` values above
   are GCC's own **half-yearly** income bands (Sources §3); this codebase's payroll period is
   locked to **monthly** (decision 3). Loading this record as-is and feeding it the employee's
   *monthly* gross would compare a monthly figure against half-yearly bands — wrong. Either the
   wage basis fed to this rule must be a rolling/projected half-yearly figure, or GDB must decide
   to divide each bracket's fee by 6 and compare against monthly income instead (a reasonable
   approximation, but a *business* decision, not a legal fact) — **PENDING_FINANCE_LEGAL_
   CONFIRMATION**, not resolved by this extension.
2. **Notification currency** — the exact notification number/date and whether it remains current
   is still only secondary-corroborated (Sources §3) — **PENDING_FINANCE_LEGAL_CONFIRMATION**,
   unchanged by this extension.

The architecture extension resolves only "can the engine express a slab table at all" (yes, now)
— it does not resolve either of the above, and this record must not be activated until both are.

## 4. Salary TDS / Income Tax

### ⚠️ The core progressive-bracket calculation is now architecturally expressible — still not safe to activate (every rate/slab/rebate/cess/surcharge value remains unconfirmed, and several real-world TDS concepts remain unmodeled).

**Update**: the rule engine now has a `PROGRESSIVE_TAX` calculation type and `StatutoryRule.
taxRegime` (`docs/PAYROLL_REQUIREMENTS.md` Section H) built exactly for "marginal-rate brackets,
resolved per the employee's elected regime" — the core shape salary TDS needs. The structural
inputs Sources §4 documents (both regimes' slab boundaries) can now be *shaped* into a rule, for
example (illustrative structure only — **every numeric value below is a placeholder, not a
sourced figure, and must not be used**):

```json
{
  "code": "TDS_NEW_REGIME_V1",
  "ruleType": "TDS",
  "taxRegime": "NEW_REGIME",
  "effectiveFrom": "PENDING_FINANCE_LEGAL_CONFIRMATION",
  "calculationType": "PROGRESSIVE_TAX",
  "parameters": {
    "brackets": "PENDING_FINANCE_LEGAL_CONFIRMATION — see Sources §4 for the reported (not primary-verified) slab boundaries/rates"
  }
}
```

This remains **not safe to activate** for reasons the architecture extension does not touch:

1. **No real slab/rate value is confirmed** — Sources §4's new/old regime figures are only
   secondary-corroborated; none are primary-verified. Loading them now would be exactly the
   "guess from the blog consensus" this task forbids.
2. **Rebate/cess/surcharge are not yet layered on top** — `PROGRESSIVE_TAX` computes the base
   marginal-bracket tax only. Section 87A's rebate (a cliff, not a bracket), the Health and
   Education Cess (a percentage of tax+surcharge, not of wages), and the tiered surcharge are
   each a *different* calculation shape layered *after* the bracket sum - "extensible rule
   components" per the architecture task, meaning each could be its own chained `StatutoryRule`
   in principle, but no chaining mechanism from one rule's *computed* result to another exists
   yet (today, `wageBasisComponentCode` reads a sibling component's *configured* `amount`, not
   its calculated result - a documented limitation, not yet built).
3. **Annualization is not modeled** — real TDS withholds monthly against a *projected annual*
   income, re-estimated through the year as the Indian financial year progresses; this rule
   computes against whatever wage basis is supplied for one period, with no annual-projection or
   true-up mechanism.
4. **Regime selection remains undecided** — `taxRegime` lets the engine resolve *differently* per
   employee once GDB decides how employees elect/default a regime; this document does not decide
   that, per the explicit instruction.

Proposing filled-in numbers here would not be a conservative placeholder — it would be an
actively incorrect tax calculation if ever activated, which this task's own safety rule
("Engineering must NOT invent... tax percentages... tax thresholds") rules out even as a draft.

Today, an employee's `TDS` component, if configured with no resolvable rule, correctly produces
`MISSING_TAX_CONFIGURATION`/`TAX_CONFIGURATION_REQUIRED` (existing Rule Engine behavior) rather
than a fabricated amount — this is the system behaving exactly as designed in the absence of a
representable rule, not a defect this document needs to work around.

## 5. What this configuration package does **not** do

- It does not insert any row into `statutory_rules`.
- It does not write a Flyway seed migration for real values.
- It does not resolve any of the ten items `docs/INDIA_PAYROLL_V1_RULE_SOURCES.md` Section 6
  lists as PENDING_FINANCE_LEGAL_CONFIRMATION — those remain exactly as unconfirmed as before.
- It does not resolve the Professional-Tax half-yearly-apportionment decision, the TDS rebate/
  cess/surcharge layering, or TDS annualization (Sections 3/4 above) - these were separately
  resolved at the *capability* level by the India Payroll V1 rule-engine architecture-extension
  task (`SLAB_BASED`/`PROGRESSIVE_TAX`/`taxRegime` now exist in `docs/PAYROLL_REQUIREMENTS.md`
  Section H), but remain open as *product* decisions and *unconfirmed legal values* here.
