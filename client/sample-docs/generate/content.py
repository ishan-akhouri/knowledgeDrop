"""Content for the 20 fictional Harborline Health demo documents.

Every document is made up. Terms are real health-insurance vocabulary or invented
but plausible; codes marked "HH-" are internal to the fictional company.
"""

DISCLAIMER = "FICTIONAL SAMPLE - Harborline Health is not a real company. For demo use only."

# Each text document: (filename_stem, format, title, meta, sections)
# sections: list of (heading, [paragraphs or ("bullets", [...]) or ("table", header, rows)])

PROVIDER_MANUAL = (
    "provider-manual-claims-chapter",
    "pdf",
    "Provider Manual, Chapter 7: Claims Submission and Payment",
    "Owner: Provider Relations - Version 2026.3 - Effective October 1, 2026",
    [
        ("7.1 Overview", [
            "This chapter explains how contracted providers submit claims to Harborline Health, how claims are "
            "adjudicated, and how payment and remittance are delivered. It applies to commercial, Medicare "
            "Advantage (HarborCare Advantage) and managed Medicaid (HarborCare Community) products unless a "
            "section says otherwise.",
            "Providers should submit claims electronically whenever possible. Electronic claims are adjudicated on "
            "average 9 days faster than paper claims and are far less likely to pend for missing data.",
        ]),
        ("7.2 Submission methods", [
            ("bullets", [
                "Electronic (preferred): ANSI X12 837P for professional claims and 837I for institutional claims, "
                "sent through our clearinghouse partner. Payer ID: HHLTH1.",
                "Provider Portal direct entry: for low-volume providers, up to 50 claims per day.",
                "Paper: CMS-1500 (02/12) or UB-04, mailed to Harborline Claims, PO Box 4471, Riverton. Paper "
                "claims must be printed in red-ink drop-out format for OCR.",
            ]),
        ]),
        ("7.3 Timely filing", [
            "Initial claims must be received within 180 days of the date of service for commercial products, 365 "
            "days for HarborCare Advantage, and 90 days for HarborCare Community. When Harborline is the secondary "
            "payer, the filing limit runs from the date of the primary payer's remittance.",
            "Corrected claims (frequency code 7) and voids (frequency code 8) must reference the original claim "
            "number and be submitted within 365 days of the original adjudication date.",
            "Claims denied for timely filing (reason code CO-29) may be appealed only with proof of timely "
            "submission, such as a clearinghouse acceptance report (999/277CA) showing the original receipt date.",
        ]),
        ("7.4 Clean claim requirements", [
            "A clean claim has no defect or impropriety and does not need additional substantiation. At minimum "
            "it must include:",
            ("bullets", [
                "Member ID exactly as shown on the ID card, including the two-digit suffix",
                "Rendering and billing provider NPI, and taxonomy code for the rendering provider",
                "Valid ICD-10-CM diagnosis codes coded to the highest specificity",
                "Valid procedure codes and modifiers, units and charges per line",
                "Prior authorization number in loop 2300 REF*G1 when the service requires one",
                "Place of service code that matches the contracted location",
            ]),
            "Harborline pays or denies clean claims within 30 calendar days of receipt (15 days for electronic "
            "clean claims on HarborCare Community). Interest is paid on late clean claims as required by state "
            "prompt-pay rules.",
        ]),
        ("7.5 Adjudication edits", [
            "Claims pass through eligibility, benefit, authorization, duplicate and code-editing checks. Our code "
            "editing follows National Correct Coding Initiative (NCCI) procedure-to-procedure and medically "
            "unlikely edit (MUE) logic, plus Harborline-specific edits listed in the Payment Policy Library.",
            "Common edit results and how to resolve them:",
            ("table", ["Edit", "What it means", "How to resolve"], [
                ["Duplicate (CO-18)", "Same member, provider, date and code already processed", "Check claim status before resubmitting"],
                ["Bundled (CO-97)", "Service included in another paid service", "Append modifier 59/XS only when documentation supports a distinct service"],
                ["Auth missing (CO-197)", "Service required prior authorization", "Request retro-authorization within 14 days or appeal"],
                ["MUE exceeded", "Units exceed the medically unlikely limit", "Split only if clinically appropriate and documented"],
            ]),
        ]),
        ("7.6 Payment and remittance", [
            "Payments are issued twice weekly by EFT. Remittance is delivered as an ANSI 835 file and as a PDF "
            "Provider Remittance Advice in the portal. Providers must enroll in EFT and ERA through the portal's "
            "Payment Setup page; paper checks are issued only during the first 60 days of a new contract.",
            "Overpayments identified by the provider must be reported within 60 days of identification. "
            "Harborline will offset overpayments it identifies against future payments after giving 30 days' "
            "written notice, unless the provider disputes the overpayment in that window.",
        ]),
        ("7.7 Claim reconsideration and appeals", [
            "Providers may request a reconsideration of a payment decision within 120 days of the remittance date "
            "using the Claim Reconsideration form (PR-114). A reconsideration is a review of how the claim was "
            "processed and is not a formal appeal. If the reconsideration is upheld, the provider may file a "
            "formal provider appeal within 60 days. Member appeals follow a separate process (see UM-APL-03).",
        ]),
    ],
)

