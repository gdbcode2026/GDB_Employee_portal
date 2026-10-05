# India Payroll V1 — Statutory/Tax Rule Sources

This document records the official sources consulted for each statutory rule family the
Configurable Statutory + Tax Rule Engine (`docs/PAYROLL_REQUIREMENTS.md`, Rule Engine task) is
built to hold, and exactly what is/is not safe to treat as an established legal fact today. It
does **not** authorize loading any value into `statutory_rules` — see
`docs/INDIA_PAYROLL_V1_RULE_CONFIGURATION.md` for the proposed (not yet applied) records, and
Section X of `docs/PAYROLL_REQUIREMENTS.md` for the standing PENDING_GDB_APPROVAL list this
document does not override.

**Research method and its limits**: official primary sources (`epfindia.gov.in`,
`labour.gov.in`, `esic.gov.in`, `incometaxindia.gov.in`, `incometax.gov.in`, `pib.gov.in`,
`chennaicorporation.gov.in`, `tnswp.com` — the Tamil Nadu Government's own Single Window Portal)
were searched and fetched directly wherever reachable. Several official PDFs returned HTTP 403
or opaque binary/compressed content that could not be rendered to text in this session;
specific figures below that rest only on *consistent cross-corroboration by multiple independent
secondary reports citing a named, dated official notification* — rather than a directly-read
primary-source sentence — are marked **(secondary-corroborated)** rather than **(primary-verified)**.
No figure in this document comes from a single uncorroborated blog or payroll-vendor marketing
page; where corroboration was thin or sources conflicted, the item is marked
**PENDING_FINANCE_LEGAL_CONFIRMATION** rather than resolved.

**Critical context found during this research, affecting every rule family below**: the
*Code on Social Security, 2020* (along with the other three Labour Codes) was brought into force
on **21 November 2025**, repealing the EPF Act 1952 and the ESI Act 1948 as free-standing
statutes (Notification S.O. 5319(E) dated 21 November 2025, per Section 164(1) of the SS Code).
Section 164(2)(b) of the SS Code preserves the schemes/rules framed under the old ESI Act for one
year from commencement. A new **Employees' Provident Funds Scheme, 2026** was separately notified
(G.S.R. 525(E), dated 29 June 2026, in force from 1 July 2026), superseding the EPF Scheme, 1952.
This means **every EPF/ESI figure dated before 21 November 2025 is now sourced from a repealed
statute's continuing scheme/rules, not the statute itself**, and GDB's Finance/Legal function
should confirm which specific scheme/rule text is the currently governing one before any value
here is treated as final.

---

## 1. EPF / PF

| Field | Value | Source |
|---|---|---|
| Legal framework (current) | Code on Social Security, 2020, Chapter III, read with the Employees' Provident Funds Scheme, 2026 | Ministry of Labour & Employment notification of SS Code commencement (21 Nov 2025, S.O. 5319(E)); EPF Scheme 2026 notification G.S.R. 525(E) dated 29 June 2026, in force 1 July 2026, superseding the EPF Scheme 1952 **(secondary-corroborated** — multiple independent legal/compliance reports cite the identical gazette numbers and dates; the Scheme's full primary text was not independently rendered in this session.**)** |
| Employee contribution rate | 12% of EPF wages | EPF Scheme 2026 as reported; consistent with the pre-2026 EPF Scheme 1952 rate, which was itself confirmed by numerous independent sources over many years **(secondary-corroborated)** |
| Employer contribution rate | 12% of EPF wages, internally split between EPS and EPF/EDLI | Same source as above **(secondary-corroborated)**. The *internal* EPS (historically 8.33%, itself capped on the wage ceiling) vs. EPF balance split, plus EDLI and administrative charges, is **not separately confirmed in this session** and is **PENDING_FINANCE_LEGAL_CONFIRMATION** — see the rule-engine fit note in Section 5 below; no `EPS`/`EDLI`/admin-charge `PayComponent` exists in this codebase's catalogue, so these cannot be separately modeled without a catalogue change outside this task's scope. |
| Wage ceiling for mandatory coverage | **Two conflicting figures found — unresolved, flagged below** | See next row |
| — Figure A: ₹15,000/month | Stated as the EPF Scheme 2026's own prescribed ceiling by one independent legal-update source (Taxguru) describing the Scheme 2026 text | **(secondary-corroborated, single-source for this specific figure)** |
| — Figure B: ₹25,000/month, effective 17 September 2026 | Notification S.O. 5109(E), dated 17 September 2026, issued by the Ministry of Labour & Employment under Chapter III of the SS Code, stated to supersede an intermediate notification S.O. 2702(E) dated 29 May 2026 (which itself is reported to have superseded the original ₹15,000 ceiling) | **(secondary-corroborated** by numerous independent reports (EY India technical alert, multiple legal/HR-compliance publications, a direct PIB press-release reference citing PIB Release ID 2310973) all citing the identical gazette number, date, and supersession chain. A direct fetch of the Ministry of Labour's own hosted PDF announcement returned HTTP 403 in this session, so the primary text itself was not independently read.**)** |
| **PENDING_FINANCE_LEGAL_CONFIRMATION** | Which wage-ceiling figure (₹15,000 or ₹25,000) is the one actually in force for payroll periods processed from the current date forward, and the exact effective date boundary (per secondary reports, 17 September 2026) | GDB Finance/Legal must obtain and confirm the primary gazette text of S.O. 5109(E) (and any notification superseding it since) before this value is configured. The chronology found strongly suggests ₹25,000 is now current, but this document does not treat that as settled. |
| **PENDING_FINANCE_LEGAL_CONFIRMATION** | Whether "EPF wages" for GDB's payroll should be configured against `BASIC_SALARY` alone or a broader basis (historically Basic + Dearness Allowance; this codebase's Common India Payroll V1 Baseline catalogue has no separate DA component) | No official source answers this for GDB specifically — it is a company-level payroll-design decision informed by law, not a law itself. |
| Applicability (eligibility) | Covered automatically from the date of joining for an employee whose wages do not exceed the ceiling at entry, per long-standing EPF practice continued under the SS Code framework; coverage, once established, is not reversed merely because wages later rise above the ceiling | General EPF practice, consistently reported; not independently re-verified against the 2026 Scheme's exact wording in this session — **PENDING_FINANCE_LEGAL_CONFIRMATION** for the precise 2026 Scheme text. |
| UAN / identifier handling | **Not a source-of-law question** — this codebase's existing `EmployeeStatutoryProfile` model (PAYROLL_REQUIREMENTS.md Section G, Compensation Management task) already correctly implements "a missing UAN is a data-quality gap (`MISSING_PF_IDENTIFIER`), never an inference of non-applicability." No source found anywhere suggests otherwise, and this document does not propose changing that model. |
| Coverage inferred from salary amount alone | **Not supported by any source, and not implemented** — `EmployeeStatutoryProfile.pfStatus` is the sole, explicitly-set source of truth for applicability (existing Compensation Management implementation); no change proposed. |

