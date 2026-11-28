# FICTIONAL SAMPLE — Harborline Health is not a real company. For demo use only.

# Claims Denial Codes: Quick Reference (CLM-REF-09)

Owner: Claims Operations · Updated: May 2026

Codes beginning with **CO** are standard claim adjustment reason codes (contractual obligation: the provider
cannot bill the member). Codes beginning with **N-** or **COB-** are Harborline internal codes.

| Code | Meaning | What resolves it | Member owes? |
|---|---|---|---|
| CO-4 | Procedure code inconsistent with modifier, or modifier missing | Provider resubmits with the correct modifier | No |
| CO-16 | Claim lacks information needed for adjudication | Provider resubmits with the missing data (check remark code) | No |
| CO-18 | Exact duplicate claim | No action; original claim is processing or paid | No |
| CO-27 | Expenses incurred after coverage terminated | Verify eligibility; if coverage was reinstated, reprocess | Possibly |
| CO-29 | Time limit for filing has expired | Appeal only with proof of timely original submission | No |
| CO-50 | Not medically necessary | Follow the appeals process (UM-APL-03) | No, unless a signed waiver is on file |
| CO-97 | Service included in another service already paid (bundling) | Provider may appeal with records showing a separate service | No |
| CO-109 | Not covered by this payer; submit to the correct payer | Check COB and other coverage | Possibly |
| CO-197 | Precertification or authorization absent | Provider requests retro-authorization within 14 days of service, or appeals | No |
| COB-01 | Awaiting primary payer EOB | Provider resubmits with the primary EOB (see CLM-COB-02) | No |
| N-340B-07 | Pharmacy not registered as a contract pharmacy for the covered entity | Route to queue PH-340B (see CLM-340B-01) | No |

## Notes for representatives

- For **CO-197**, check whether an authorization exists under a different provider NPI before telling the
  member it is missing; mismatched NPIs cause a large share of these denials.
- For **CO-27**, confirm the termination reason. Retroactive reinstatements after a missed premium are
  processed by Enrollment, not Claims.
- Never tell a member they owe the balance on a CO-coded denial. CO denials are the provider's
  responsibility under their contract.