UM_PROGRAM = (
    "utilization-management-program-description-2027",
    "pdf",
    "Utilization Management Program Description 2027",
    "Owner: Medical Management - Approved by the Quality Improvement Committee, November 2026",
    [
        ("1. Purpose and scope", [
            "The Utilization Management (UM) Program makes sure members receive medically necessary, appropriate "
            "and cost-effective care in the right setting. It covers prior authorization, concurrent review, "
            "retrospective review, discharge planning and care transitions for all Harborline products.",
            "UM decisions are based only on the appropriateness of care and the existence of coverage. Harborline "
            "does not reward practitioners or reviewers for issuing denials, and does not offer incentives that "
            "encourage under-utilization.",
        ]),
        ("2. Program structure", [
            ("bullets", [
                "Chief Medical Officer: accountable for the program and signs the annual program description.",
                "UM Medical Directors: board-certified physicians who make all adverse determinations based on "
                "medical necessity.",
                "UM Nurse Reviewers (RN): apply clinical criteria and may approve, but never deny, requests.",
                "UM Coordinators: non-clinical staff who process intake, check eligibility and route cases.",
                "Pharmacy Clinical Review: pharmacists who review drug prior authorization and formulary exceptions.",
            ]),
        ]),
        ("3. Review criteria", [
            "Reviewers apply nationally recognized, evidence-based criteria (InterQual for inpatient, post-acute "
            "and imaging; ASAM criteria for substance use disorder treatment) and Harborline Medical Policies. "
            "Criteria are reviewed at least annually by the Medical Policy Committee with input from network "
            "practitioners.",
            "Individual circumstances always matter. Reviewers consider the member's age, comorbidities, "
            "complications, home environment and the availability of services in the local delivery system.",
        ]),
        ("4. Decision timeframes", [
            ("table", ["Request type", "Commercial", "HarborCare Advantage", "HarborCare Community"], [
                ["Standard pre-service", "15 calendar days", "7 calendar days", "14 calendar days"],
                ["Expedited pre-service", "72 hours", "72 hours", "72 hours"],
                ["Concurrent (urgent)", "24 hours", "24 hours", "24 hours"],
                ["Post-service", "30 calendar days", "30 calendar days", "30 calendar days"],
            ]),
            "Extensions are allowed only when the member or provider requests one or when Harborline needs "
            "additional information and documents the reason, as permitted by the applicable regulator.",
        ]),
        ("5. Gold carding", [
            "Starting January 2027, practices whose prior authorization requests for a service category were "
            "approved at least 92% of the time over the prior 12 months (minimum 25 requests) are exempt from "
            "prior authorization for that category for the next year. Gold-card status is re-evaluated each "
            "October; a random 5% retrospective sample is audited for appropriateness.",
        ]),
        ("6. Denials and peer-to-peer", [
            "Only a physician or other appropriate clinical peer may issue a denial based on medical necessity. "
            "The treating practitioner is offered a peer-to-peer discussion before or within 5 business days after "
            "any adverse determination. Denial letters state the specific criteria used, how to obtain a copy of "
            "them, and the member's appeal rights.",
        ]),
        ("7. Measuring the program", [
            "The UM Committee monitors turnaround-time compliance (target 98%), overturn rate on appeal (target "
            "below 10%), inter-rater reliability of reviewers (target 90% agreement) and over- and "
            "under-utilization indicators such as inpatient days per 1,000 and 30-day readmission rates.",
        ]),
        ("8. Delegation", [
            "Harborline delegates behavioral health UM to Calmwater Behavioral Partners (fictional) and high-tech "
            "radiology review to ClearScan Review Services (fictional). Delegates are audited annually against "
            "Harborline's standards and must report turnaround times and appeal overturns monthly.",
        ]),
    ],
)

