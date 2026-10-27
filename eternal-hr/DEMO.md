# Eternal HR demo: step-by-step

## 0. Start or check the stack

Open Git Bash in the repo root and run:

```bash
SKIP_BUILD=1 bash deploy/local/up.sh                      # platform: engine, tasklist, Caddy
SKIP_BUILD=1 bash eternal-hr/deploy/scripts/up-local.sh   # Eternal HR and the mailbox
docker ps --format '{{.Names}}  {{.Status}}'              # wait until everything shows (healthy)
```

To rebuild after code changes, run the same two commands without `SKIP_BUILD=1`.

## Links and logins

| What | Link | User | Password |
| --- | --- | --- | --- |
| Tasklist (Eternal HR Onboarding) | https://localhost:18443 | `tm.demo`, `rm.demo`, `ch.demo`, `pm.demo`, `hr.demo` | `e924c986c268` (same for all five) |
| Mailbox (every email sent) | https://localhost:18443/mail/ | `mail` | `435697909b43` |
| Cockpit (watch the process) | https://localhost:18443/camunda/ | `admin` | `76d14852f39f2fa7d622f953` |
| Document storage (local S3, RustFS console) | http://127.0.0.1:19001/rustfs/console/ | `DOCUMENTS_ACCESS_KEY` | `DOCUMENTS_SECRET_KEY` |

These are the passwords of this machine's local stack, from `eternal-hr/deploy/.env` (`DEMO_PASSWORD`,
`MAILPIT_PASSWORD`) and `deploy/local/.env` (`SAMANVAY_ADMIN_PASSWORD`, and the storage keys `DOCUMENTS_ACCESS_KEY`
and `DOCUMENTS_SECRET_KEY`). If you recreate the stack, read the new ones
from those files.

The browser warns about the certificate the first time. Click **Advanced → Proceed**.

**Tip:** for the smoothest demo, use three windows:
- one browser window for the tasklist;
- an incognito window for the mailbox and the employee form;
- a third tab for Cockpit.

Before you switch to another user in the tasklist, use **Sign out** (top right).

---

## Act 1: Joining request and approvals

**Step 1. The Territory Manager raises the request**

1. Open https://localhost:18443 and sign in as `tm.demo` / `e924c986c268`.
2. Click **Start → Employee joining & documentation**.
3. Fill in the form:

   | Field | Example |
   | --- | --- |
   | Candidate full name | `Ananya Sharma` |
   | Personal email | `ananya@example.com` |
   | Mobile | `9876543210` |
   | Designation | `Sales Executive` |
   | Department | Sales |
   | Project / client | `Acme Retail` |
   | Work location | `Pune` |
   | Employment type | permanent |
   | Proposed joining date | any date next month |
   | Proposed annual CTC | `450000` |

4. **Attach documents** in the *Documents* box above the form. Choose a **Document type** (for example
   *Joining approval / supporting document*), click **Attach file** and pick a PDF or a JPEG/PNG photo. Click the
   attached file to preview it on the right, beside the form.
5. Click **Start**, then **Sign out**.

**Step 2. The Reporting Manager reviews it**

1. Sign in as `rm.demo` and open **Review joining: Ananya Sharma**.
   - If the tasklist asks, click **Claim** first. This applies to every task below.
2. The **Documents** box lists everything attached so far. Click one: it opens on the right, beside the form,
   so you can check it against the request. PDFs show in the browser's viewer; click an image to zoom.
3. After claiming you can attach documents too. You can remove only your own.
4. Set **Your decision** to **forward**, add a comment, complete the task and sign out.

**Optional: show a send-back.**
- `rm.demo` chooses **sendBack** instead of forward.
- `tm.demo` gets **Update joining request**, fills in **What did you change?** and completes it.
- The request goes back to `rm.demo`.

**Step 3. The Country Head approves**

1. Sign in as `ch.demo` and open **Approve joining (Country Head)**.
2. Choose **approve**, complete the task and sign out.

**Step 4. The Project Manager approves**

1. Sign in as `pm.demo` and open **Approve joining (PM)**.
2. Choose **approve**, complete the task and sign out.

The system remembers `pm.demo` as the PM who reviews the appointment letter later.

**Optional: show a reject.** If any approver chooses **reject**, the process ends. The TM gets a "Joining request
rejected" email, which you can show in the mailbox.

## Act 2: HR and the employee's joining form

**Step 5. HR starts the formalities**

1. Sign in as `hr.demo` and open **Start joining formalities: Ananya Sharma**.
2. Confirm the email and joining date.
3. Tick **Approved joining confirmation received**.
4. Set **Supporting documents received** to Yes.
5. Complete the task.

