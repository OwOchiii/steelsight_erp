"""Builds Report4_Software_Design_Document.md from the design documents in docs/ and the diagrams.

    python3 scripts/report4_diagrams.py     # write docs/diagrams/src/*.puml and the manifest
    scripts/render-diagrams.sh              # render docs/diagrams/png/*.png
    python3 scripts/report4_build.py        # write the report

The report follows the layout of the Report 4 template: each section leads with its figure, and each
feature of the detailed design has its class diagram with the class specification, its sequence
diagrams and its state diagrams. Figures and tables are numbered in order.
"""
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")
MANIFEST = {d["id"]: d for d in json.load(open(os.path.join(DOCS, "diagrams/manifest.json"), encoding="utf8"))}
FIGURES = [0]


def read(name):
    return open(os.path.join(DOCS, name), encoding="utf8").read()


def section(text, heading):
    """The body under a heading (matched by its start), up to the next heading of the same or a higher level."""
    m = re.search(r"^(#+) " + re.escape(heading) + r".*$", text, re.M)
    if not m:
        raise SystemExit(f"heading not found: {heading}")
    level = len(m.group(1))
    rest = text[m.end():]
    end = re.search(r"^#{1,%d} " % level, rest, re.M)
    return rest[:end.start() if end else len(rest)].strip("\n")


def no_mermaid(text):
    return re.sub(r"```mermaid\n.*?```\n?", "", text, flags=re.S)


def fig(did, title=None):
    d = MANIFEST[did]
    FIGURES[0] += 1
    caption = f"Figure {FIGURES[0]}: {title or d['title']}"
    png = f"docs/diagrams/png/{did}.png"
    return (f"![{caption}]({png})\n\n*{caption}*  \n"
            f"[Image Detail]({png}) · [PlantUML source](docs/diagrams/src/{did}.puml)\n")


def ids(prefix):
    return [i for i in MANIFEST if i.startswith(prefix)]


def demote(text, by):
    return re.sub(r"^(#+) ", lambda m: "#" * (len(m.group(1)) + by) + " ", text, flags=re.M)


def links(text):
    reps = [
        (r"\[database-design\.md\]\(database-design\.md\),? section (\d)", r"Section 2.\1"),
        (r"\[database-design\.md\]\(database-design\.md\) (\d)\.(\d)\.(\d) and (\d)\.(\d)\.(\d)", r"Sections 2.2.\2.\3 and 2.2.\5.\6"),
        (r"\[database-design\.md\]\(database-design\.md\) (\d)\.(\d)\.(\d)", r"Section 2.2.\2.\3"),
        (r"\[database-design\.md\]\(database-design\.md\) (\d)\.(\d)", r"Section 2.2.\2"),
        (r"\[security-and-report-design\.md\]\(security-and-report-design\.md\),? section 4", "Section 5.4"),
        (r"see section 4 of the security and report\ndesign", "see Section\n5.4"),
        (r"\[integration-design\.md\]\(integration-design\.md\),? section 2", "Section 4.2"),
        (r"\[tech-stack\.md\]\(tech-stack\.md\),? section 2", "Section 1.1.3"),
        (r"\[tech-stack\.md\]\(tech-stack\.md\)", "Section 1.1.2"),
        (r"\]\(detection-app-contract\.md\)", "](docs/detection-app-contract.md)"),
        (r"\]\(deployment\.md\)", "](docs/deployment.md)"),
        (r"\]\(\.\./03_implement/", "](03_implement/"),
        (r"\(3\.13\)", "(Section 3.13)"), (r"\(3\.12\)", "(Section 3.12)"), (r"\(3\.8\)", "(Section 3.8)"),
        (r"as in 3\.(\d+) below", r"as in Section 3.\1"), (r"See 3\.12", "See Section 3.12"),
    ]
    for pattern, repl in reps:
        text = re.sub(pattern, repl, text)
    return text


def number_tables(text):
    """Adds "Table N: <heading>" under every table, named after the nearest heading above it."""
    out, heading, n, in_table = [], "", 0, False
    lines = text.split("\n")
    for i, line in enumerate(lines):
        h = re.match(r"^#+ (?:[\dIVX]+(?:\.\d+)*\.?\s+)?(.*)$", line)
        if h and not line.startswith("#!"):
            heading = h.group(1).strip()
        label = re.match(r"^\*\*([^*]+)\*\*$", line)
        if label:
            heading = label.group(1).strip()
        is_row = line.startswith("|")
        out.append(line)
        if is_row:
            in_table = True
        if in_table and not is_row:
            pass
        nxt = lines[i + 1] if i + 1 < len(lines) else ""
        if is_row and not nxt.startswith("|"):
            n += 1
            out.append(f"\n*Table {n}: {heading}*")
            in_table = False
    return "\n".join(out)