EOB_GUIDE = (
    "how-to-read-your-eob",
    "pdf",
    "Member Guide: How to Read Your Explanation of Benefits",
    "Owner: Member Communications - Plain-language reviewed",
    [
        ("What an EOB is", [
            "An Explanation of Benefits (EOB) is a statement we send after we process a claim for care you "
            "received. It is not a bill. It shows what your provider charged, what Harborline paid, and what you "
            "may owe the provider.",
        ]),
        ("The key columns", [
            ("table", ["Column", "What it means"], [
                ["Amount billed", "What the provider charged before any discounts"],
                ["Plan discount", "The amount removed because the provider is in our network"],
                ["Allowed amount", "The most the plan recognizes for the service"],
                ["Plan paid", "What Harborline paid the provider"],
                ["Deductible / Copay / Coinsurance", "Your share, based on your plan"],
                ["You may owe", "The most the provider can bill you for this service"],
            ]),
        ]),
        ("Tips for customer service representatives", [
            ("bullets", [
                "If 'You may owe' is $0 but the member received a bill, the provider may have billed before the "
                "claim processed. Suggest the member wait for a corrected statement.",
                "Explain that an in-network provider cannot bill more than 'You may owe' (balance billing "
                "protection).",
                "Remark codes at the bottom explain denials; look them up in CLM-REF-09.",
            ]),
        ]),
    ],
)

STARS_BRIEF = (
    "medicare-advantage-star-ratings-brief",
    "pdf",
    "HarborCare Advantage: Star Ratings Improvement Brief",
    "Owner: Quality & Stars Office - For internal leadership use",
    [
        ("Where we stand", [
            "HarborCare Advantage HMO received an overall 3.5-star rating for the 2027 rating year, down from 4.0. "
            "Because plans at 4 stars or above qualify for a quality bonus payment, regaining 4 stars is worth an "
            "estimated $18 million in annual benchmark revenue at current enrollment.",
        ]),
        ("Measures that moved us down", [
            ("bullets", [
                "Statin Use in Persons with Diabetes (Part D): 2 stars, below the cut point by 1.8 points",
                "Medication Adherence for Hypertension (RAS antagonists): 3 stars",
                "Getting Needed Care (CAHPS survey): 3 stars",
                "Members Choosing to Leave the Plan: 3 stars",
            ]),
        ]),
        ("2027 action plan", [
            ("bullets", [
                "Pharmacist outreach to members with 60+ days since last fill on adherence-measure drugs",
                "90-day supply and mail-order default for maintenance medications",
                "Provider gap-in-care reports delivered monthly instead of quarterly",
                "Member Services 'warm transfer' to a nurse line when members report trouble getting appointments",
            ]),
            "Customer service representatives play a direct role in CAHPS measures: every call is a chance to "
            "resolve an access problem before the member answers the survey.",
        ]),
    ],
)

FWA_POLICY = (
    "fraud-waste-abuse-reporting-policy",
    "pdf",
    "Fraud, Waste and Abuse Reporting Policy (COMP-FWA-02)",
    "Owner: Special Investigations Unit - Applies to all employees and contractors",
    [
        ("Policy", [
            "Every Harborline employee must report suspected fraud, waste or abuse (FWA) within 2 business days of "
            "becoming aware of it. Reports may be made anonymously through the Compliance Hotline or the SIU "
            "intake form. Harborline prohibits retaliation against anyone who reports in good faith.",
        ]),
        ("Examples to report", [
            ("bullets", [
                "Billing for services not rendered or upcoding to a higher-paying code",
                "A member lending their ID card to someone else",
                "Kickbacks for referrals, including free services to induce referrals",
                "Unbundling services that should be billed together",
            ]),
        ]),
    ],
)

