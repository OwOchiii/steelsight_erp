# Steelsight: Workflow Tutorial

A step-by-step walk through every main workflow of the SRS (Report 3, section 1.2), with the buttons to
click, what to type, what you should see, and what happens when you break a rule. Use it to learn the
system, to demo it, or as a manual test script: each "Try this" row is a check with its expected result.

The labels are the English ones; switch with the **EN / VI** control in the top bar, and the same
buttons appear in Vietnamese. Messages in quotes are shown exactly as written here. Where the browser
checks a field first (a pattern or a required field), it shows its own hint before the form is sent; the
quoted message is what the server says if the value gets past the browser.

**Contents**

- [0. Set up a practice site](#0-set-up-a-practice-site)
- [1. Log in and change password (WF 1.2.1)](#1-log-in-and-change-password-wf-121)
- [2. New employee: consent, account, face photos and Zalo link (WF 1.2.2)](#2-new-employee-consent-account-face-photos-and-zalo-link-wf-122)
- [3. Teams and the weekly schedule (WF 1.2.3)](#3-teams-and-the-weekly-schedule-wf-123)
- [4. Gate check-in and check-out (WF 1.2.4)](#4-gate-check-in-and-check-out-wf-124)
- [5. PPE check at the gate (WF 1.2.5)](#5-ppe-check-at-the-gate-wf-125)
- [6. Work zone PPE monitoring and the HSE alert (WF 1.2.6)](#6-work-zone-ppe-monitoring-and-the-hse-alert-wf-126)
- [7. Violation review and confirmation (WF 1.2.7)](#7-violation-review-and-confirmation-wf-127)
- [8. Violation appeal (WF 1.2.8)](#8-violation-appeal-wf-128)
- [9. Attendance error report (WF 1.2.9)](#9-attendance-error-report-wf-129)
- [10. Missing check-out explanation (WF 1.2.10)](#10-missing-check-out-explanation-wf-1210)
- [11. Pending overtime (WF 1.2.11)](#11-pending-overtime-wf-1211)
- [12. Leave and early leave request (WF 1.2.12)](#12-leave-and-early-leave-request-wf-1212)
- [13. Monthly safety score, bonus level and report (WF 1.2.13)](#13-monthly-safety-score-bonus-level-and-report-wf-1213)
- [14. Offline queue and notification delivery (WF 1.2.14)](#14-offline-queue-and-notification-delivery-wf-1214)
- [15. Quick reference: rules and their messages](#15-quick-reference-rules-and-their-messages)

---

## 0. Set up a practice site

**Shortcut:** on your own computer, `scripts/local.sh up` and `scripts/local.sh seed` build this whole
practice site with sample people, visits, violations and tickets in a few minutes; see
[local-run.md](local-run.md). Then skip to section 1. To build it by hand instead:

You need the first Admin account, which the server creates at start-up from `ADMIN_EMPLOYEE_CODE` and
`ADMIN_PASSWORD` (see `docs/deployment.md`). Then, as the Admin:

1. **Zones.** Open **Zones** in the sidebar. You need four zones; for each one click **New zone**, type
   the **Name in alerts**, choose the **Kind**, and click **Add zone**:

   | Name in alerts | Kind | Code you should see on Zones |
   | --- | --- | --- |
   | Main gate | Gate attendance | GATE-IN |
   | Gate PPE check | Gate PPE check | GATE-PPE |
   | Area A - west embankment | Work area | AREA-A |
   | Area B - pier 3 | Work area | AREA-B |

   The code is given by the system and can't be changed.
2. **Cameras.** Still on **Zones**, under **Link a camera**, type the **Camera code** (CAM-01) and choose
   the **Zone** (Main gate), then click **Link a camera**. Repeat for CAM-02 (Gate PPE check), CAM-03
   (Area A) and CAM-04 (Area B). CAM-01 shows the role *Attendance*, the others *PPE*.
3. **Accounts.** Open **Team**, click **New account**, and create one person per role (section 2 shows
   the form in detail): a Manager, an HSE officer, two Workers and a second Admin. Write down each
   starting password.
4. **Team.** On **Team**, under **Teams**, type a **New team** name, choose the Manager under **Choose a
   leader**, and click **Create team**. Then open each Worker (**Edit**), choose the team under **Role
   and team**, and click **Change team**.
5. **Work areas.** On **Zones**, give Area A the new team in the **Team** column and click **Save**.

**Try this**

| Try | Expected result |
| --- | --- |
| Add a zone named "X" | "A zone name has 2 to 50 characters" |
| Add a second zone named "main GATE" | "A zone with this name already exists." (names are compared without capitals) |
| Add a gate zone and give it a team | "Only a work area can belong to a team" |
| Link the camera code "C" | "A camera code has 2 to 50 letters, digits, dots, dashes or underscores" |
| Remove CAM-01 after it recorded a check-in | "This camera has records, so it can't be removed. Link it to another zone instead." |
| Create a team with no leader | "Choose a leader for the team" |
| Choose a Manager who already leads a team | "This manager already leads another team" |
| Delete a team that still has people | "Move this team's people to another team first" |

---

## 1. Log in and change password (WF 1.2.1)

**Who:** everyone.

1. Open the ERP address. On **Sign in**, type your **Employee code** and **Password**, and click
   **Sign in**.
2. If an Admin chose your password (a new account or a reset), you land on **Change password** with
   "An admin set your password. Choose your own before you continue." Type the **Current password**
   (the one the Admin gave you), a **New password**, the same again in **Repeat the new password**, and
   click **Change password**. You see "Your password was changed." and the **Dashboard**.
3. To change it later, open the account menu (your name, top right) and choose **Change password**.
4. To leave, open the account menu and click **Sign out**: "You have signed out."

**Try this**

| Try | Expected result |
| --- | --- |
| A wrong password, or a code that doesn't exist | "The employee code or password is wrong." (the same message for both, BR-01) |
| Five wrong passwords in a row from the same computer | "Too many failed sign-ins. Try again in 15 minutes." Even the right password is refused until then (BR-04) |
| Sign in to a deactivated or deleted account | "The employee code or password is wrong." |
| Open any page by its address before changing a forced password | You are sent back to **Change password** |
| New password of 7 characters | "A password needs at least 8 characters" (BR-02) |
| New password longer than 72 bytes (for example 80 letters, or about 25 Vietnamese letters with marks) | "That password is too long" |
| Repeat the new password differently | "The new passwords don't match" |
| Wrong **Current password** | "The current password is wrong" |
| New password the same as the current one | "Choose a password that's different from the current one" |
| Leave the browser idle for 30 minutes, then click a button | You are asked to sign in again (BR-05); a form left open says it expired instead of saving |

---

## 2. New employee: consent, account, face photos and Zalo link (WF 1.2.2)

**Who:** the Admin creates the account; the employee links Zalo.

**Before you start:** the employee has signed the paper consent form for face data, and you have a clear
photo of their face looking at the camera (JPEG or PNG, at least 480 × 480 pixels, at most 5 MB).

**Create the account**

1. Open **Team** and click **New account**.
2. **Employee code:** choose the **Role** first, then click **Generate** next to the code. The field
   fills with a free code for that role, such as `WRK-48213` (MGR- for a Manager, HSE- for an HSE
   officer, ADM- for an Admin, EMP- if no role is chosen). The system checks that no account, not even a
   deleted one, already has it, and draws again if one does. You can also type your own code. It is used
   to sign in and can't be changed later.
3. Type the **Full name**.
4. **Role:** Worker, Manager, HSE officer or Admin. **Group:** Site or Office (a team leader is always
   Site; HSE and the Admin are always Office). **Team:** required for a Site worker.
5. **Email** is optional. **Phone number:** the number the employee uses on Zalo, 10 digits starting
   with 0.
6. **Starting password:** at least 8 characters. Give it to the employee; they must change it at first
   sign-in.
7. **Face photo:** choose the photo (optional now, needed before they can check in at the gate).
8. Tick **The employee has signed the consent form for face data.**
9. Click **Create account**. You see "The account was created." and the account's page.

**Add face angles (optional, helps the gate camera)**

10. On the account's page, under **Extra angles**, upload a photo with the head turned left and one
    turned right, then **Save photo** for each. The status shows **Waiting for enrolment** until the
    detection app has processed it, then **Face enrolled**, or **Enrolment failed** with the reason.

**Link Zalo (the employee)**

11. The employee signs in and, on the **Dashboard**, under **Zalo notices**, clicks **Link Zalo**. (The
    card only appears when the server has the Zalo bot set up; see `docs/deployment.md`.) A
    6-digit code and a QR code appear: "The code has 6 digits and works once, for 24 hours."
12. They scan the QR with Zalo (or click **Open the bot on Zalo**), and send the code to the SteelSight
    bot. The bot answers "Your Zalo account is now linked to SteelSight (…)". After reloading, the card
    shows **Linked**.
13. Bot commands: `/lich` (this week's schedule), `/lich tuantoi` (next week), `/chamcong` (this month's
    attendance), `/chamcong thangtruoc` (last month), `/help`, and `/stop` to unlink.
14. The Admin can also make a code for someone on their account page with **New link code**.

**Try this**

| Try | Expected result |
| --- | --- |
| Click **Generate** several times | A different free code each time; the prefix follows the chosen role |
| Employee code "ab" or "WRK_01" | "Employee code: 3 to 20 characters, letters A-Z, digits and dashes." (lower case is turned into capitals) |
| An employee code someone already has (also a deleted account's) | "This employee code already exists." |
| Phone number "912345678" or "+84912345678" | "Phone number: 10 digits starting with 0." |
| A phone number another active employee has | "This phone number is used by another employee." |
| Email "ann@" | "That email address doesn't look right" |
| An email someone already has | "That employee code or email is already in use" |
| Full name of one letter | "A name must have 2 to 100 characters" |
| No role chosen | "Choose a role" |
| A Site worker with no team | "Choose the team of this site worker." |
| Leave the consent box unticked | "Confirm that the employee has signed the consent form for face data." (BR-10) |
| Starting password of 6 characters | "A password needs at least 8 characters" |
| Upload a PDF, a text file renamed to `.jpg`, or a GIF | "The photo must be JPEG or PNG, at most 5 MB and at least 480 x 480 pixels." (the type is read from the file's bytes, not its name, BR-09) |
| Upload a 300 × 300 photo | The same message |
| Upload a 6 MB photo | The browser warns "This photo is larger than 5 MB. Choose a smaller one." before sending; past the browser: "The photo must be 5 MB or smaller" |
| Upload a LEFT angle before any front photo | "Add the photo looking at the camera first" |
| Send the bot a wrong or expired code | "This code is not valid. Please ask the site office for a new code." Five wrong codes in an hour and the bot ignores that chat for a while |
| Link a Zalo account that is already linked to another active employee | "This Zalo account is already linked to another employee. Please contact the site office." (BR-14) |
| Ask for a new code while one is still valid | The old code stops working at once (BR-13) |
| Delete the account | Sign-in stops, the face photos and face data are erased, Zalo is unlinked and future shifts are cancelled (BR-07). Tick **I understand this can't be undone from the app** first, or: "Tick the box to confirm the deletion" |
| Delete or deactivate your own account, or change your own role | "You cannot delete your own account", "You cannot deactivate your own account", "You cannot change your own role" |

---

## 3. Teams and the weekly schedule (WF 1.2.3)

**Who:** the team leader plans regular shifts for their own team; the Admin plans anyone and adds
overtime.

1. Open **Shifts**. The week grid shows your team's people by day. Move with **Previous week** and
   **Next week**.
2. Click an empty cell (it says "Add a shift for … on …") or **New shift**.
3. Fill in the form: **Worker**, **Zone**, **Kind** (Regular; Overtime is for the Admin), **Frame**
   (Morning 07:00-11:30, Afternoon 13:30-17:30, Evening 18:00-22:00 overtime, or Custom), **Day**,
   **Starts** and **Ends** for a custom frame, and **Reason** for overtime.
4. Click **Add shift**. You see "The shift was added." and the shift in the grid with its hours.
5. To change a shift, click it, change the fields and click **Save shift**: "The shift was saved."
6. To cancel, open it and click **Cancel shift**, then confirm "Cancel this shift? … will be told on
   Zalo."
7. **Copy last week** copies last week's regular shifts into this week and lists any it couldn't copy
   under **Not copied:**, each with the reason (for example an overlap).
8. On **Saturday at 18:00** the system sends everyone their next week's schedule (web and Zalo). After
   that, a change to the current or next week is sent on its own (BR-18, BR-19).
9. A shift becomes **Completed** by itself when the worker checks out, or when it ends.

**Try this**

| Try | Expected result |
| --- | --- |
| A shift of 20 minutes, or 08:00 to 21:00 | "The end must be after the start and the shift must last 30 minutes to 12 hours." (BR-21) |
| A shift that overlaps another of the same worker | "This worker already has a shift at that time" with the times (BR-22) |
| As team leader, a shift for someone in another team | The worker isn't offered; sending it anyway is refused |
| As team leader, an Overtime shift | "Only the Admin plans overtime shifts" (BR-23) |
| As Admin, an Overtime shift without a reason, or a reason of 5 characters | "Write the reason for the overtime (10 to 500 characters)." |
| As team leader, add or change next week's shift after Saturday 18:00 | "The schedule of this week has been sent. Only the Admin can change it now." |
| Change or cancel a shift that has started | "This shift has already started and cannot be changed." (BR-24) |
| Change a Completed or Cancelled shift | "A finished or cancelled shift can't be changed." / "Only a scheduled shift can be changed" |
| Two people edit the same shift; the second saves | The second sees the newer version instead of overwriting it |
| A worker or HSE officer opens **Shifts** | The page isn't in their menu; opening it by address is refused |

---

## 4. Gate check-in and check-out (WF 1.2.4)

**Who:** the worker at the gate camera (CAM-01); the guard watches the gate screen; HSE fixes mistakes.

**On the site laptop (detection app, in Vietnamese)**

1. Start the app with `run.bat` (or `SteelSightAI.exe`) and sign in with the app's own account
   (**Tên đăng nhập**, **Mật khẩu**, **Đăng nhập**).
2. First time only, in **Cài đặt** (Settings): under **Đồng bộ ERP Steelsight (Supabase)** tick **Bật
   đồng bộ ERP** and fill in the database settings; add each camera with **＋ Thêm camera**, its RTSP
   address (**Địa chỉ RTSP**), its role (**Máy quét khuôn mặt** for CAM-01) and its **mã camera ERP**
   (CAM-01 to CAM-04, exactly as on the ERP's Zones page). Click **Lưu & áp dụng**.
3. Open **Màn hình cổng ↗** on the second screen at the gate.

**The check-in**

4. The worker stands in front of CAM-01 and looks at it. When the face is recognised twice about a
   second apart, the gate screen shows their name and the time, and the speaker greets them (BR-28).
5. In the ERP, the visit appears on the worker's **My attendance**, attached to their shift if it
   starts within the next 60 minutes (BR-30). If they linked Zalo, they get "Check-in at 07:52, starting
   shift T3 24/11 08:00–17:00."
6. **The check-out:** the same worker scans again at least 5 minutes later. The gate screen shows the
   check-out, the shift becomes Completed, and Zalo says "Check-out at 17:05, finishing shift …, hours
   worked 8.00."

**Fixing a visit by hand (HSE officer or Admin)**

7. Open **Attendance**, click **Add a check-in**. Choose the **Worker**, the **Zone** (optional), type
   **Checked in** and **Checked out** (leave empty if still on site), and the **Reason (10 to 500
   characters)**. Save: "The check-in was added." The worker is told.
8. To change a visit, click **Correct** on it, change the times and the reason, and click **Save
   correction**: "The check-in was corrected." The **Change history** keeps the old values and who
   changed them (BR-39).

**Try this**

| Try | Expected result |
| --- | --- |
| Scan again within 5 minutes of checking in | Ignored; the gate screen says when you can scan again (BR-29) |
| A face that isn't enrolled | Not recognised; nothing is recorded |
| Check in at 08:05 for an 08:00 shift | On time (BR-31) |
| Check in at 08:20 | Late 20 min, paid from 08:20, no make-up time (BR-31) |
| Check out at 16:30 for a 17:00 shift | Early 30 min, paid to 16:30 (BR-32) |
| A visit under 10 minutes | Too short: ignored for the shift, which counts as Absent (BR-33) |
| Add a check-in with no worker or no time | "Choose a worker" / "A check-in time is required" |
| A check-out before the check-in | "Check-out must be after check-in" |
| A reason of 5 characters | "The reason must have 10 to 500 characters" |
| A visit that overlaps another of the same person | "This person already has a visit at that time." |
| Add a check-in for someone already on site | "This worker is already checked in" |
| Two officers correct the same visit; the second saves | The second sees the newer version instead of overwriting it |
| A team leader opens **Attendance** | Only **Team attendance** (read only); recording and correcting are refused |

---

## 5. PPE check at the gate (WF 1.2.5)

**Who:** everyone entering at CAM-02; the detection app decides.

1. A person walks past CAM-02 (in or out) without a hard hat for 10 seconds, or without a vest for
   3 seconds (BR-43).
2. The speaker gives a spoken reminder at once. If the item is still missing, the app records a
   violation with an evidence photo. Both missing is one violation, "Hard hat and vest" (BR-45).
3. The person is suggested from the latest gate scan within 30 seconds (BR-46). The suggestion is never
   a decision: the violation reaches the ERP as **Pending review** with no person.
4. The same person and item aren't recorded again for 60 seconds (BR-44).

**Try this**

| Try | Expected result |
| --- | --- |
| Take the hard hat off for 5 seconds and put it back | A reminder, no violation |
| Take both off for 15 seconds | One violation of type "Hard hat and vest" |
| Stand without a vest for 2 minutes | One violation per 60 seconds at most |
| Pull the network cable, cause a violation, plug it back | The violation arrives in the ERP once the laptop is back online (see section 14) |

---

## 6. Work zone PPE monitoring and the HSE alert (WF 1.2.6)

**Who:** the detection app on CAM-03 and CAM-04; the HSE officer on duty.

1. A person in Area A without PPE is recorded as in section 5, with a face suggestion when the camera
   sees the face (BR-47).
2. The moment the violation reaches the database, every HSE officer **with a shift at that time** gets
   "Missing hard hat detected in Area A - west embankment, 09:14. Suggested: …" on the bell and on Zalo
   (BR-54). The notice opens the violation.
3. If no HSE officer is on duty, every HSE officer and every Admin is told.

**Try this**

| Try | Expected result |
| --- | --- |
| Give the HSE officer a shift covering now, cause a violation in Area A | Only that officer is told |
| No HSE shift covers now | All HSE officers and Admins are told |
| Rename Area A on **Zones**, cause another violation | The new alert uses the new name; old violations show the new name too (BR-55) |

---

## 7. Violation review and confirmation (WF 1.2.7)

**Who:** the HSE officer (or the Admin).

1. Open **PPE review**. The **Review queue** lists violations waiting for a decision, oldest first.
   Filter by zone or day if needed.
2. Open a violation. You see the evidence photo, the zone, the time, what is missing and the
   **Suggested person**.
3. Click **Take** to let the other officers know you are going to the place: "… is going".
4. To name the person, under **Confirm who it was**, choose them in **Person** (grouped as
   *Suggested*, *Team of this zone*, *On site at that time*, *Everyone*), add a **Note** if you want, and
   click **Confirm**. For someone who isn't an employee, tick **Stranger: the person is not an
   employee** instead.
5. The violation becomes **Confirmed**. A Site employee loses 5 points and is told their new score
   and how long they can appeal; an Office employee's violation becomes a **Reminder** and costs no
   points (BR-51).
6. To throw it out, under **Dismiss the violation**, choose the **Reason to dismiss** (False detection,
   Person from outside the site, Allowed to remove PPE, Other), and click **Dismiss**, then confirm
   "Dismiss this violation?".
7. A violation not reviewed by 23:00 of its day becomes **Overdue**; the Admins are reminded at 07:00
   (BR-53).

**Try this**

| Try | Expected result |
| --- | --- |
| Click **Confirm** without choosing a person or Stranger | "Choose the person, or mark the violation as Stranger." |
| Dismiss with reason Other and a 5-character explanation | "Choose the reason. For Other, write the reason (10 to 500 characters)." |
| Two officers decide the same violation; the second clicks | "This violation was already decided by another officer. Reload the page to see the decision." |
| Review a violation of a locked month | Refused: the month is closed (BR-70) |
| A worker or team leader opens **PPE review** | Not in their menu; refused by address |

---

## 8. Violation appeal (WF 1.2.8)

**Who:** the worker or team leader whose violation was confirmed; the Admin decides, with the facts
from the HSE officer.

**Appeal (the employee)**

1. Open **Violations** (or the link in the notice). Open the violation.
2. Under **Think this is wrong?**, type **Why do you disagree?** and click **Send appeal**. You see
   "Your dispute was sent to the Admin." and the appeal ticket. The violation shows **Disputed**.

**Decide (the Admin)**

3. Open **Ticket queue** (or **PPE review**, where "… appeals wait for the Admin" links to them), open the
   appeal ticket, and click **Go to the decision**.
4. To hear the HSE officer, on the ticket choose them under **Ask an HSE Officer for the facts** and
   click **Ask**. They are told, open the ticket and write **What the HSE Officer of that shift said**.
5. On the violation, under **Decide this appeal**, fill in **What the HSE Officer of that shift said**,
   the **Note to the employee (10 to 500 characters)**, and, if it was someone else, **If overturned,
   the right person**. Choose **Uphold: the violation stands** or **Overturn: the detection was wrong**
   (confirm "Overturn and give the 5 points back?").
6. The ticket closes with the outcome; the employee is told, and an overturned violation gives the
   points back.

**Try this**

| Try | Expected result |
| --- | --- |
| Appeal after the appeal period (3 working days by default) | The appeal box is gone and the page says why |
| Appeal the same violation twice | The second time the appeal box is gone |
| Appeal a Reminder (Office employee) | Not possible: reminders can't be appealed |
| Open someone else's violation by address | Refused |
| Admin decides without a note, or with a 5-character note | "Write a note for the employee (10 to 500 characters)." |
| Admin decides without the HSE facts | "Write what the HSE Officer of that shift said (10 to 1,000 characters)" |
| The Admin who sent the appeal tries to decide it | "You cannot decide your own request. Another Admin will handle it." |
| Decide a violation that isn't disputed | "Only a disputed violation can be decided" |
| Open an appeal from **New ticket** | Not offered: "To appeal a PPE violation, open it from My violations and press Send appeal." |

---

## 9. Attendance error report (WF 1.2.9)

**Who:** the employee whose visit is missing or wrong; the Admin decides.

1. Open **My attendance**. On the shift with the problem, click **Report error**.
2. The **New ticket** form opens with category **Attendance error**. Choose the **Day and shift**, the
   **Direction** ("IN: check-in missing or wrong" or "OUT: check-out missing or wrong"), the **Time at
   the gate**, and attach a **Photo** of the gate screen (required). Type a **Subject** and **Details**,
   then click **Send ticket**: "Your ticket was sent."
3. The Admin opens the ticket in the **Ticket queue**, clicks **Take**, then **Approve**. The
   **Attendance** form opens with the change; "This change answers ticket #…; saving it approves the
   ticket." Clicking **Save correction** corrects the visit and resolves the ticket. Or **Reject** with a
   note.

**Try this**

| Try | Expected result |
| --- | --- |
| No photo | "Attach a photo taken at the gate." |
| A photo that isn't an image | "The photo must be JPEG or PNG, at most 5 MB and at least 480 x 480 pixels." |
| A shift more than 2 days ago | "The period for this request has ended." |
| No direction or no time | "Choose IN or OUT" / "Enter the time you were at the gate" |
| Subject of 3 characters, or details of 5 | "A subject must have 5 to 100 characters" / "The details must have 10 to 2000 characters" |
| A second open ticket for the same shift and category | "You already have an open request for this. Open it to add more details." |
| Cancel your open ticket with **Cancel this ticket** | It shows Cancelled; a resolved one: "Only an open ticket can be cancelled" |

---

## 10. Missing check-out explanation (WF 1.2.10)

**Who:** the system flags it; the employee explains; the Admin decides.

1. A worker who checked in and didn't check out is flagged **Incomplete attendance** at 23:00 (20
   minutes after the shift end is the limit) and told: "Incomplete attendance: you checked in at … and
   did not check out. Explain it within 3 working days." The visit is closed 4 hours after the shift,
   with no guessed check-out time (BR-35).
2. The worker opens **My attendance**, clicks **Explain** on that day, and the ticket **Missing
   check-out** opens with the **Visit to explain**. They write what happened and click **Send ticket**.
3. The Admin approves (entering the real check-out on the Attendance form) or rejects.
4. Not explained in time: the day counts 0 hours and **To review**, or a **Half day** if **Company
   settings** has **Half day for unexplained missing check-out** on (BR-36).

**Try this**

| Try | Expected result |
| --- | --- |
| Explain a visit that isn't incomplete | "Only a visit with incomplete attendance can be explained" |
| Explain after 3 working days | "The period for this request has ended." |
| Check in again the next morning without having checked out | Yesterday's visit is closed by the system first, and the new check-in is recorded |

---

## 11. Pending overtime (WF 1.2.11)

**Who:** the system creates it; the team leader adds a note; the Admin decides.

1. A worker who checks out more than 20 minutes after the shift end without an overtime shift gets
   **Pending OT** for the time after the end plus 20 minutes (BR-34). It's paid at 0% until made valid.
2. The team leader sees it on **Team attendance** and adds a **Team leader's note** ("Tell the Admin who
   really worked overtime").
3. The Admin opens **Pending overtime**, and for a real one clicks **Add overtime shift**: the shift form
   opens with Kind **Overtime** and the times; type the **Reason** and save. The pending time becomes
   **Valid OT** where the shift covers it (BR-38).
4. To refuse, tick the rows and click **Cancel selected** (confirm "Cancel the selected pending overtime?
   It cannot be made valid again.").
5. Pending overtime not handled within 48 hours, or by the 2nd of next month, is cancelled by itself
   (BR-37).

**Try this**

| Try | Expected result |
| --- | --- |
| Check out 15 minutes after the shift end | Nothing pending: the 20-minute buffer (BR-34) |
| Check out 1 hour after | 40 minutes pending |
| Make a cancelled pending overtime valid | "This pending overtime was cancelled and can no longer be made valid." |
| Overtime shift for a different person | "The overtime shift must be for the same person" |
| A leader's note over 500 characters | "A note can be at most 500 characters" |
| A team leader adds a note for another team's pending overtime | "This pending overtime is not of your team" |

---

## 12. Leave and early leave request (WF 1.2.12)

**Who:** the employee asks; their team leader can comment; the Admin decides.

1. Open **My attendance** and click **Request leave** on the shift (or **Tickets** → **New ticket** with
   category **Leave request**).
2. Choose the **Day and shift**, the **Leave type** (Paid leave or Unpaid leave), **Leave from** and
   **Leave to** inside the shift (for early leave, from the time you leave to the shift end), a
   **Subject** and **Details**, and click **Send ticket**.
3. The team leader sees it under **My team's tickets** and can **Post comment** (for example to
   confirm), but can't decide.
4. The Admin opens it, clicks **Take**, then **Approve** or **Reject** with a **Note to the employee**.
   Approved leave shows as **Paid leave** or **Unpaid leave** hours, not as early leave (BR-68).

**Try this**

| Try | Expected result |
| --- | --- |
| No leave type | "Choose paid or unpaid leave" |
| Leave from 18:00 on a 08:00-17:00 shift, or an end before the start | "The leave must be inside the shift and the end must be after the start." |
| Ask after the shift ended | "A leave request must be sent before the shift ends." |
| A team leader tries to decide | No decision buttons; by address it's refused |
| A comment over 1,000 characters | "A comment can be at most 1000 characters" |

---

## 13. Monthly safety score, bonus level and report (WF 1.2.13)

**Who:** every Site employee has a score; the team leader exports their team; the Admin exports the
site.

1. Each Site employee starts the month on 100 and loses 5 points per confirmed violation (BR-57).
   **Violations** shows the score, the **Bonus level** (Level 0 full bonus; Level 1 −2% at 80 or below;
   Level 2 −4% at 70; Level 3 −6% at 60) and the confirmed violations (BR-58). A worse level is
   announced.
2. On the last working day the Admins are reminded of open tickets and HSE of overdue violations.
3. At 23:59 of the 2nd working day of the next month, the month is **locked**: scores are frozen and
   nothing in that month can change (BR-70).
4. To export, open **Monthly report**, choose the **Month** and, for the Admin, the **Scope** (Whole site
   or a team), and click **Export**. A team leader can also use **Download Excel** on **Team**. The Excel file has the
   sheets Site summary, Office summary, Violations, Attendance and Absences. Before the lock it's marked
   **Provisional**. **Earlier exports** lists every export.

**Try this**

| Try | Expected result |
| --- | --- |
| Confirm 5 violations for one worker this month | Score 75, Level 1, bonus −2% |
| Overturn one of them on appeal | Score 80 again |
| Export the current month | The file says Provisional |
| Export a month that hasn't started | "This month has not started yet." |
| A team leader exports another team | Only their own team is offered |
| A worker or HSE officer opens **Monthly report** | Not in their menu; refused by address |
| Change a shift or a visit of a locked month | Refused: the month is locked |

---

## 14. Offline queue and notification delivery (WF 1.2.14)

**Who:** the detection app and the ERP, by themselves.

1. **Offline queue.** If the laptop loses the internet, every check-in, check-out and violation waits in
   the app's local queue with its own ID. When the connection comes back they are sent oldest first, and
   a repeat is stored once.
2. **Notification delivery.** Every notice is saved in the ERP first (the bell and **Notifications**),
   then sent on Zalo to people who linked it. The Notifications page shows each one's state: **Zalo:
   waiting**, **Zalo: sent**, **Zalo: failed** or **Zalo: not linked**. A failed send is retried after
   1, 5 and 15 minutes, then marked failed (BR-16). A long text is cut and ends with a link (BR-17).
3. Click a notice to open its page; **Mark as read** or **Mark all as read**.

**Try this**

| Try | Expected result |
| --- | --- |
| Disconnect the laptop, do a check-in and a check-out, reconnect | Both appear in the ERP in order; the gate log showed them at once |
| A person who never linked Zalo gets a notice | It's on their bell with **Zalo: not linked** |
| Block the bot in Zalo, then trigger a notice | After three retries the notice shows **Zalo: failed**; the Admin's dashboard counts **Failed Zalo messages** |
| `/chamcong` for a long month | Several messages, totals first |

---

## 15. Quick reference: rules and their messages

| Rule | Limit | Message |
| --- | --- | --- |
| Employee code | 3 to 20 of A-Z, 0-9 and dash; unique for ever | "Employee code: 3 to 20 characters, letters A-Z, digits and dashes." / "This employee code already exists." |
| Password | 8 characters to 72 bytes; differs from the current one | "A password needs at least 8 characters" / "That password is too long" / "Choose a password that's different from the current one" |
| Sign-in | 5 failures from one computer or 20 for one code lock it for 15 minutes | "Too many failed sign-ins. Try again in 15 minutes." |
| Phone | 10 digits starting with 0, unique among active accounts | "Phone number: 10 digits starting with 0." / "This phone number is used by another employee." |
| Full name | 2 to 100 characters | "A name must have 2 to 100 characters" |
| Photo | JPEG or PNG by content, at least 480 × 480, at most 5 MB | "The photo must be JPEG or PNG, at most 5 MB and at least 480 x 480 pixels." |
| Zalo code | 6 digits, 24 hours, once | "This code is not valid. Please ask the site office for a new code." |
| Shift | 30 minutes to 12 hours, no overlap, locked once started | "The end must be after the start and the shift must last 30 minutes to 12 hours." |
| Overtime reason | 10 to 500 characters | "Write the reason for the overtime (10 to 500 characters)." |
| Attendance reason | 10 to 500 characters | "The reason must have 10 to 500 characters" |
| Dismiss reason Other | 10 to 500 characters | "Choose the reason. For Other, write the reason (10 to 500 characters)." |
| Ticket subject / details | 5 to 100 / 10 to 2,000 characters | "A subject must have 5 to 100 characters" / "The details must have 10 to 2000 characters" |
| Comment | at most 1,000 characters | "A comment can be at most 1000 characters" |
| HSE facts | 10 to 1,000 characters | "Write what the HSE Officer of that shift said (10 to 1,000 characters)" |
| Decision note | 10 to 500 characters | "Write a note for the employee (10 to 500 characters)." |
| Zone name | 2 to 50 characters, unique | "A zone name has 2 to 50 characters" / "A zone with this name already exists." |
| Camera code | 2 to 50 letters, digits, dots, dashes or underscores | "A camera code has 2 to 50 letters, digits, dots, dashes or underscores" |