# ---------------------------------------------------------------------------------------------------
system = read("system-design.md")
stack = read("tech-stack.md")
database = read("database-design.md")
detailed = read("detailed-design.md")
integration = read("integration-design.md")
security = read("security-and-report-design.md")

arch_text = section(system, "1. System Architecture")
arch_text = no_mermaid(arch_text.split("### 1.1 Monitoring")[0]).strip()
zones_text = section(system, "1.1 Monitoring zones and cameras")
erp_pkg = no_mermaid(section(system, "2.1 Web ERP")).strip()
det_pkg = section(system, "2.2 Detection app")
deploy = section(system, "3. Deployment")
matrix = section(stack, "1. Matrix")
ports = section(stack, "2. Communication protocols and ports")

LAYERS = """The web ERP is a single Spring Boot application in six layers. A request goes down the layers and
never skips one: pages never reach the database except through a service.

1. **Client layer:** the browser shows server-rendered HTML (Thymeleaf), swaps parts of a page with htmx
   and is styled with the committed Tailwind stylesheet. There is no separate frontend application.
2. **Web layer** (`web`): page controllers, form objects and helpers (`Display`, `SiteTime`, `Charts`,
   `MonthlyReportWorkbook`), and `ZaloController` for the bot's webhook. Controllers hold no business rule.
3. **Security layer** (`security`): two filter chains, one for the pages (form login by employee code,
   CSRF, sessions, the sign-in limit and the forced password change) and one for `/api/**` (API key).
4. **Business layer** (the feature packages `identity`, `scheduling`, `attendance`, `ppe`, `ticketing`,
   `notification`, `reporting`, `jobs`): services with the business rules and transactions; every
   record-level check goes through `AccessPolicy`.
5. **Persistence layer:** Spring Data JPA repositories, `JdbcTemplate` for set-based queries, and
   `PhotoStorage` for files.
6. **Data layer:** PostgreSQL 17 on Supabase, which also enforces rules with triggers, constraints, the
   `can_*()` permission functions, `safety_scores()` and row level security; and Supabase Storage."""

head = """# Report 4 – Software Design Document

**Capstone Project Report** · Steelsight: PPE monitoring and attendance for construction sites · Hanoi, Nov 2026

This is the Markdown version of `Report4_Software Design Document.docx`, written from the code after
migration V27 (25 November 2026). Every figure is PlantUML: the sources are in `docs/diagrams/src`,
generated by `scripts/report4_diagrams.py` (class diagrams take their operations and fields from the Java
source), and rendered to `docs/diagrams/png` by `scripts/render-diagrams.sh`.

## I. Record of Changes

| Date | A\\*M, D | In charge | Change Description |
| --- | --- | --- | --- |
| | | | **Version 1.0.0** |
| 30/10/2026 | A | SonLQT | Create the report from the Report 4 template. Add 1.1 System Architecture and 1.2 Package Diagrams for the web ERP and the desktop application. |
| 30/10/2026 | A | SonLQT | Add 1.3 Deployment Diagram and 1.4 Technology Stack. |
| 02/11/2026 | A | AnNV | Add 2. Database Design: ERD, table descriptions, data dictionary, local SQLite database and file storage. |
| 02/11/2026 | A | SonLQT | Add 3. Detailed Design for 15 features with class diagrams, class specifications and sequence diagrams. |
| 03/11/2026 | A | SonLQT | Add 4. Integration Design and 5. Security and Report Design. |
| 04/11/2026 | M | SonLQT | Align with Report 3 v1.1.0: Manager team scope (BR-45), ticket routing (BR-46). |
| 25/11/2026 | M | Claude Code (AI-assisted) | Rewrite every section from the code after V26 and Report 3 of 23/11/2026: feature packages; the detection app as a Windows desktop app with no vest numbers, burst alerts or licensing; 27 tables in 7 domains with zone codes; 13 features; the current routes, Zalo bot, authorization matrix and report sheets. |
| 25/11/2026 | M | Claude Code (AI-assisted) | V27: team leaders see and appeal their own violations (SRS 3.1.3). |
| 25/11/2026 | M | Claude Code (AI-assisted) | Rebuild the report around 61 PlantUML figures: architecture, layers, packages, deployment, an ERD per domain, and a class diagram, sequence diagrams and state diagrams for each feature. |

\\*A - Added M - Modified D - Deleted

## II. Software Design Document
"""

