# Steelsight: User Guide

How to use the Steelsight ERP, for each role. The labels below are the English ones. The app also has Vietnamese, switched with **EN / VI** at the top of every page.

## 1. Who can do what

| Role | What they do in Steelsight |
|---|---|
| **Admin** | Creates and changes every account and team, handles every ticket, decides PPE disputes, can plan any shift, and exports the monthly report for every worker |
| **Team leader** (Manager) | Plans shifts for their own team, sees their team's attendance, reads and comments on their team's tickets, exports their team's monthly report for accounting, and, like a worker, sees their own violations and safety score and can appeal |
| **HSE officer** | Reviews PPE violations, names the worker on unidentified ones, gives the admin the facts of a shift when asked, records check-ins by hand, and watches safety by shift |
| **Worker** | Sees their timetable, violations and safety score, disputes a wrong violation, and opens tickets such as leave requests |

Everyone signs in with an **employee code** and a password. The sidebar only shows what your role can use.

## 2. Signing in

### 2.1 The first admin
When the system starts on an empty database, it creates the first admin from two settings on the server:
- `ADMIN_EMPLOYEE_CODE`, which is `ADMIN` unless changed
- `ADMIN_PASSWORD`

To sign in as that admin:
1. Open the site. The **Sign in** page appears.
2. Enter the employee code, for example `ADMIN`, and the password from `ADMIN_PASSWORD`.
3. Click **Sign in**. You land on the **Dashboard**.

If no admin is created, `ADMIN_PASSWORD` was empty when the server started. See `docs/deployment.md`.

### 2.2 Your first sign-in after an admin sets your password
When an admin creates your account or resets your password, you must choose your own at the next sign-in:
1. Sign in with the temporary password the admin gave you.
2. The **Change password** page opens, saying an admin set your password. The menu stays hidden until you finish.
3. Enter the temporary password as **Current password**, then a new password of at least 8 characters twice.
4. Click **Change password**. The temporary password stops working.

### 2.3 Everyday use
| To | Do this |
|---|---|
| Change language | Click **EN** or **VI** at the top. The choice is kept for a year on that browser. |
| Switch light and dark mode | Click the sun or moon button at the top |
| Change your password | Click your name at the top right, then **Change password** |
| See notifications | Click the bell. The red number counts unread ones. |
| Sign out | Click your name at the top right, then **Sign out** |

### 2.4 If you can't sign in
- **"The employee code or password is wrong."** Check both. Codes are case-sensitive, for example `WRK01`.
- **"Too many failed sign-ins. Try again in 15 minutes."** Five wrong passwords lock that code from your device for 15 minutes. Wait, or ask an admin to reset your password.
- **Forgot your password:** ask an admin to reset it, as in 3.4.
- **Your account was deactivated:** only an admin can reactivate it.

## 3. Admin

### 3.1 Set up teams
Each team has one leader, and a manager leads at most one team. Create the manager accounts first, as in 3.2, then:
1. Open **Team** in the sidebar.
2. In the **Teams** panel, type a name under **New team**, choose a **Leader**, and click **Create team**.
3. To give a team a new leader, choose them in that team's row and click **Change leader**. Only managers without a team are listed.
4. To delete a team, first move its people to another team. **Delete team** only appears when a team is empty.

### 3.2 Create an account
1. Open **Team**, then click **New account**.
2. Fill in:
   - **Role:** Worker, HSE officer, Manager or Admin. Choose it first.
   - **Employee code:** used to sign in, and can't be changed later. Click **Generate** for a free code
     for the chosen role, such as `WRK-48213` (MGR-, HSE-, ADM-, or EMP- with no role). It's checked
     against every account, deleted ones too, and drawn again if taken. Or type your own: 3 to 20
     letters A-Z, digits and dashes.
   - **Full name:** 2 to 100 characters.
   - **Group:** Site or Office. A team leader is always Site; HSE and the Admin are always Office.
   - **Team:** required for a Site worker.
   - **Email:** optional. **Phone number:** the number the person uses on Zalo, 10 digits starting
     with 0, not used by another active employee.
   - **Starting password:** at least 8 characters. Give it to the person, who must change it at their
     first sign-in.
   - **Face photo:** optional now, needed for check-in at the gate. A clear photo of the face looking at
     the camera, JPEG or PNG, at least 480 × 480 pixels, at most 5 MB.
   - Tick **The employee has signed the consent form for face data.** The account can't be created
     without it.
