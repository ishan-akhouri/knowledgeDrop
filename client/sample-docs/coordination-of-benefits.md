# FICTIONAL SAMPLE — Harborline Health is not a real company. For demo use only.

# Coordination of Benefits (COB) Reference Guide (CLM-COB-02)

Owner: Claims Operations · Effective: February 1, 2026

## What COB is

Coordination of benefits applies when a member is covered by more than one health plan. One plan pays first
(primary) and the other pays second (secondary). The combined payment may not exceed the allowed amount.

## Determining the primary plan (order of benefit rules)

1. **Non-dependent vs dependent:** the plan covering the person as an employee or subscriber is primary over
   the plan covering them as a dependent.
2. **Birthday rule (dependent children of married or cohabiting parents):** the plan of the parent whose
   birthday (month and day, not year) falls **earlier in the calendar year** is primary.
3. **Divorced or separated parents:** a court decree assigning responsibility controls. Without one, the
   order is: custodial parent, spouse of custodial parent, non-custodial parent, spouse of non-custodial parent.
4. **Active vs retired or laid-off:** coverage through active employment is primary.
5. **COBRA:** the plan that is not COBRA continuation coverage is primary.
6. **Longer vs shorter coverage:** if none of the above apply, the plan that has covered the person longer is
   primary.

Medicare has its own rules: for employer groups with 20+ employees, the employer plan is primary for active
workers aged 65+. For retirees, Medicare is primary.

## Claims handling

- Claims arriving at Harborline as **secondary** must include the primary payer's explanation of benefits
  (EOB) or electronic remittance (835 data). Claims without it pend with reason code **COB-01**
  ("awaiting primary EOB") for 45 days, then deny.
- Harborline as secondary pays the lesser of (a) what it would have paid as primary or (b) the member's
  remaining liability after the primary payment.
- Annual COB questionnaires are mailed each January. If a member does not respond within 60 days, claims are
  **not** denied; Claims Operations flags the account for outreach instead.

## Customer service tips

- Ask: "Do you or anyone on your plan have other health coverage, including Medicare, Medicaid, or a
  spouse's employer plan?"
- Update COB information only after verifying the other carrier's name, member ID and effective date.
- If a member says a claim was denied for COB-01, check whether the primary EOB is on file before
  transferring; often the provider simply needs to resubmit with it attached.
