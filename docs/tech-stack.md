# Steelsight: Technology Stack and Component Matrix

Versions are the ones the build uses today, from `pom.xml`, `package.json` and the committed files. A dash means the version isn't recorded in this repository, because that part lives in the detection service or is a hosted service. Items marked **planned** are in the architecture diagram but not built yet.

GitLab CI/CD is the project's pipeline. While the code is still on GitHub, GitHub Actions runs the tests, builds the image into GitHub's container registry and deploys it. The GitLab stages are set up when the repository moves.

## 1. Matrix

| Layer | Component/Tool | Tech/Version | Description | Purpose |
| --- | --- | --- | --- | --- |
| Frontend | Page templates | Thymeleaf 3.1.3 | HTML pages rendered on the server, with no separate frontend app | Keeps the frontend inside the Spring Boot app, so there's one thing to build and deploy |
| Frontend | Security in templates | thymeleaf-extras-springsecurity6 3.1.3 | Shows or hides links and buttons by the signed-in user's permissions | Users only see the actions they're allowed to take |
| Frontend | Partial page updates | htmx 2.0.7, htmx-spring-boot-thymeleaf 5.1.0 | Links and forms swap the page content instead of reloading the whole page | Removes the blink and full reload on every click and form submit |
| Frontend | Styling | Tailwind CSS 4.1.13 | Utility CSS, built once into `static/css/app.css` and committed | Responsive layout, Odoo-style look, and light and dark themes |
| Frontend | Icons | Lucide (lucide-static 0.544.0) | One SVG sprite built by `scripts/build-icons.mjs` | Consistent icons for the sidebar, bell and buttons |
| Frontend | Page script | Plain JavaScript (`static/js/app.js`) | Theme toggle, mobile sidebar and closing the account menu | Small interactions with no framework |
| Frontend | Languages | Spring `MessageSource`, `messages.properties` and `messages_vi.properties` | Every label in English and Vietnamese; the choice is kept in a cookie for a year | Supports Vietnamese and English speakers |
| Frontend | Dashboard charts | Server-side bars (`web/Charts.java`) with CSS | Bars are worked out in Java and drawn with HTML and CSS | Informative dashboard with no chart library |
| Backend | Language and runtime | Java 17 (Eclipse Temurin in Docker) | The language the ERP is written in | Long-term support version the team knows |
| Backend | Application framework | Spring Boot 4.0.0 (Spring Framework 7.0.1) | Web app, configuration and dependency injection | The base of the ERP |
| Backend | Web layer | Spring Web MVC 7.0.1 on embedded Tomcat 11.0.14 | Page controllers and the REST alert API | Serves pages to browsers and JSON to the detection service |
| Backend | Security | Spring Security 7.0.0, BCrypt | Sign-in by employee code, permission and scope checks, CSRF, an API key for `/api/**` | Each role only sees and changes what it's allowed to |
| Backend | Sign-in limit | Caffeine 3.2.3 | In-memory count of failed sign-ins | Slows password guessing on the public site |
| Backend | Data access | Spring Data JPA, Hibernate 7.1.8 | Entities and repositories over PostgreSQL | Reads and writes data without hand-written SQL for common cases |
| Backend | Connection pool | HikariCP 7.0.2 | Reuses database connections | Stays within Supabase's connection limit |
| Backend | Schema migrations | Flyway 11.14.1 | Versioned SQL migrations `V1` to `V9`, plus row level security after each run | Only the ERP changes the schema, in a repeatable order |
| Backend | JDBC driver | PostgreSQL JDBC 42.7.8 | Connects Java to PostgreSQL | Database access |
| Backend | JSON | Jackson 3.0.2 | Reads and writes JSON | Request and response bodies for the alert API |
| Backend | Health check | Spring Boot Actuator 4.0.0 | Only `/actuator/health` is public | Render checks that a new release started before switching to it |
| Backend | Logging | SLF4J with Logback 1.5.21 | Request, error and sign-in logs, all sharing a request ID | Find what broke and for whom |
| Backend | Photo storage | Local disk or Supabase Storage REST API | `PhotoStorage` with a local version for development and a Supabase version for production, keeping ticket and face photos apart | Photos survive restarts on Render |
| Backend | Excel export | fastexcel 0.18.4 | Writes the monthly report as an `.xlsx` file by streaming, with sheets for the summary, violations, attendance and no-shows | Team leaders send safety and attendance figures to accounting for pay and bonuses |
| Backend | Boilerplate | Lombok 1.18.42 | Generates getters, setters and constructors at compile time | Shorter entity and service code |
| Database | Relational database | PostgreSQL 17 | 20 tables in 6 domains, with triggers and `can_*` permission functions | The shared record for the ERP and the detection service |
| Database | Vector extension | pgvector | Stores face embeddings | Face matching at the gate |
| Database | Range extension | btree_gist | Supports an exclusion constraint | Stops a worker having overlapping shifts |
| Database | Row level security | PostgreSQL RLS | On for every table, with policies only for the detection role | Blocks Supabase's public Data API, and limits what detection can read and write |
| Detection | Language | Python | The detection service, run in Docker on the demo laptop | Runs the computer vision models |
| Detection | PPE detection | YOLO26 | Finds hard hats, vests, gloves and harnesses in camera frames | Detects PPE violations |
| Detection | Vest ID reading | EasyOCR | Reads the number printed on a vest | Links a violation to the worker wearing that vest |
| Detection | Face matching | InsightFace | Matches faces at the gate camera | Check-in by face |
| Detection | Database access | psycopg | Writes check-ins, vest links and violations through the session pooler | Sends detection results to the shared database |
| Detection | Local outbox | - | Holds events while the internet is down | Nothing is lost when the laptop is offline |
| Detection | Cameras | RTSP | 1 gate camera and 3 zone cameras | Video input |
| Hosting | ERP hosting | Render free web service | Runs the ERP container behind Render's HTTPS proxy | Public site with HTTPS and nothing to install |
| Hosting | Container image | Docker, multi-stage `Dockerfile` | Builds with the JDK and runs on the JRE as a non-root user, sized for 512 MB | The same image runs on Render and the laptop |
| Hosting | Production settings | `application-prod.yaml` | Secure cookie, template cache, small connection pool, Supabase Storage | Turned on by the Dockerfile, so production can't miss them |
| Hosting | Database hosting | Supabase, session pooler | Managed PostgreSQL over TLS and IPv4 | Reachable from both Render and the laptop |
| Hosting | File hosting | Supabase Storage, private buckets | Ticket photos and face photos in separate buckets, with snapshots planned | Durable file storage that Render's disk can't provide |
| Hosting | Detection hosting | Demo laptop, Docker Desktop on WSL2 with the NVIDIA driver | Started by hand before a demo | The only machine with a GPU |
| Hosting | Demo backup | Cloudflare Tunnel (`cloudflared`) | Exposes the ERP running on the laptop | Fallback if Render is too slow on demo day |
| CI/CD | Pipeline | GitLab CI/CD with a shared runner | Test, scan, build and deploy stages; merge requests stop after the scan | Only tested and scanned code reaches the live site |
| CI/CD | Code scan | Semgrep, in GitLab CI/CD | Static analysis of the code | Finds security bugs before merge |
| CI/CD | Dependency scan | Trivy, in GitLab CI/CD | Checks dependencies and the image for known vulnerabilities | Blocks known vulnerable libraries |
| CI/CD | Image registry | GitLab Container Registry | Stores the ERP image tagged by commit | Render pulls the exact image that was tested |
| CI/CD | Deploy | Render deploy hook, called by GitLab CI/CD from `main` | Tells Render which image to run | Releases happen only from the main branch |
| CI/CD | Testing and images, for now | GitHub Actions | Runs `mvn verify` on pushes and pull requests. From `master`, or by hand, it also builds the image and calls Render's deploy hook with that commit's tag | Keeps tests and releases running until the move to GitLab |
| CI/CD | Image registry, for now | GitHub Container Registry (`ghcr.io/owochiii/steelsight_erp`) | Holds the ERP image, tagged by commit, by branch, and `latest` for `master` | Render pulls the exact image that passed the tests |
| Testing | Test framework | JUnit 6.0.1, AssertJ 3.27.6, Hamcrest 3.0, Mockito 5.20.0 | Unit and integration tests | Check behaviour before every release |
| Testing | Real database in tests | Testcontainers 2.0.2 with `pgvector/pgvector:pg17` | Starts a real PostgreSQL for the tests | Tests the triggers, constraints and functions that an in-memory database can't run |
| Testing | Excel checks | fastexcel-reader 0.18.4 | Reads the exported report back in tests | Checks the figures accounting will see |
| Testing | Web tests | Spring MockMvc, Spring Security Test | Calls pages and the API as each role | Checks permissions and pages without a browser |
| Build | Java build | Maven 3.10.0 through the Maven Wrapper | Compiles, tests and packages the ERP | Same build on every machine and in CI |
| Build | Frontend build | Node.js with the Tailwind CLI | Builds the stylesheet and icon sprite, whose output is committed | Only needed when styles change, not to build the app |
| Integrations | Email, planned | Email API over HTTPS | Password reset and notices | Render blocks SMTP ports, so email has to go over HTTPS |

