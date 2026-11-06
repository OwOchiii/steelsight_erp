# Steelsight: User Guide

How to use the Steelsight ERP, for each role. The labels below are the English ones. The app also has Vietnamese, switched with **EN / VI** at the top of every page.

## 1. Who can do what

| Role | What they do in Steelsight |
|---|---|
| **Admin** | Creates and changes every account and team, handles every non-safety ticket such as leave requests, can plan any shift, and exports the monthly report for every worker |
| **Team leader** (Manager) | Plans shifts for their own team, sees their team's attendance, sets the team's late check-in rule, and exports their team's monthly report for accounting |
| **HSE officer** | Reviews PPE violations, names the worker on unidentified ones, decides disputes, handles safety tickets, records check-ins by hand, and watches safety by shift |
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
   - **Employee code:** used to sign in, such as `WRK07`, using letters, numbers, dots, dashes and underscores. It can't be changed later.
   - **Full name**
   - **Role:** Worker, HSE officer, Manager or Admin
   - **Team:** optional. Workers usually belong to one.
   - **Email** and **Phone number:** optional
   - **Starting password:** at least 8 characters. Give it to the person, who must change it at their first sign-in.
   - **Face photo:** optional, for check-in at the gate. Use a clear photo of the face looking at the camera, as JPEG, PNG or WebP, up to 5 MB.
3. Click **Create account**. The new account's page opens.

### 3.3 Edit an account
Open **Team** and click **Edit** next to the person. The page has these sections:

| Section | What you can do |
|---|---|
| **Details** | Change the name, email and phone, then click **Save details** |
| **Face photo** | See the current photo and its status, upload or replace it, and click **Save photo** |
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

**Deleting:** the person can no longer sign in, and their face photo and face data are erased. Their shifts, check-ins and tickets stay on record. A manager who still leads a team can't be deleted or given another role until the team has a new leader.

### 3.4 Handle tickets
1. Open **Ticket queue**. It shows admin's categories: Leave request, Shift or timetable issue, Equipment request, and Account or access.
2. Use **To handle** and **Resolved** to switch between open and closed tickets. Click **All tickets** to also see HSE's safety tickets.
3. Open a ticket. Under **Handle this ticket**, you can:
   - choose a person under **Assign to** and click **Assign**
   - write **What was done?** and click **Resolve ticket**
   - post a comment the person can read

A leave request is only a ticket. Approving it doesn't change any shifts, so also cancel the person's shift on that day, as in 4.2. Otherwise it counts as a no-show in the monthly report.

### 3.5 Other admin pages
An admin can also use every team leader page in section 4 for any team, and the monthly report covers every worker.

## 4. Team leader (Manager)

### 4.1 Your team
Open **Team** to see the people on your team. You can't edit their details. Ask an admin for that.

### 4.2 Plan shifts
1. Open **Shifts**. The grid shows your team's people by day for one week, with their weekly hours.
2. Use **Previous week** and **Next week** to move between weeks.
3. To add a shift, click **+** in an empty cell. The form opens with that person and day filled in, set to 08:00 to 17:00. Change the times if needed, then click **Add shift**. You can also click **New shift**.
4. To change a shift, click it in the grid, then edit and click **Save shift**.
5. To end a shift early, click **Cancel shift**. After it's worked, click **Mark completed**. Neither can be undone.

You can only plan shifts for people on your own team.

### 4.3 Late check-in rule
On **Team**, under **Late check-in rule**, set how many minutes after a shift starts a check-in still counts as on time, then click **Save**. The default is 15.

Late time is made up at the end of the shift, up to that many minutes. For example, on an 08:00 to 17:00 shift with the 15-minute rule, arriving at 08:10 means working until 17:10. Arriving after 08:15 is tagged late, with the work time ending at 17:15.

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

### 4.7 Tickets
Team leaders don't handle tickets. You can still open your own tickets, as in 6.4.

## 5. HSE officer

### 5.1 PPE review
Open **PPE review**. The tabs are:
- **Disputed:** workers who think a detection is wrong
- **No worker:** violations where the camera couldn't identify the person
- **All**

**Name the worker on an unidentified violation:**
1. Open the violation from **No worker**.
2. Under **Who is this?**, choose the person and click **Save worker**. They can then see it and dispute it, and it counts towards their safety score.

**Decide a dispute:**
1. Open the violation from **Disputed** and read **The worker's reason**.
2. Optionally write a **Note to the worker**.
3. Click **Uphold: the violation stands** or **Overturn: the detection was wrong**. Overturning gives the worker their points back. Either way, the dispute ticket is resolved.

### 5.2 Safety tickets
Open **Ticket queue**. HSE sees PPE disputes and safety concerns, such as broken equipment or a hazard, and no other tickets. Assign, comment on and resolve them as in 3.4. A PPE dispute ticket is resolved by deciding the violation, through **Go to the decision**.

### 5.3 Safety by shift
Open **Safety by shift**. Each row is one time slot across the site, ranked by violations per 100 worker-hours, so that large and small shifts compare fairly.

### 5.4 Attendance
Open **Attendance** to see everyone on site.
- **Add a check-in:** records a visit by hand, for example if the gate camera missed someone.
- **Correct:** fixes the times on an existing visit.

### 5.5 Alerts and safety scores
When the detection service reports too many violations in a short time, every HSE officer gets a **Too many PPE violations** notification under the bell. Open **Workers** to see every worker's safety score, lowest first.

## 6. Worker

### 6.1 Dashboard
The **Dashboard** shows your next shift, your safety score and your open tickets. If your score is low, a warning appears with the PPE you must wear: a hard hat and a PPE vest.

### 6.2 Timetable
Open **Timetable** to see your shifts for the week. Use **Previous week** and **Next week** to look back or ahead.

### 6.3 Violations, safety score and disputes
Open **Violations** to see every PPE violation recorded against you.

**Safety score:**
- It starts at 100 each month and goes back to 100 on the 1st.
- Each violation lowers it, and repeats within 30 minutes count once.
- At 70 or below you get a **Safety warning**.
- At 40 or below you get a **Safety danger notice**, which also warns that your pay may be deducted under company policy.

**If a violation is wrong:**
1. Open it.
2. Under **Think this is wrong?**, write why you disagree.
3. Click **Send dispute**. HSE reviews it. If they overturn it, you get your points back.

### 6.4 Tickets, such as leave requests
1. Click **Report a problem** on the Dashboard, or open **Tickets** and click **New ticket**.
2. Choose a category:
   - **Leave request**, **Shift or timetable issue**, **Equipment request** or **Account or access** go to admin.
   - **Safety concern** goes to HSE.
3. Write a subject and the details. Optionally choose a zone and attach a photo, as JPEG, PNG or WebP up to 5 MB.
4. Click **Send ticket**. You can follow its progress and comments under **Tickets**.

To contest a PPE violation, use **Send dispute** on the violation itself, not a ticket.

### 6.5 Checking in
Workers check in by face at the gate camera, not in this app. If the camera misses you, an HSE officer can record the visit by hand.

## 7. Common questions

| Question | Answer |
|---|---|
| A page I need isn't in my menu | Your role can't use it. Ask an admin if your role is wrong. |
| I changed someone's role, but their menu didn't change | They need to sign out and back in. The server applies the new role straight away. |
| The report download says "You don't lead a team yet" | An admin needs to make you the leader of a team, as in 3.1 |
| A face photo stays "Waiting for enrolment" | The detection service hasn't run since the upload. Check that it's running. |
| The site is slow to open | On the free hosting plan, the site sleeps after 15 minutes without visitors and takes about a minute to wake. Signing in again may be needed. |