DUPLICATE_POLICY = (
    "duplicate-claim-editing-policy",
    "docx",
    "Payment Policy: Duplicate and Overlapping Claims (PP-DUP-11)",
    "Owner: Payment Integrity - Effective January 1, 2027",
    [
        ("Purpose", [
            "Defines how Harborline identifies exact duplicate, suspect duplicate and overlapping claims so that "
            "the same service is not paid twice.",
        ]),
        ("Definitions", [
            ("bullets", [
                "Exact duplicate: same member, billing NPI, date of service, procedure code, modifier, units and "
                "charge as a claim already finalized. Denied automatically with CO-18.",
                "Suspect duplicate: matches on member, date of service and procedure code but differs in provider, "
                "modifier or units. Pends for examiner review (pend code DP2).",
                "Overlapping claim: an outpatient or professional service billed during an inpatient stay paid "
                "under a DRG, when the service is included in the facility payment.",
            ]),
        ]),
        ("Examiner guidance for suspect duplicates", [
            "Pay the second line when documentation or modifiers show a distinct service: a different anatomic "
            "site (RT/LT), a repeat procedure by the same physician (modifier 76) or by another physician "
            "(modifier 77), or a separate encounter on the same day (modifier 25 or 59 where appropriate).",
            "Deny as duplicate when the second claim is a resubmission of the same service without frequency code "
            "7. Tell the provider to submit a corrected claim rather than a new original.",
        ]),
        ("Inpatient overlap", [
            "Professional services billed by the hospital's own employed practitioners during a DRG-paid stay are "
            "reviewed against the facility contract. Ambulance, DME delivered for home use on discharge, and "
            "professional fees of independent physicians are not considered overlapping.",
        ]),
    ],
)

CREDENTIALING_SOP = (
    "provider-credentialing-sop",
    "docx",
    "Provider Credentialing and Recredentialing SOP (PNM-CRD-01)",
    "Owner: Provider Network Management - Credentialing Committee approved",
    [
        ("1. Scope", [
            "Applies to physicians, advanced practice providers, behavioral health clinicians and ancillary "
            "providers who are listed in the Harborline directory or who bill independently. Facility "
            "credentialing (hospitals, skilled nursing, home health) follows PNM-CRD-02.",
        ]),
        ("2. Application", [
            "Practitioners apply through their CAQH ProView profile, which must be attested within the last 120 "
            "days. The credentialing specialist confirms the profile is complete before starting verification. "
            "Incomplete applications are returned within 5 business days with a checklist of missing items.",
        ]),
        ("3. Primary source verification", [
            ("table", ["Element", "Source", "Time limit"], [
                ["State license", "State licensing board", "Within 180 days of decision"],
                ["DEA / CDS certificate", "DEA registry or copy of certificate", "Current"],
                ["Board certification", "ABMS, AOA or specialty board", "Within 180 days"],
                ["Education and training", "Board certification or residency program", "Once, at initial credentialing"],
                ["Malpractice history", "National Practitioner Data Bank (NPDB) query", "Within 180 days"],
                ["Sanctions", "OIG LEIE, SAM.gov, state Medicaid exclusion list", "Within 180 days, then monthly"],
                ["Work history", "CV with gaps over 6 months explained", "5 years"],
            ]),
        ]),
        ("4. Committee review", [
            "Clean files (no adverse findings) may be approved by the Medical Director under delegated authority. "
            "Files with malpractice settlements in the last 5 years, license actions, sanctions or unexplained "
            "gaps go to the Credentialing Committee, which meets monthly. Decisions are communicated in writing "
            "within 10 business days.",
            "The credentialing decision must be made within 90 calendar days of receiving a complete application. "
            "Practitioners may review information obtained during verification and correct erroneous "
            "information before the committee decides.",
        ]),
        ("5. Recredentialing", [
            "Every practitioner is recredentialed at least every 36 months. Recredentialing repeats primary source "
            "verification and adds performance data: member complaints, quality-of-care referrals and medical "
            "record review results.",
        ]),
        ("6. Ongoing monitoring", [
            "Between cycles, the team checks the OIG and state exclusion lists monthly and license status "
            "quarterly. Any exclusion results in immediate suspension of payment and removal from the directory "
            "pending committee review.",
        ]),
        ("7. Directory accuracy", [
            "Practitioners must confirm directory information (address, phone, accepting new patients, "
            "telehealth availability) every 90 days. Unverified listings are flagged in the online directory "
            "after 120 days and suppressed after 180 days.",
        ]),
    ],
)

