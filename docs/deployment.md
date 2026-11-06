# Deploying Steelsight

The ERP runs on Render from a Docker image, with its database and photos on Supabase. GitHub Actions runs the tests, builds the image from the `Dockerfile`, pushes it to GitHub's container registry, and asks Render to run it.

The image is `ghcr.io/owochiii/steelsight_erp`. Each build is tagged with its full commit ID, and the newest build from `master` is also tagged `latest`.

Never put these values in the repository. They belong in Render's environment, GitHub's secrets and your local `.env`. To start a local `.env`, copy `.env.example`, which lists every setting with notes.

## 1. Supabase

1. Create a project, in the region closest to your site.
2. Use a new database password. Don't reuse the old local one, because it's in this repository's public git history.
3. Create two **private** Storage buckets: `ticket-photos` and `face-photos`. Face photos are biometric data, so they're kept apart from ticket photos.
4. Copy the session pooler connection details from the project's **Connect** panel. Use the session pooler because Supabase's direct connection is IPv6 unless you buy its IPv4 add-on.
5. Copy the project URL and server key. If the newer secret key format is refused when uploading a photo, try the `service_role` key.

The first start of the ERP creates the schema through Flyway. After that, give the detection service its login, once, in the SQL editor:

```sql
ALTER ROLE steelsight_detection LOGIN PASSWORD '<a strong password>';
```

## 2. The image

1. Push the workflow to GitHub. To publish an image before `master` exists, open **Actions**, choose **CI**, click **Run workflow** and pick your branch. That build is tagged with the commit ID and the branch name, such as `feature-erp-backend`.
2. After the first successful run, open your GitHub profile, then **Packages**, then `steelsight_erp`, then **Package settings**, and set the visibility to **Public**. The code is public already, and the image holds no secrets, because those are only set in Render. Render can then pull it with no password.

If you'd rather keep the image private, give Render a registry credential instead: a GitHub personal access token with only the `read:packages` scope. Check that Render accepts it for `ghcr.io` before relying on it.

## 3. Render

Create a web service from an **existing image** with:

- **Image URL:** `ghcr.io/owochiii/steelsight_erp:latest`, or a branch tag such as `:feature-erp-backend` until `master` has a build
- **Region:** the one closest to your Supabase project
- **Health check path:** `/actuator/health`

| Environment variable | Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://<pooler host>:5432/postgres?sslmode=require` |
| `DB_USERNAME` | The pooler username, in the form `postgres.<project ref>` |
| `DB_PASSWORD` | The Supabase database password |
| `SUPABASE_URL` | `https://<project ref>.supabase.co` |
| `SUPABASE_SERVICE_KEY` | The Supabase server key |
| `SUPABASE_STORAGE_BUCKET` | Only if the ticket bucket isn't named `ticket-photos` |
| `SUPABASE_FACE_BUCKET` | Only if the face bucket isn't named `face-photos`. The detection service needs the same bucket name, because `face_file_path` holds only the file name |
| `STEELSIGHT_DETECTION_API_KEY` | A long random value, for example from `openssl rand -hex 32`. The detection software sends the same one. |
| `ADMIN_EMPLOYEE_CODE` | The first admin's code, or leave unset for `ADMIN` |
| `ADMIN_PASSWORD` | A strong password for the first admin |

**Database URL format:** Supabase's **Connect** panel shows a URI such as `postgresql://postgres.<ref>:[YOUR-PASSWORD]@<host>:5432/postgres`. Don't paste it into `DB_URL` as it is. The JDBC driver refuses a username and password inside the URL, and the app fails to start with `Driver org.postgresql.Driver claims to not accept jdbcUrl`. Split it into three settings:

```properties
DB_URL=jdbc:postgresql://<host>:5432/postgres?sslmode=require
DB_USERNAME=postgres.<ref>
DB_PASSWORD=<the database password, never the [YOUR-PASSWORD] placeholder>
```

The Dockerfile already turns on the `prod` profile, which sets the secure session cookie, template caching, a small connection pool and Supabase Storage. The app won't start if the Supabase settings are missing.

## 4. GitHub

1. Copy the service's **Deploy Hook** URL from Render's settings.
2. Add it as a repository secret named `RENDER_DEPLOY_HOOK`.

Pushes and pull requests run the tests. A push to `master` that passes also builds the image and tells Render to run that commit's tag. Render only accepts a tag of the same image as the service's image URL. A push to `master` fails its deploy step if the secret is missing.

## 5. Demo data

Don't load `scripts/dev-sample-data.sql` as it is, because its accounts have known passwords and this site is public. Create demo accounts as the admin, or load a copy with new passwords.

## 6. Check the live site

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