## 2. ESI

| Field | Value | Source |
|---|---|---|
| Legal framework (current) | Code on Social Security, 2020, Chapter IV; ESI Act 1948 repealed 21 Nov 2025 subject to the one-year scheme/rule continuity saving (Section 164(2)(b) SS Code) | Ministry of Labour & Employment SS Code commencement notification; multiple independent legal reports **(secondary-corroborated)** |
| Employee contribution rate | 0.75% of wages | **(primary-verified)** — directly fetched from `esic.gov.in/contribution`, the official ESIC website, stated as effective from 1 July 2019 |
| Employer contribution rate | 3.25% of wages | **(primary-verified)** — same official page |
| Wage ceiling for coverage | ₹21,000/month (gross); ₹25,000/month for persons with disability | **(secondary-corroborated** by numerous independent sources with consistent figures and a consistent "unchanged since January 2017" claim; the official `esic.gov.in/contribution` page fetched directly in this session did not itself state an upper ceiling figure, only the contribution percentages, so the ceiling number specifically is not primary-verified in this session.**)** |
| Low-wage employee exemption | Employees with an average daily wage up to ₹176 are exempt from the *employee's* 0.75% contribution; the employer must still pay its 3.25% share for them | **(primary-verified)** — directly fetched from `esic.gov.in/contribution` |
| **PENDING_FINANCE_LEGAL_CONFIRMATION** | Whether the ₹21,000/₹25,000 ceiling figures remain current given the broader 2026 Labour Code transition, and whether any ESI-specific 2026 scheme (analogous to the EPF Scheme 2026) has since revised them | No ESI-specific 2026 scheme revision to the wage ceiling was found in this session; absence of evidence is not confirmation of continuity. |
| UAN / ESI identifier handling | Existing `EmployeeStatutoryProfile.esiIdentifier`/`esiStatus` model already correctly treats a missing identifier as a data-quality gap (`MISSING_ESI_IDENTIFIER`), never an inferred non-applicability — no change proposed. |