GRIEVANCE_TEMPLATES = (
    "member-grievance-letter-templates",
    "docx",
    "Member Grievance Letter Templates",
    "Owner: Appeals & Grievances - Use the template that matches the case type",
    [
        ("Template A: Grievance acknowledgment", [
            "Dear [Member Name],",
            "We received your complaint on [Date Received] about [brief description]. Your reference number is "
            "[Case Number]. We will review it and send you a written decision by [Due Date]. If you have more "
            "information you'd like us to consider, call Member Services at the number on your ID card.",
        ]),
        ("Template B: Grievance resolution - substantiated", [
            "Dear [Member Name],",
            "Thank you for telling us about [issue]. We reviewed your complaint and agree that [what went wrong]. "
            "To fix this, we have [actions taken]. We are sorry for the inconvenience this caused.",
        ]),
        ("Template C: Grievance resolution - not substantiated", [
            "Dear [Member Name],",
            "We reviewed your complaint about [issue] and found that [findings]. We understand this may not be "
            "the outcome you hoped for. If you disagree, you may [next-level rights for the product].",
        ]),
        ("Usage notes", [
            ("bullets", [
                "Replace every bracketed field; never send a letter with brackets remaining.",
                "Quality-of-care grievances: do not describe clinical findings; route to the Quality Department.",
                "HarborCare Advantage grievances must be resolved within 30 days and may be extended 14 days.",
            ]),
        ]),
    ],
)

MIN_NECESSARY = (
    "hipaa-minimum-necessary-policy",
    "docx",
    "HIPAA Minimum Necessary Standard (PRIV-04)",
    "Owner: Privacy Office",
    [
        ("Policy", [
            "When using or disclosing protected health information (PHI), employees access and share only the "
            "minimum necessary to accomplish the task. On calls, verify the caller with three identifiers (member "
            "ID, date of birth and address on file) before discussing any claim or clinical detail.",
            "The standard does not apply to disclosures to the member, to a provider for treatment, or when "
            "required by law. Report any suspected over-disclosure to the Privacy Office within 24 hours.",
        ]),
    ],
)

FORMULARY_EXCEPTION = (
    "formulary-exception-procedure",
    "docx",
    "Pharmacy Formulary Exception and Step Therapy Override Procedure (RX-FE-06)",
    "Owner: Pharmacy Services - Effective 2027 plan year",
    [
        ("When a formulary exception applies", [
            "A member or prescriber may request coverage of a non-formulary drug, an exception to a quantity "
            "limit, or a lower cost-sharing tier (tiering exception). Step therapy overrides are requested when "
            "the member cannot take the preferred drug first.",
        ]),
        ("Required information", [
            ("bullets", [
                "Prescriber statement of medical need: preferred alternatives were ineffective, caused adverse "
                "effects, or are contraindicated",
                "Drug name, strength, dosing and expected duration",
                "Relevant chart notes or pharmacy claims history",
            ]),
        ]),
        ("Timeframes", [
            ("table", ["Request", "Decision due"], [
                ["Standard exception", "72 hours after the prescriber's supporting statement"],
                ["Expedited exception", "24 hours after the prescriber's supporting statement"],
                ["Transition fill (new members)", "30-day supply dispensed automatically in first 90 days"],
            ]),
        ]),
        ("Approval duration", [
            "Approved exceptions remain in effect for the rest of the plan year, or 12 months for maintenance "
            "drugs, as long as the member stays on the plan and the prescription is refilled.",
        ]),
    ],
)