## 2. Communication protocols and ports

| Protocol | Port | Source -> Destination | Description |
| --- | --- | --- | --- |
| HTTPS | 443 | Browser -> Render proxy | Users open the ERP; Render holds the TLS certificate for the onrender.com address |
| HTTP | Render's `PORT` (8080 locally) | Render proxy -> ERP | Render ends TLS and forwards the request to the container; the app listens on whatever port Render gives it |
| PostgreSQL over TLS (JDBC) | 5432 | ERP -> Supabase session pooler | The ERP's database connection, as the table owner |
| PostgreSQL over TLS (psycopg) | 5432 | Detection service -> Supabase session pooler | Writes check-ins, vest links and violations, as the limited detection role |
| HTTPS (REST) | 443 | ERP -> Supabase Storage | Uploads, reads and deletes ticket photos and face photos with the server key |
| HTTPS (REST) | 443 | Detection service -> ERP | `POST /api/v1/detection/alerts` with an `X-Api-Key` header, through Render's proxy |
| HTTPS (REST) | 443 | Detection service -> Supabase Storage | Uploads violation and check-in snapshots, downloads enrolment photos |
| RTSP | 554 (camera default) | Cameras -> Detection service | Video from the gate camera and the 3 zone cameras, on the site network |
| HTTPS | 443 | Browser -> ERP -> Supabase Storage | Photos go through the ERP, which checks permission before fetching them |
| HTTP | Render's `PORT` | Render -> ERP `/actuator/health` | Render checks inside its network that a new release started, before switching traffic to it |
| HTTPS | 443 | GitLab CI/CD -> Render deploy hook | Starts a deploy after the pipeline passes on `main` |
| HTTPS | 443 | Render -> GitHub Container Registry | Render pulls the ERP image tagged by commit; GitLab Container Registry after the move |
| HTTPS | 443 | Developer or laptop -> GitLab | `git pull` and `git push`; the laptop only connects outwards, with no open ports |
| HTTPS | 443 | GitHub Actions runner -> GitHub | Runs the tests on each push and pull request while the code is on GitHub |
| HTTPS | 443 | ERP -> Email API | **Planned:** password reset and notices; Render blocks SMTP ports |
| QUIC or HTTP/2 (Cloudflare Tunnel) | 7844 outbound | Laptop -> Cloudflare | **Demo backup only:** exposes the ERP running on the laptop with no open ports |
| PostgreSQL | 55433 | ERP -> local Docker database | **Development only:** the `steelsight-db` container on the developer's machine |
| PostgreSQL | Random | Tests -> Testcontainers database | **Tests only:** a throwaway pgvector database for each test run |