parts = [head]

# 1. System design ---------------------------------------------------------------------------------
parts.append("## 1. System Design\n\n### 1.1 System Architecture\n\n#### 1.1.1 Technology Architecture Diagram\n")
parts.append(arch_text.replace("```\n", "") + "\n\n" + fig("1-1-architecture"))
parts.append("#### 1.1.2 Technology Stack and Component Matrix\n\n" + matrix + "\n")
parts.append("#### 1.1.3 Communication Protocols and Ports\n\n" + ports + "\n")
parts.append("#### 1.1.4 Software Architecture\n\n" + LAYERS + "\n\n" + fig("1-2-layers"))
parts.append("#### 1.1.5 Monitoring Zones and Cameras\n\n" + zones_text + "\n")
parts.append("### 1.2 Package Diagram\n\n#### 1.2.1 Web ERP\n\n" + fig("1-3-erp-packages") + "\n" + erp_pkg + "\n")
parts.append("#### 1.2.2 Detection App\n\n" + fig("1-4-detection-packages") + "\n" + det_pkg + "\n")
parts.append("### 1.3 Deployment Diagram\n\n" + fig("1-5-deployment") + "\n" + deploy + "\n")

# 2. Database design -------------------------------------------------------------------------------
overall = no_mermaid(section(database, "1. Overall"))
overall = overall.replace("all 52 foreign keys are in the domain diagrams\nof Section 2.",
                          "all 52 foreign keys are in the domain diagrams\nof Section 2.2.")
parts.append("## 2. Database Design\n\n### 2.1 Overall\n\n" + overall.split("\n\nThe overview shows")[0] + "\n\n"
             + fig("2-0-domains") + "\nThe overview shows" + overall.split("\n\nThe overview shows")[1] + "\n")
parts.append("### 2.2 Data Dictionary\n")
dictionary = section(database, "2. Data Dictionary")
for n, block in enumerate(re.split(r"\n(?=### 2\.\d )", "\n" + dictionary)[1:], 1):
    title = re.match(r"### 2\.\d (.+)", block).group(1).strip()
    body = block.split("\n", 1)[1]
    body = re.sub(r"```mermaid\n.*?```\n?", fig(f"2-{n}-erd", f"Entity relationship diagram: {title}") + "\n", body, count=1, flags=re.S)
    body = re.sub(r"^#### 2\.\d\.(\d+) ", lambda m: f"##### 2.2.{n}.{m.group(1)} ", body, flags=re.M)
    parts.append(f"#### 2.2.{n} {title}\n" + body.rstrip() + "\n")
parts.append("### 2.3 Functions, Triggers and Row Level Security\n\n" + section(database, "3. Functions, Triggers") + "\n")
parts.append("""### 2.4 Local Outbox of the Detection App

The detection app keeps every event in a local SQLite database (through SQLAlchemy) before sending it.
Each event has a UUID `event_id` made on the laptop. The same value is stored in `check_in.event_id` or
`violation.event_id`, which are unique, so an event sent twice after a network error is stored once
(`ON CONFLICT (event_id) DO NOTHING`). Events are sent oldest first, so a check-out never arrives before
its check-in, and an evidence photo is uploaded before its violation row (see Section 3.13.2).

### 2.5 File Storage Design

Photos are files in three private Supabase Storage buckets, and tables store only their path:
`face-photos` (written by the ERP, Admin only), `ticket-photos` (written by the ERP for the ticket's
author) and `evidence-photos` (written by the detection app). Browsers never get a Storage URL; each
photo goes through an ERP route that checks the permission first. Section 4.3 lists which column refers
to which bucket and the route that serves it.
""")
parts.append("### 2.6 Differences from the SRS Data Model\n\n" + section(database, "4. Differences from the SRS") + "\n")