PRIOR_AUTH_LIST = (
    "prior-authorization-required-services-2027",
    "md",
    "Prior Authorization Required Services List - 2027",
    "Owner: Utilization Management - Effective January 1, 2027",
    [
        ("How to use this list", [
            "Services below require prior authorization (PA) for all commercial and HarborCare products unless the "
            "practice holds gold-card status for that category. Emergency services never require PA.",
        ]),
        ("Services", [
            ("table", ["Category", "Examples", "Review vendor"], [
                ["Advanced imaging", "MRI, CT, PET, nuclear cardiology", "ClearScan Review Services"],
                ["Elective inpatient admissions", "Planned surgical admissions", "Harborline UM"],
                ["Post-acute care", "Skilled nursing, inpatient rehab, LTACH", "Harborline UM"],
                ["Genetic testing", "Panels over $500 allowed", "Harborline UM"],
                ["Specialty drugs (medical benefit)", "Infused biologics, oncology drugs", "Pharmacy Clinical Review"],
                ["DME over $1,000", "Power wheelchairs, CPAP after 90-day trial", "Harborline UM"],
                ["Outpatient therapy", "PT/OT/ST after 12 visits per year", "Harborline UM"],
                ["Behavioral health", "Residential treatment, partial hospitalization, TMS", "Calmwater Behavioral Partners"],
            ]),
        ]),
        ("Changes from 2026", [
            ("bullets", [
                "Removed: cataract surgery, routine colonoscopy, sleep studies in the home setting",
                "Added: transcranial magnetic stimulation (TMS), genetic panels over $500",
            ]),
        ]),
    ],
)

TELEHEALTH_FAQ = (
    "telehealth-coverage-faq",
    "md",
    "Telehealth Coverage FAQ",
    "Owner: Member Services - For representative use on calls",
    [
        ("Is telehealth covered?", [
            "Yes. Video and audio-only visits with in-network providers are covered at the same cost share as "
            "an in-person office visit. HarborNow virtual urgent care visits have a $0 copay on most commercial "
            "plans.",
        ]),
        ("What place of service code do providers use?", [
            "POS 02 for telehealth when the member is not at home and POS 10 when the member is at home, with "
            "modifier 95 for synchronous video visits and modifier 93 for audio-only.",
        ]),
        ("Are out-of-state providers covered?", [
            "Only if the provider is licensed in the state where the member is located at the time of the visit."
        ]),
    ],
)

ID_CARD_SCRIPT = (
    "call-script-id-card-requests",
    "txt",
    "Call Script: Replacement ID Card Requests",
    "Owner: Member Services Training",
    [
        ("Script", [
            "1. Verify the caller with three identifiers (see PRIV-04).",
            "2. Confirm the mailing address on file. If it changed, update it before ordering the card.",
            "3. Order the card in the Member Hub: Member > Cards > Request Replacement. Cards arrive in 7-10 "
            "business days.",
            "4. Offer the digital ID card in the HarborHealth mobile app, available immediately.",
            "5. If the member needs a prescription filled today, give the pharmacy BIN 610832, PCN HHRX and the "
            "group number shown in the Member Hub.",
        ]),
    ],
)

DOWNTIME_BULLETIN = (
    "it-bulletin-claims-system-maintenance",
    "txt",
    "IT Bulletin: Claims Platform Maintenance Window",
    "Owner: IT Service Desk - Bulletin #2026-118",
    [
        ("Notice", [
            "The ClaimsCore adjudication platform will be unavailable Saturday, December 12, 2026 from 10:00 PM to "
            "Sunday 4:00 AM Eastern for the quarterly code-set update (new HCPCS codes and NCCI edit tables). "
            "Claim status lookups in the Member Hub will show data as of 10:00 PM Saturday during the window.",
            "Representatives: tell callers that claim status may be delayed and to call back after 6:00 AM Sunday.",
        ]),
    ],
)

TEXT_DOCUMENTS = [
    PROVIDER_MANUAL, UM_PROGRAM, EOB_GUIDE, STARS_BRIEF, FWA_POLICY,
    DUPLICATE_POLICY, CREDENTIALING_SOP, GRIEVANCE_TEMPLATES, MIN_NECESSARY, FORMULARY_EXCEPTION,
    PRIOR_AUTH_LIST, TELEHEALTH_FAQ, ID_CARD_SCRIPT, DOWNTIME_BULLETIN,
]