3. Click **Create account**. The new account's page opens.

Every rule and its message, with a step-by-step walk through all the workflows, is in
[workflow-tutorial.md](workflow-tutorial.md).

### 3.3 Edit an account
Open **Team** and click **Edit** next to the person. The page has these sections:

| Section | What you can do |
|---|---|
| **Details** | Change the name, email and phone, then click **Save details** |
| **Face photo** | See the current photo and its status, upload or replace it, and click **Save photo**. Under **Extra angles**, you can also add a photo with the head turned left and one turned right |
| **Role and team** | Choose a new role and click **Change role**, or a new team and click **Change team** |
| **Reset password** | Enter a temporary password and click **Reset password**. The person must change it at their next sign-in. |
| **Account status** | **Deactivate** stops someone signing in. **Reactivate** lets them back in. |
| **Delete account** | Tick the box, then click **Delete account** |

On your own account, the role, status and delete sections are hidden, so you can't lock yourself out.

**Face photo status:**
- **No face photo:** nothing uploaded yet.
- **Waiting for enrolment:** the detection service processes it the next time it runs.
- **Face enrolled:** the gate camera can recognise this person.
- **Enrolment failed:** the reason is shown, such as no face found. Upload a clearer photo.

**Extra angles:** optional photos with the head turned about 30 to 45 degrees to each side. They help the gate camera recognise someone who isn't looking straight at it, especially a camera mounted high. Add the photo looking at the camera first. Each extra photo has its own status, and **Remove photo** deletes it and its face data. A photo taken by the gate camera itself works well as an extra angle.

**Deleting:** the person can no longer sign in, and their face photo and face data are erased. Their shifts, check-ins and tickets stay on record. A manager who still leads a team can't be deleted or given another role until the team has a new leader.

### 3.4 Handle tickets
1. Open **Ticket queue**. Every ticket comes here, whatever its category, including PPE disputes and safety concerns.
2. Use **To handle** and **Resolved** to switch between open and closed tickets.
3. Open a ticket. Under **Handle this ticket**, you can:
   - choose a person under **Assign to** and click **Assign**
   - write **What was done?** and click **Resolve ticket**
   - post a comment the person can read

You can't handle a ticket you opened yourself. Another admin handles it.

**Decide a PPE dispute:** a dispute ticket has **Go to the decision**, which opens the violation. Before deciding, ask the HSE officer who was on duty that shift what they saw. Then:
1. Read **The worker's reason**.
2. Optionally write a **Note to the worker**.
3. Click **Uphold: the violation stands** or **Overturn: the detection was wrong**. Overturning gives the worker their points back. Either way, the dispute ticket is resolved.

A leave request is only a ticket. Approving it doesn't change any shifts, so also cancel the person's shift on that day, as in 4.2. Otherwise it counts as a no-show in the monthly report.

### 3.5 Other admin pages
An admin can also use every team leader page in section 4 for any team, and the monthly report covers every worker.

### 3.6 Zalo
The Zalo bot is set up once on the server; see `deployment.md` for `ZALO_BOT_TOKEN`, `ZALO_BOT_LINK`,
`ZALO_WEBHOOK_SECRET` and `ZALO_WEBHOOK_URL`. Without them, notices stay in the web ERP only.
- To link someone who can't use the page, open **Team**, click **Edit** next to the person, and click
  **New link code**. They scan the QR code on that page with Zalo and send the 6 digits to the bot. A new
  account gets its first code automatically, so you can do this right after creating it. A new code makes the old one stop working.
- To stop Zalo for someone, click **Stop Zalo notices** on the same page.
- On **Notifications**, each notice shows its Zalo state: waiting, sent, failed (after 3 retries), or not
  linked.

## 4. Team leader (Manager)

### 4.1 Your team
Open **Team** to see the people on your team. You can't edit their details. Ask an admin for that.