## 3. Professional Tax — Tamil Nadu (Greater Chennai Corporation)

| Field | Value | Source |
|---|---|---|
| Legal framework | Tamil Nadu Panchayats, Municipalities and Municipal Corporations (levy of tax on Professions, Trades, Callings and Employments) — Professional Tax is a *local-body* (municipal corporation/municipality/panchayat) tax in Tamil Nadu, not a single state-wide slab. **Scope note**: per the task's own instruction, this document covers only the **Greater Chennai Corporation (GCC)** rates, since that is the specific local body most consistently documented; every *other* Tamil Nadu local body (other municipal corporations, municipalities, town/village panchayats) sets its **own** slab under the same Act, and those are **not** researched here — if any GDB employee's statutory jurisdiction is a TN local body other than GCC, its specific rate is **PENDING_FINANCE_LEGAL_CONFIRMATION**. |
| Jurisdiction | Greater Chennai Corporation (GCC), Tamil Nadu | — |
| Current half-yearly slab (per employee's half-yearly gross income) | Up to ₹21,000: Nil. ₹21,001–₹30,000: ₹180. ₹30,001–₹45,000: ₹425. ₹45,001–₹60,000: ₹930. ₹60,001–₹75,000: ₹1,025. Above ₹75,000: ₹1,250. | **(secondary-corroborated** by at least five independent, mutually-consistent reports (legalitysimplified, ascent-hr, akriviahcm, greythr product-update notes, calcguru, dtnext news coverage), citing a Greater Chennai Corporation revision "effective for the 2nd half of FY 2024-25" and a specific notification reference, R.D.(HQ).C.No.PT/SPL/2024 dated 6 March 2025, reported by one source. The underlying GCC/TNSWP-hosted PDF was fetched directly in this session but returned only unreadable compressed binary content — the notification number and exact effective date are therefore **not primary-verified** in this session, only cross-corroborated.**)** |
| Payment cadence | Half-yearly (due before 30 September for April–September; before 31 March for October–March) | Same sources; `chennaicorporation.gov.in` identified as the official payment portal. |
| **PENDING_FINANCE_LEGAL_CONFIRMATION** | The exact notification number/date, and confirmation this is still the current slab (no later revision found or ruled out) | Requires GDB Finance/Legal to obtain the GCC's own current circular directly, e.g. via `chennaicorporation.gov.in` or a certified copy of R.D.(HQ).C.No.PT/SPL/2024. |
| **PENDING_FINANCE_LEGAL_CONFIRMATION** | Which GDB employees fall under GCC jurisdiction vs. another Tamil Nadu local body (each with its own, un-researched slab) | This is an HR/Finance jurisdiction-mapping question, not a legal-research question this document can resolve. |
| Jurisdiction-awareness in the data model | Already implemented — `EmployeeStatutoryProfile.ptJurisdiction` records a free-text jurisdiction per employee, and `StatutoryRuleCalculationStrategy` resolves a jurisdiction-matching `StatutoryRule` version at calculation time (Rule Engine task). This document's proposal (Section 5 below, and the companion configuration document) treats "Tamil Nadu - Greater Chennai Corporation" as one specific jurisdiction value among potentially many — it is **not** treated as a national Professional Tax rule. |

## 4. Salary TDS / Income Tax

| Field | Value | Source |
|---|---|---|
| Governing Act from 1 April 2026 | **Income-tax Act, 2025** (Act No. 30 of 2025), replacing the Income-tax Act, 1961 in full from that date; tax years beginning before 1 April 2026 remain governed by the 1961 Act | **(primary-verified)** — directly fetched from `incometax.gov.in`'s official "Objective and scope of the New Act" page, and corroborated by a PIB document (`static.pib.gov.in`) confirming Parliament passage (12 Aug 2025) and Presidential assent (21 Aug 2025) |
| "Tax Year" terminology | The 2025 Act replaces "previous year"/"assessment year" with a single "Tax Year" (1 April–31 March, or a shorter period for a business/activity starting mid-year) | **(primary-verified)** — same `incometax.gov.in` page, quoting its own FAQ |
| New tax regime — governing section | Section 202 of the Income-tax Act, 2025 (successor to Section 115BAC of the 1961 Act); remains the **default** regime; an employee may still elect the old regime | **(primary-verified)** — same `incometax.gov.in` page |
| New regime slabs (FY 2025-26 / "Tax Year" 2026-27 under the new Act — Budget 2025 structure, reported unchanged by Budget 2026) | ₹0–4,00,000: Nil. ₹4,00,000–8,00,000: 5%. ₹8,00,000–12,00,000: 10%. ₹12,00,000–16,00,000: 15%. ₹16,00,000–20,00,000: 20%. ₹20,00,000–24,00,000: 25%. Above ₹24,00,000: 30%. | **(secondary-corroborated** by numerous independent, mutually-consistent tax-calculator/advisory publications (ClearTax, Axis Max Life, Bajaj Finserv, Canara HSBC Life) explicitly describing this as the Budget 2025 structure carried forward unchanged into FY 2026-27 by Budget 2026. The exact slab table was not independently read from the Act's own schedule text in this session (PDF fetch of the Act text returned unreadable binary content).**)** |
| New regime rebate (Section 87A equivalent) | 100% rebate on tax payable for a resident individual with total taxable income up to ₹12,00,000, capped at ₹60,000 of rebate | **(secondary-corroborated)**, same sources as above |
| New regime standard deduction (salaried) | ₹75,000 | **(secondary-corroborated)**, same sources |
| Old regime slabs (unchanged structure, available by election) | ₹0–2,50,000: Nil. ₹2,50,000–5,00,000: 5%. ₹5,00,000–10,00,000: 20%. Above ₹10,00,000: 30% (long-standing structure) | **(secondary-corroborated** — consistent across multiple independent tax-advisory sources; not independently re-confirmed against the Income-tax Act 2025's own old-regime provision text in this session.**)** |
| Old regime rebate (Section 87A equivalent) | 100% rebate up to ₹12,500, for total taxable income up to ₹5,00,000 | **(secondary-corroborated)** |
| Old regime standard deduction (salaried) | ₹50,000 | **(secondary-corroborated)** |
| Health and Education Cess | Historically 4% of (tax + surcharge), under both regimes | **(secondary-corroborated only by background knowledge/general tax-advisory consensus; not independently re-confirmed as continuing unchanged under the Income-tax Act, 2025 in this session — PENDING_FINANCE_LEGAL_CONFIRMATION.)** |
| Surcharge (high-income taxpayers) | Tiered surcharge historically applies above ₹50 lakh/₹1 crore/etc. thresholds, with marginal-relief provisions; not researched in detail in this session as out of scope for typical GDB salary bands | **PENDING_FINANCE_LEGAL_CONFIRMATION** if GDB has any employee in the applicable income band. |
| Regime must be employee-specific, never assumed | No source anywhere suggests a single regime can be assumed for every employee — the new regime is merely the *default* absent an election. This document's proposal (Section 5) does **not** hard-code one regime; see the architecture-fit note below. |

## 5. Rule-engine fit — a finding, not a value

**Update (India Payroll V1 rule-engine architecture-extension task): this capability gap is now
resolved at the architecture level.** The original finding below is preserved for its historical
accuracy at the time this research was written; see
`docs/PAYROLL_REQUIREMENTS.md`'s "Revision: India Payroll V1 rule-engine architecture extension
implemented" section for what changed. In short: `StatutoryRuleCalculationType` now also includes
`SLAB_BASED` (exactly one matching bracket applies, non-cumulative — the correct shape for
Professional Tax's flat-fee-per-bracket structure) and `PROGRESSIVE_TAX` (every bracket reached
contributes its own marginal share, summed — the correct shape for income-tax withholding), both
built on an ordered, contiguous, non-overlapping `StatutoryRuleBracket` list, validated at write
time. `StatutoryRule.taxRegime` (mirroring `jurisdiction`) now lets a `TDS`-coded component
resolve per the employee's own elected regime. **No real rate, slab, threshold, or eligibility
value was introduced by that extension — only the generic capability to express them once
supplied.** Every item in Section 6 below remains exactly as unconfirmed as before; only the
"the engine literally cannot express this" finding is resolved, not the legal-value sourcing gap.

The original finding (preserved for context): the existing `StatutoryRuleCalculationType` enum
(`FIXED_AMOUNT`, `PERCENTAGE`, `THRESHOLD_BASED` — `docs/PAYROLL_REQUIREMENTS.md` Section H) was
**sufficient for EPF and ESI** (both are a flat percentage of a wage basis, gated by a wage
ceiling — exactly what `THRESHOLD_BASED` already expresses) but **was not sufficient to
correctly express**:

- **Tamil Nadu Professional Tax's multi-bracket, flat-fee-per-bracket slab structure** — six
  brackets, each a *fixed* half-yearly amount (not a percentage), on a *half-yearly* income
  basis, while this codebase's payroll period is locked to monthly (decision 3). No single
  `StatutoryRule` version could hold six brackets; `THRESHOLD_BASED`'s one floor/one ceiling/one
  cap shape could not represent "a different flat fee per bracket" at all. **Now expressible via
  `SLAB_BASED`** — the half-yearly-vs-monthly period mismatch and per-employee bracket selection
  remain open product questions, documented in the companion configuration document.
- **Salary TDS's progressive, multi-slab, regime-dependent, annualized withholding calculation**
  — a fundamentally different kind of computation (marginal rates applied to *projected annual*
  taxable income, divided across the year, with employee-level regime election, rebate
  cliffs, and cess) that none of the three original calculation shapes could approximate, let
  alone exactly express. **Now expressible via `PROGRESSIVE_TAX` plus `taxRegime`-aware
  resolution** for the core progressive-bracket calculation; rebate/cess/surcharge layering and
  annualization-vs-monthly-withholding remain open product/engineering questions, documented in
  the companion configuration document.

## 6. Summary of PENDING_FINANCE_LEGAL_CONFIRMATION items

1. Which EPF wage ceiling (₹15,000 or ₹25,000) governs payroll from the current date, and the
   exact effective-date boundary.
2. What "EPF wages" should be configured as for GDB specifically (Basic Salary alone, given no
   separate Dearness Allowance component exists in the current catalogue).
3. The EPS/EPF-balance/EDLI/administrative-charge internal split of the employer's 12% EPF
   contribution — not separately modelable without a catalogue change outside this task's scope.
4. Whether the ESI ₹21,000/₹25,000 wage ceilings remain current post-2026-Labour-Code-transition.
5. The exact GCC Professional Tax notification number/date and whether it remains current.
6. Which GDB employees fall under GCC jurisdiction vs. another, un-researched Tamil Nadu local
   body.
7. The monthly-vs-half-yearly apportionment treatment for Professional Tax (a business decision,
   not a law) — see the companion configuration document.
8. Continuation of the 4% Health and Education Cess and surcharge thresholds under the
   Income-tax Act, 2025.
9. GDB's own policy for which tax regime is offered/defaulted per employee, and how/when an
   employee elects (a company HR process question, not purely a legal one, though the law
   requires the capability to differ per employee).
10. **The Professional Tax slab and TDS calculation architecture-fit gap itself (Section 5)** —
    this is not confirmable by Finance/Legal; it requires an engineering/product decision before
    any TDS or complete Professional Tax configuration can be loaded.