# Slide decks: (filename_stem, title, subtitle, slides[(title, [bullets], notes)])
DECKS = [
    (
        "q3-2026-claims-operations-review",
        "Claims Operations Review - Q3 2026",
        "Claims Operations leadership - Quarterly business review",
        [
            ("Q3 at a glance", [
                "4.21M claims received (+6% vs Q2)",
                "Auto-adjudication rate: 87.4% (target 88%)",
                "Clean-claim turnaround: 96.8% within 30 days",
                "Financial accuracy: 99.2%  -  Payment accuracy audit sample of 1,200 claims",
            ], "Volume growth driven by HarborCare Advantage open enrollment gains."),
            ("Top pend reasons", [
                "COB-01 awaiting primary EOB - 18% of pends",
                "DP2 suspect duplicate - 14%",
                "Missing authorization (CO-197 pre-check) - 11%",
                "Provider not loaded / NPI mismatch - 9%",
            ], "NPI mismatch pends doubled after the July provider data migration."),
            ("What we changed", [
                "Auto-match rule for COB when 835 data arrives from the primary payer",
                "Weekly NPI mismatch report sent to Provider Data Management",
                "Examiner job aid for modifier 59/XS reviews",
            ], ""),
            ("Q4 priorities", [
                "Reach 88% auto-adjudication",
                "Cut DP2 pends 25% with a smarter suspect-duplicate algorithm",
                "Load 2027 fee schedules by December 15",
            ], ""),
        ],
    ),
    (
        "new-hire-member-services-training",
        "Member Services New Hire Training - Week 1",
        "Learning & Development",
        [
            ("Welcome to Member Services", [
                "Our job: resolve the member's need on the first call",
                "Week 1: systems, privacy, benefits basics",
                "Week 2: claims and EOBs  -  Week 3: nesting with a mentor",
            ], ""),
            ("Insurance vocabulary you'll hear every day", [
                "Deductible - what the member pays before the plan starts sharing costs",
                "Copay vs coinsurance - flat dollar amount vs percentage",
                "Out-of-pocket maximum - the most a member pays in a plan year",
                "In-network vs out-of-network - contracted providers cost members less",
                "Prior authorization - approval needed before certain services",
            ], "Quiz on Friday covers these terms."),
            ("Privacy first", [
                "Verify three identifiers before discussing PHI",
                "Minimum necessary: share only what the caller needs",
                "Never read full SSNs or bank details aloud",
            ], "See PRIV-04."),
            ("When to escalate", [
                "Safety concerns: warm transfer to the crisis line",
                "Urgent medication access: Pharmacy Urgent Access desk",
                "Regulator, attorney or media: Tier 3 supervisor",
            ], "Full rules in MS-ESC-05."),
            ("Your tools", [
                "Member Hub - eligibility, benefits, claims, ID cards",
                "Knowledge Portal - ask questions about internal policies",
                "Call notes - document every call before wrap-up",
            ], ""),
        ],
    ),
    (
        "value-based-care-program-overview",
        "HarborPartners Value-Based Care Program",
        "Provider Network Strategy - 2027",
        [
            ("Why value-based care", [
                "Pay for outcomes, not volume",
                "Shared savings for primary care groups that lower total cost of care",
                "Quality gates protect members from under-treatment",
            ], ""),
            ("Program tracks", [
                "Track 1 - Upside only: 50% shared savings, no downside risk",
                "Track 2 - Two-sided: 60% shared savings / 40% shared losses, capped at 5% of benchmark",
                "Track 3 - Primary care capitation (PMPM) plus quality bonus",
            ], "Groups need 2,000+ attributed members for Track 2 or 3."),
            ("Quality gate measures", [
                "Controlling High Blood Pressure",
                "Hemoglobin A1c Control for Patients with Diabetes",
                "Colorectal and Breast Cancer Screening",
                "Plan All-Cause Readmissions",
            ], "Groups must hit the 50th percentile on 3 of 4 measures to earn savings."),
        ],
    ),
]

