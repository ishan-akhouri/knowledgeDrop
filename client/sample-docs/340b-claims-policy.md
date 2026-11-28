# FICTIONAL SAMPLE — Harborline Health is not a real company. For demo use only.

# 340B Drug Pricing Program: Claims Handling Policy (CLM-340B-01)

Owner: Pharmacy Benefits Operations · Effective: January 1, 2026 · Review cycle: annual

## 1. Purpose

This policy explains how Harborline Health identifies, adjudicates and reconciles pharmacy and medical
claims for drugs purchased under the federal 340B Drug Pricing Program. The goal is to prevent duplicate
discounts (a 340B discount plus a Medicaid rebate on the same unit) and to pay covered entities correctly.

## 2. Key terms

- **Covered entity (CE):** a hospital or clinic registered in the HRSA 340B database (for example
  disproportionate share hospitals, federally qualified health centers and Ryan White clinics).
- **Contract pharmacy:** an outside pharmacy that dispenses 340B drugs on behalf of a covered entity.
- **340B identifier:** for pharmacy claims, Submission Clarification Code (SCC) **20** in NCPDP field 420-DK.
  For medical (outpatient) claims, HCPCS modifier **JG** (drug acquired at 340B price) or **TB**
  (reporting-only for certain providers).
- **Duplicate discount:** a Medicaid drug rebate claimed on a unit already purchased at the 340B price.

## 3. Submission requirements

1. Pharmacy claims for 340B-purchased drugs **must** carry SCC 20. Claims without it are treated as non-340B.
2. Medical claims must include modifier JG or TB on each 340B drug line, plus the NDC and NDC units.
3. Contract pharmacies must be listed on the CE's current HRSA registration on the date of service.
4. Claims must be submitted within **90 days** of the date of service. Late 340B claims are denied with
   reason code CO-29 and may be appealed only with proof of timely original submission.
5. Managed Medicaid members: the CE's Medicaid Exclusion File status determines whether 340B drugs may be
   billed. If the CE has "carved out" Medicaid, 340B drugs must **not** be billed for Medicaid members.

## 4. Adjudication rules

- Claims flagged as 340B are priced at the contracted 340B reimbursement schedule (Schedule P-7), not the
  standard ingredient-cost formula.
- Claims flagged as 340B for managed Medicaid members are excluded from the quarterly rebate file sent to the
  state, to prevent duplicate discounts.
- If a claim is flagged as 340B but the pharmacy is not a registered contract pharmacy on the date of
  service, deny with reason code **N-340B-07** ("pharmacy not registered for covered entity").

## 5. Retroactive identification and reconciliation

Covered entities sometimes identify 340B eligibility after a claim has paid (retroactive "replenishment"
models). Retroactive flags are accepted for up to **180 days** after the date of service through the 340B
Retro Flag File (weekly, SFTP). Pharmacy Benefits Operations reprocesses affected claims and removes them from
the next rebate submission. Flags older than 180 days are rejected and the CE is notified.

## 6. Customer service guidance

- Members are not affected by 340B status: copays and coinsurance are the same either way. If a member asks
  why their claim mentions 340B, explain that it is a pricing program between the pharmacy and the
  manufacturer and does not change what they owe.
- Provider calls about 340B denials should be routed to the **Pharmacy Provider Help Desk (queue PH-340B)**.
  Do not reprocess 340B claims from the general claims queue.

## 7. Audits

Pharmacy Benefits Operations audits a random 2% sample of 340B claims each quarter for correct identifiers
and Medicaid exclusion compliance. Findings are reported to the Compliance Committee.