### 4.2 Plan shifts
1. Open **Shifts**. The grid shows your team's people by day for one week, with their weekly hours.
2. Use **Previous week** and **Next week** to move between weeks.
3. To add a shift, click **+** in an empty cell. The form opens with that person and day filled in, set to 08:00 to 17:00. Change the times if needed, then click **Add shift**. You can also click **New shift**.
4. To change a shift, click it in the grid, then edit and click **Save shift**.
5. To call off a shift before it starts, click **Cancel shift**. This can't be undone. You don't need to finish shifts: a shift becomes **Completed** by itself when the worker checks out, or within an hour of ending if nobody checks out.

You can only plan shifts for people on your own team.

### 4.3 Late check-in rule
The rule is the same for every team and can't be changed: a check-in up to 5 minutes after the shift
starts is on time; later is late (BR-31). Late time isn't made up at the end of the shift. For example,
on an 08:00 to 17:00 shift, arriving at 08:05 is on time and paid from 08:00; arriving at 08:20 is late
and paid from 08:20.

### 4.4 Attendance
Open **Attendance** to see who on your team is on site now, and recent visits. Tags show **Check-in late**, **Check-out early**, **No check-out** or **On time**. This page is read-only for team leaders.

### 4.5 Workers
Open **Workers** to see any worker's details, safety score, this month's violations, upcoming shifts and latest check-ins. Search by name or code.

### 4.6 Monthly report for accounting
1. Open **Team**.
2. Under **Monthly report for accounting**, choose a **Month**. Last month is chosen for you.
3. Click **Download Excel**, then send the file to accounting.

The file has 4 sheets:

| Sheet | What's in it |
|---|---|
| **Summary** | One row per worker: safety score and level, violations by type, disputes awaiting a decision, shifts scheduled and worked, no-shows, hours worked, times and minutes late and early, and missing check-outs |
| **Violations** | Every violation that month, with its dispute status |
| **Attendance** | Every check-in and check-out, with late and early minutes |
| **No-shows** | Past shifts with no check-in |

Overturned violations don't count. Hours worked include estimated check-outs, which are also counted under **Missing check-outs**.

### 4.7 Your own violations and safety score
You work on site, so you have a safety score like a worker. Open **Violations** to see the violations
confirmed against you, your score and your bonus level, and appeal a wrong one, as in 6.3. You can't
see other people's violations or the review pages.

### 4.8 Tickets
Team leaders don't decide tickets. Under **My tickets**, **My team's tickets** lists the tickets your team has opened. You can open them and add a comment, for example to confirm a leave request; the admin decides. You can still open your own tickets, as in 6.4.

## 5. HSE officer

### 5.1 PPE review
Open **PPE review**. The tabs are:
- **No worker:** violations where the camera couldn't identify the person
- **Disputed:** workers who think a detection is wrong. The admin decides these
- **All**

**Name the worker on an unidentified violation:**
1. Open the violation from **No worker**.
2. Under **Who is this?**, choose the person and click **Save worker**. They can then see it and dispute it, and it counts towards their safety score.

### 5.2 Safety by shift
Open **Safety by shift**. Each row is one time slot across the site, ranked by violations per 100 worker-hours, so that large and small shifts compare fairly.

### 5.3 Attendance
Open **Attendance** to see everyone on site.
- **Add a check-in:** records a visit by hand, for example if the gate camera missed someone.
- **Correct:** fixes the times on an existing visit.

### 5.4 Alerts and safety scores
When the detection service reports too many violations in a short time, every HSE officer gets a **Too many PPE violations** notification under the bell. Open **Workers** to see every worker's safety score, lowest first.

## 6. Worker

### 6.1 Dashboard
The **Dashboard** shows your next shift, your safety score and your open tickets. If your score is low, a warning appears with the PPE you must wear: a hard hat and a PPE vest.

### 6.2 Timetable
Open **Timetable** to see your shifts for the week. Use **Previous week** and **Next week** to look back or ahead.

### 6.3 Violations, safety score and appeals
Open **Violations** to see the PPE violations confirmed against you. A camera never decides who you
are: every violation waits for an HSE officer to confirm the person, mark a stranger, or dismiss it.