# Workbooks: (filename_stem, title, sheets[(name, header, rows)])
WORKBOOKS = [
    (
        "2027-professional-fee-schedule-excerpt",
        "2027 Commercial Professional Fee Schedule - Excerpt",
        [
            ("Fee Schedule", ["Code", "Description (Harborline summary)", "Modifier", "Allowed - Region 1", "Allowed - Region 2", "PA required"], [
                ["99202", "New patient office visit, straightforward", "", 78.40, 72.15, "No"],
                ["99203", "New patient office visit, low complexity", "", 118.90, 109.30, "No"],
                ["99213", "Established patient office visit, low complexity", "", 96.25, 88.60, "No"],
                ["99214", "Established patient office visit, moderate complexity", "", 135.70, 124.80, "No"],
                ["99215", "Established patient office visit, high complexity", "", 189.40, 174.25, "No"],
                ["70553", "MRI brain with and without contrast", "26", 102.30, 94.10, "Yes"],
                ["70553", "MRI brain with and without contrast", "TC", 388.60, 357.50, "Yes"],
                ["97110", "Therapeutic exercise, each 15 minutes", "GP", 31.85, 29.30, "After 12 visits"],
                ["G0438", "Annual wellness visit, initial", "", 168.20, 154.70, "No"],
                ["HH-T001", "HarborNow virtual urgent care visit", "95", 49.00, 49.00, "No"],
            ]),
            ("Notes", ["Note"], [
                ["Fictional sample. Amounts are made up for demo use."],
                ["Region 1 = metro counties; Region 2 = all other counties."],
                ["Modifier 26 = professional component; TC = technical component."],
            ]),
        ],
    ),
    (
        "denial-reason-crosswalk",
        "Denial Reason Crosswalk - CARC to Internal Codes",
        [
            ("Crosswalk", ["CARC", "Internal code", "Short description", "Member liability", "Typical fix", "Owner team"], [
                ["CO-16", "HH-INFO", "Missing information", "No", "Resubmit with missing data", "Claims"],
                ["CO-18", "HH-DUP", "Exact duplicate", "No", "None - original is processing/paid", "Claims"],
                ["CO-27", "HH-TERM", "Coverage terminated", "Possibly", "Verify eligibility; reinstatement via Enrollment", "Enrollment"],
                ["CO-29", "HH-TFL", "Timely filing", "No", "Appeal with proof of timely submission", "Claims"],
                ["CO-50", "HH-MNEC", "Not medically necessary", "No", "Appeal (UM-APL-03)", "Medical Management"],
                ["CO-97", "HH-BUND", "Bundled service", "No", "Appeal with records for a distinct service", "Payment Integrity"],
                ["CO-197", "HH-NOAUTH", "Authorization absent", "No", "Retro-auth within 14 days", "Medical Management"],
                ["PR-1", "HH-DED", "Deductible amount", "Yes", "None - member cost share", "Member Services"],
                ["PR-2", "HH-COINS", "Coinsurance amount", "Yes", "None - member cost share", "Member Services"],
                ["N/A", "COB-01", "Awaiting primary EOB", "No", "Resubmit with primary EOB", "Claims"],
            ]),
        ],
    ),
    (
        "network-adequacy-report-q4-2026",
        "Network Adequacy Report - Q4 2026 (HarborCare Community)",
        [
            ("By County", ["County", "Members", "PCPs", "Members per PCP", "OB/GYN within 30 min (%)", "Behavioral health within 30 min (%)", "Meets standard"], [
                ["Ashford", 41250, 212, 194.6, 99.1, 97.4, "Yes"],
                ["Brennan", 18730, 61, 307.0, 96.2, 88.9, "Yes"],
                ["Calloway", 9420, 22, 428.2, 84.5, 71.3, "No - BH gap"],
                ["Dunmore", 27880, 140, 199.1, 98.7, 95.0, "Yes"],
                ["Ellery", 6115, 11, 555.9, 78.9, 64.2, "No - PCP and BH gaps"],
                ["Fairhaven", 33540, 168, 199.6, 99.4, 96.8, "Yes"],
            ]),
            ("Standards", ["Measure", "Standard"], [
                ["Members per PCP", "No more than 500"],
                ["OB/GYN access", "90% of members within 30 minutes"],
                ["Behavioral health access", "90% of members within 30 minutes"],
            ]),
            ("Actions", ["County", "Action", "Due"], [
                ["Calloway", "Contract two telehealth-first behavioral health groups", "2027-02-28"],
                ["Ellery", "Recruit FQHC satellite clinic; expand telehealth PCP panel", "2027-03-31"],
            ]),
        ],
    ),
]
