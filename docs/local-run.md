# Steelsight: Run It Locally

A local copy of the ERP with its own database and sample data, for trying the workflows in
[workflow-tutorial.md](workflow-tutorial.md) without touching Supabase, Render or Zalo.

## 1. What you need

- **Docker Desktop**, running, with WSL integration turned on if you use WSL (Settings → Resources →
  WSL integration). Check with `docker version`.
- **curl** and **bash** (both come with WSL, Linux and macOS; on Windows without WSL, use Git Bash).
- Free ports **8080** (the ERP) and **55434** (the database). The older `steelsight-db` container on
  55433, if you have one, is left alone.

Nothing else: the ERP is built inside Docker, so no Java or Maven is needed.

## 2. Start it

From the project folder:

```bash
scripts/local.sh up      # builds the ERP and starts it with a fresh database (first time: a few minutes)
scripts/local.sh seed    # loads the sample people and work
```

Open <http://localhost:8080> and sign in. Every account below uses the same password, **Admin12345**
(set `LOCAL_ADMIN_PASSWORD` before `up` to choose another).

| Employee code | Role | What to try with it |
| --- | --- | --- |
| `ADMIN`, `ADM02` | Admin | Accounts (with **Generate**), teams, zones, the ticket queue, appeals, pending overtime, the monthly report, company settings. Two Admins, so one can decide what the other sent |
| `HSE01` | HSE officer, on duty 07:00 to 17:00 | The Review Queue (2 pending, 1 overdue), confirming and dismissing, the HSE facts asked on an appeal, recording and correcting visits, the violation alerts on the bell |
| `HSE02` | HSE officer, no shifts | The same pages; gets alerts only when nobody is on duty |
| `MGR01` | Team leader of Team A (Area A) | The shift grid, Team attendance, the team's leave request with a comment, their own violations and score, the team report |
| `MGR02` | Team leader of Team B (Area B) | Pending overtime of WRK05 and its note |
| `WRK01` | Worker, Team A | A leave request waiting; on site today |
| `WRK02` | Worker, Team A | Late yesterday; score 90 with an appeal waiting; an attendance error ticket |
| `WRK03` | Worker, Team A | Left early yesterday; four confirmed violations: score 80, bonus level 1 |
| `WRK04` | Worker, Team B | Incomplete attendance yesterday, with its explanation ticket |
| `WRK05` | Worker, Team B | Pending overtime yesterday; a visit typed in by HSE two days ago; an approved request |
| `WRK06` | Worker, deactivated | Can't sign in: "The employee code or password is wrong." |
| `WRK07` | New joiner | Sent to **Change password** at first sign-in; no face photo yet |
| `OFC01` | Office worker | A violation saved as a reminder; no safety score |

The site matches the tutorial's practice site: zones GATE-IN, GATE-PPE, AREA-A (Team A) and AREA-B
(Team B), cameras CAM-01 to CAM-04, shifts from three days ago to four days ahead. Dates count from
the day you load the data, so it always looks current.

## 3. Everyday commands

```bash
scripts/local.sh logs    # follow the ERP's log (Ctrl+C to stop following)
scripts/local.sh psql    # a SQL prompt on the local database
scripts/local.sh stop    # stop, keeping the data; "up" starts it again
scripts/local.sh reset   # delete everything; then "up" and "seed" start from nothing
```

After changing the code, run `scripts/local.sh up` again: it rebuilds the image and Flyway applies any
new migration to the local database.

## 4. What is different from production

| Part | Locally |
| --- | --- |
| Database | PostgreSQL 17 with pgvector in the `steelsight-local-db` container, on port 55434 |
| Photos | Kept on the container's disk (`/tmp/steelsight-uploads`); they are gone after `reset` or a rebuild. The sample violations have no photo files, so their evidence photo doesn't show |
| Zalo | Off: no token is set, so the Dashboard has no **Zalo notices** card and every notice is saved in the ERP with **Zalo: not linked**. To try the bot locally you need a public address for the webhook (for example a Cloudflare Tunnel) and the `ZALO_*` settings of `docs/deployment.md` |
| Scheduled jobs | On, in Vietnam time: the 23:00, 07:00, hourly and month jobs run while the container is up |
| Cookies | Not marked Secure, because the site is plain `http://localhost` |
| Detection app | Not part of it; see section 5 to connect it |

## 5. Optional: connect the detection app

To see real check-ins and violations from a camera or webcam in the local ERP:

1. Let the detection role sign in to the local database (pick any password; this is local only):

   ```bash
   scripts/local.sh psql
   ALTER ROLE steelsight_detection LOGIN PASSWORD 'detect-local';
   \q
   ```

2. In the detection app's `.env`, point it at the local database instead of Supabase:

   ```
   STEELSIGHT_DB_URL=postgresql://steelsight_detection:detect-local@localhost:55434/steelsight_local?sslmode=disable
   ```

3. In the app's **Cài đặt**, tick **Bật đồng bộ ERP**, give the attendance camera the code `CAM-01`, and
   upload a face photo for an account in the ERP (**Team** → the person → **Edit**). The app enrolls it,
   and a scan at CAM-01 shows up on that person's **My attendance**.

Put the Supabase settings back in the detection app's `.env` when you're done.

## 6. If something goes wrong

| Problem | Fix |
| --- | --- |
| `docker: command not found` in WSL | Turn on WSL integration in Docker Desktop and open a new terminal |
| `port is already allocated` | Another program uses 8080 or 55434. Stop it, or change the left-hand port in `compose.local.yaml` |
| `The container name ... is already in use` | An older copy exists: `docker rm -f steelsight-local-db steelsight-local-erp`, then `up` |
| "The ERP did not start in 3 minutes" | `scripts/local.sh logs` shows why; a failed migration names the file and the line |
| "The sample data is already loaded" | It loads once. `scripts/local.sh reset`, `up` and `seed` to start again |
| A page says the form expired | You were away for 30 minutes; sign in again |