# 3. Detailed design -------------------------------------------------------------------------------
intro = detailed.split("\n## 3.1 ")[0]
features_table = intro[intro.index("| # | Feature"):].split("\n\n")[0]
parts.append("## 3. Detailed Design\n\nThis section gives the detailed design of the 13 features of the web ERP and the detection app. "
             "Each feature has a class diagram with its class specification, its sequence diagrams and, where its data "
             "has a lifecycle, its state diagrams. In the class diagrams, colours show the stereotype: blue for "
             "controllers, green for services, yellow for repositories, grey for entities, purple for components and "
             "interfaces, red for database objects, cyan for the detection app's Python classes. A dashed arrow is a "
             "dependency, a solid line an association, a dashed arrow with a hollow head an implementation. In the "
             "sequence diagrams, solid arrows are calls, dashed arrows are returns, and alt, opt and loop frames show "
             "conditions.\n\n" + features_table + "\n")

for n in range(1, 14):
    sec = f"3.{n}"
    body = section(detailed, f"{sec} ")
    title = re.search(r"^## 3\.%d (.+)$" % n, detailed, re.M).group(1).strip()
    body = re.sub(r" Diagrams: the\s+`STEELSIGHT-DET-\*` TDS listed at the top\.", " Its own design is in the\ndetection repository's `03_implement/STEELSIGHT-DET-*` documents.", body)
    paragraphs = [p for p in body.split("\n\n") if not p.startswith("Diagrams:")]
    spec, rest = None, []
    for p in paragraphs:
        if p.startswith("| Class | Stereotype |") and spec is None:
            spec = p
        else:
            rest.append(p)
    parts.append(f"### {sec} {title}\n\n" + "\n\n".join(rest).strip() + "\n")
    parts.append(f"#### {sec}.1 Class Diagram\n\n" + fig(f"3-{n}-class"))
    if spec:
        parts.append(f"**Class specifications of {title}**\n\n" + spec + "\n")
    seqs = ids(f"3-{n}-seq-")
    if seqs:
        parts.append(f"#### {sec}.2 Sequence Diagrams\n")
        for k, s in enumerate(seqs, 1):
            parts.append(f"##### {sec}.2.{k} {MANIFEST[s]['title']}\n\n" + fig(s))
    states = ids(f"3-{n}-state-")
    if states:
        parts.append(f"#### {sec}.3 State Diagrams\n")
        for k, s in enumerate(states, 1):
            parts.append(f"##### {sec}.3.{k} {MANIFEST[s]['title']}\n\n" + fig(s))

# 4. Integration design ----------------------------------------------------------------------------
int_intro = integration.split("\n## 1.")[0].split("\n", 1)[1].strip()
int_intro = re.sub(r"^This is section 4 of the design \(Report 4\)\.\s*", "", int_intro)
parts.append("## 4. Integration Design\n\n" + int_intro + "\n\n" + fig("4-1-integration"))
parts.append(demote(re.sub(r"^## (\d)\. ", r"## 4.\1 ", integration[integration.index("\n## 1."):], flags=re.M).strip(), 1) + "\n")

# 5. Security and report design --------------------------------------------------------------------
sec_body = security[security.index("\n## 1."):]
sec_body = re.sub(r"^## (\d)\. ", r"## 5.\1 ", sec_body, flags=re.M)
sec_body = sec_body.replace("\n## 5.3 ", "\n" + fig("5-1-authz") + "\n## 5.3 ", 1)
parts.append("## 5. Security and Report Design\n\nHow people sign in, what each role may do, how personal data is protected, "
             "and what the monthly report contains.\n" + demote(sec_body, 1) + "\n")

report = links("\n".join(parts))
report = number_tables(report)
report = re.sub(r"\n{3,}", "\n\n", report)
out = os.path.join(ROOT, "Report4_Software_Design_Document.md")
open(out, "w", encoding="utf8").write(report)

# A copy with every image inside it (base64), for viewers that don't have docs/diagrams/png beside the file.
import base64


def inline(m):
    path = os.path.join(ROOT, m.group(2))
    if not os.path.exists(path):
        return m.group(0)
    data = base64.b64encode(open(path, "rb").read()).decode("ascii")
    return f"![{m.group(1)}](data:image/png;base64,{data})"


embedded = re.sub(r"!\[([^\]]*)\]\((docs/diagrams/png/[^)]+)\)", inline, report)
open(os.path.join(ROOT, "Report4_Software_Design_Document.embedded.md"), "w", encoding="utf8").write(embedded)
print(f"{FIGURES[0]} figures (also embedded in Report4_Software_Design_Document.embedded.md), {len(re.findall(r'^\*Table \d+:', report, re.M))} tables, {len(report.splitlines())} lines -> {os.path.relpath(out, ROOT)}")