**Step 6. The employee gets the link by email**

1. Open https://localhost:18443/mail/ and sign in as `mail` / `435697909b43`.
2. Open **Welcome to Eternal HR: complete your joining form** and click the link.

**Step 7. The employee fills in the joining form**

The employee has no login; the signed link is all they need. Name, email and mobile are already filled in. Enter
the rest:

| Field | Example |
| --- | --- |
| Date of birth | 1998-05-14 |
| Gender | Female |
| Father's / spouse's name | Rakesh Sharma |
| Current and permanent address | any |
| Emergency contact | Rakesh Sharma, `9812345678` |
| PAN | `ABCDE1234F` |
| Aadhaar (last 4 digits) | `4321` |
| Bank name | HDFC Bank |
| IFSC | `HDFC0001234` |
| Bank account (last 4 digits) | `5678` |
| Highest qualification | MBA |
| Experience (years) | `2` |

Under **Documents**, choose what each file is (for example *PAN card* or *Aadhaar card*) and pick the file
(PDF, JPEG or PNG, up to 10 MB). Use **+ Add another document** for more. Then tick the declaration and click
**Submit**.

- **Show validation:** enter a PAN like `ABC123` and the form rejects it with a message.
- **Show single use:** open the same link again; it now says the link is no longer available.
- **Show the file check:** rename a text file to `.pdf` and attach it; it is refused because it is not really a PDF.
- **Show reminders:** skip this step for 10 minutes and a reminder email arrives in the mailbox. Up to 3 are sent.

**Step 8. The system creates the HRMS record**

This is automatic. Within a few seconds an employee code such as `EHR-2610-xxxxx` is created. It appears on the next
HR tasks.

## Act 3: Induction, appointment letter and documents

`hr.demo` now has **two** tasks, because induction runs in parallel with the letter.

**Step 9. Induction**

1. As `hr.demo`, open **Conduct induction**.
2. Pick an induction date and select who attended.
3. Complete the task.

**Step 10. Prepare the appointment letter**

1. As `hr.demo`, open **Prepare appointment letter**.
2. Check the CTC, date and location, and enter `AL-2026-001` as the letter reference.
3. Complete the task.

The mailbox now has **Appointment letter for approval**, sent to `pm.demo`.

**Step 11. The PM reviews the letter**

1. Sign in as `pm.demo` and open **Review appointment letter**.
2. Choose **approve** and complete the task.

The mailbox now has **Your appointment letter from Eternal HR**, sent to the employee.

**Optional: show requested changes.**
- The PM chooses **changes** and says what should change.
- HR gets **Revise appointment letter** and must tick *I have the project manager's written approval*.
- The letter goes back to the PM for review.

**Step 12. Collect documents**

1. As `hr.demo`, open **Collect employee documents**.
2. Mark each document as received.
3. Tick **All received documents are moved to the HRMS**.
4. Complete the task.

**Step 13. Review documents**

This step shows the problem path:

1. Open **Review documents**. Every document from the TM, the approvers and the employee is in the
   *Documents* box. Click each one and compare it with the details in the form beside it.
2. Set **Are all documents clear…** to **No**.
3. Enter an issue under *Issues found*, for example `PAN copy unreadable`, and complete the task.
4. Check the mailbox: the employee got an **Action needed** email.
5. HR now has **Follow up on documents**. Enter notes, tick **Corrected documents received**, and complete it.
   - If you leave the follow-up task open for 15 minutes, the PM gets an escalation email.
6. **Review documents** comes back. This time answer **Yes** to both questions and complete it.

**Step 14. Correct and verify the data**

1. Open **Correct and verify employee data**.
2. Fix anything you want.
3. Tick the three confirmations and complete the task.

**Where the files are:** open the S3 console at http://127.0.0.1:19001/rustfs/console/ (keys from `deploy/local/.env`) and browse
the `samanvay-documents` bucket. Each joining has a folder `process-instances/<id>/documents/`. On AWS the same
layout is in an S3 bucket.

## Finale

**Step 15. Onboarding completes**

- The HRMS record is marked verified.
- The mailbox shows **onboarding complete** emails to `tm.demo` and `pm.demo`, with the employee code.

**Step 16. Show the process in Cockpit**

1. Open https://localhost:18443/camunda/ and sign in as `admin`.
2. Go to **Cockpit → Processes → Employee joining & documentation**.
3. Open the **History** tab of the instance to show:
   - the path it took through the six lanes;
   - every variable, including who approved and the employee code;
   - that there are 0 incidents.

---

To run the demo again, start a new joining as `tm.demo`. Each one is independent, so old instances do no harm.
