# Deploying Steelsight

The ERP runs on Render from the `Dockerfile`, with its database and ticket photos on Supabase. GitHub Actions runs the tests, then asks Render to deploy.

Never put these values in the repository. They belong in Render's environment, GitHub's secrets and your local `.env`.

## 1. Supabase

1. Create a project, in the region closest to your site.
2. Use a new database password. Don't reuse the old local one, because it's in this repository's public git history.
3. Create a **private** Storage bucket named `ticket-photos`.
4. Copy the session pooler connection details from the project's **Connect** panel. Use the session pooler because Supabase's direct connection is IPv6 unless you buy its IPv4 add-on.
5. Copy the project URL and server key. If the newer secret key format is refused when uploading a photo, try the `service_role` key.

The first start of the ERP creates the schema through Flyway. After that, give the detection service its login, once, in the SQL editor:

```sql
ALTER ROLE steelsight_detection LOGIN PASSWORD '<a strong password>';
```

## 2. Render

Create a web service from this repository with:

- **Runtime:** Docker
- **Region:** the one closest to your Supabase project
- **Health check path:** `/actuator/health`
- **Auto-deploy:** off, so that failed tests stop a release

| Environment variable | Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://<pooler host>:5432/postgres?sslmode=require` |
| `DB_USERNAME` | The pooler username, in the form `postgres.<project ref>` |
| `DB_PASSWORD` | The Supabase database password |
| `SUPABASE_URL` | `https://<project ref>.supabase.co` |
| `SUPABASE_SERVICE_KEY` | The Supabase server key |
| `SUPABASE_STORAGE_BUCKET` | Only if the bucket isn't named `ticket-photos` |
| `STEELSIGHT_DETECTION_API_KEY` | A long random value, for example from `openssl rand -hex 32`. The detection software sends the same one. |
| `ADMIN_EMPLOYEE_CODE` | The first admin's code, or leave unset for `ADMIN` |
| `ADMIN_PASSWORD` | A strong password for the first admin |

The Dockerfile already turns on the `prod` profile, which sets the secure session cookie, template caching, a small connection pool and Supabase Storage. The app won't start if the Supabase settings are missing.

## 3. GitHub

1. Copy the service's **Deploy Hook** URL from Render's settings.
2. Add it as a repository secret named `RENDER_DEPLOY_HOOK`.

Pushes and pull requests run the tests. A push to `master` that passes also deploys. A push to `master` fails its deploy step if the secret is missing.

## 4. Demo data

Don't load `scripts/dev-sample-data.sql` as it is, because its accounts have known passwords and this site is public. Create demo accounts as the admin, or load a copy with new passwords.

## 5. Check the live site

- `/actuator/health` shows `UP`, and sign-in works.
- The session cookie is marked `Secure`.
- A ticket photo still opens after restarting the service.
- Five wrong passwords lock that sign-in, and the message appears.
- The detection API refuses a wrong `X-Api-Key`.
- The detection role can sign in through the pooler, and can't read `password_hash`.

## Free-tier limits

- Render's free service sleeps after 15 minutes idle and takes at least a minute to wake, so open the site a few minutes before a demo. Sleeping also signs everyone out, because sessions are kept in memory.
- Supabase may pause a free project that stays idle, so check it before a demo too.
- If Render is too slow, run the same image on the laptop and expose it with `cloudflared tunnel --url http://localhost:8080`.