**Safety score** (site staff only: workers and team leaders):
- Score = 100 minus 5 for each confirmed violation this month, never below 0. It starts again on the 1st.
- Your bonus level follows the score: above 80 is level 0 (full safety bonus), 71 to 80 is level 1
  (2% less), 61 to 70 is level 2 (4% less), 60 or below is level 3 (6% less). No salary is deducted.
- After each confirmed violation you get your new score and level.
- Office staff have no score. A confirmed violation is saved as a reminder, and you get the reminder.

**If a violation is wrong (appeal):**
1. Open it. Under **Think this is wrong?**, write why you disagree.
2. Click **Send appeal**. You can appeal once, within 3 working days of the confirmation, before the
   month is locked. The points stay deducted until the decision.
3. The Admin decides with the facts from the HSE officer of that shift. If it's overturned, you get the
   5 points back.

### 6.4 Tickets, such as leave requests
1. Open **Tickets** and click **New ticket**.
2. Choose a category. Every ticket goes to the Admin; your team leader can read it and comment.
   - **Attendance error**: the gate camera missed a scan. Add the day, shift, IN or OUT, the time you
     were at the gate, and a photo of the gate screen. Send it within 48 hours after the shift ends.
   - **Missing check-out**: explain a visit flagged *Incomplete attendance*, within 3 working days.
   - **Leave request**: paid or unpaid leave for part or all of a shift, sent before that shift ends.
   - **Other**: anything else.
3. Click **Send ticket**. You can follow it under **Tickets**, and cancel it while it's still open.

To appeal a PPE violation, use **Send appeal** on the violation itself.

### 6.5 Checking in
You check in and out by face at the gate (CAM-01), not in this app. The first scan of the day is a
check-in; a scan at least 5 minutes after it is a check-out. The gate screen shows your name and time
once it's saved. If the camera misses you, send an **Attendance error** ticket. A check-in up to 5
minutes after your shift starts counts as on time.

### 6.6 Zalo notices
Zalo is optional. Every notice is also on the bell in this app.
1. On your **Dashboard**, under **Zalo notices**, click **Link Zalo**. A 6-digit code appears. It
   works once, for 24 hours.
2. Scan the QR code next to it with Zalo (or click **Open the bot on Zalo** on your phone), and send the
   code to the SteelSight bot.
3. The bot replies that your account is linked. Reload the page: it shows **On**.

To stop, click **Stop Zalo notices**, or send `/stop` to the bot. If you can't use the page, the Admin
can make a code for you (3.6).

When the gate camera checks you in or out, the message names the shift it starts or finishes, by its day,
date and times: `Chấm công vào lúc 07:52, bắt đầu ca T3 24/11 08:00–17:00.` and `Chấm công ra lúc 17:05,
kết thúc ca T3 24/11 08:00–17:00, số giờ làm …` (your paid hours). A check-in more than 60 minutes before your shift, or
on a day with no shift, says `(không có ca)` instead. Checking out completes that shift; if you come back
in before it ends, your new check-in still goes with the same shift.

Once linked, you can also ask the bot about your own shifts. Each shift comes back as one line:

| Send | You get |
|---|---|
| `/lich` | Your shifts this week, e.g. `T4 26/11 07:00–17:00 · Khu A` |
| `/lich tuantoi` | Your shifts next week |
| `/chamcong` | This month so far: each shift with its check-in and check-out times and its status (on time, late, absent…), then the totals, your safety score and a link to My Attendance |
| `/chamcong thangtruoc` | The same for last month |
| `/help`, or anything else | The list of commands |

Capitals, spaces and Vietnamese marks don't matter, so `/Lịch tuần tới` works too. Until the month is
locked, the numbers are provisional, the same as on the My Attendance page.

## 7. Common questions

| Question | Answer |
|---|---|
| A page I need isn't in my menu | Your role can't use it. Ask an admin if your role is wrong. |
| I changed someone's role, but their menu didn't change | They need to sign out and back in. The server applies the new role straight away. |
| The report download says "You don't lead a team yet" | An admin needs to make you the leader of a team, as in 3.1 |
| A face photo stays "Waiting for enrolment" | The detection service hasn't run since the upload. Check that it's running. |
| The site is slow to open | On the free hosting plan, the site sleeps after 15 minutes without visitors and takes about a minute to wake. Signing in again may be needed. |
