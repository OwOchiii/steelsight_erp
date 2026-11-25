"""Writes the PlantUML sources of the Report 4 diagrams into docs/diagrams/src, with a manifest.

    python3 scripts/report4_diagrams.py          # write the .puml files and docs/diagrams/manifest.json
    scripts/render-diagrams.sh                   # render them to docs/diagrams/png

Class diagrams take their operations' parameters and return types, and the entities' fields, from the
Java source; if an operation named here no longer exists, the script stops instead of drawing a stale
diagram. The entity-relationship diagrams are built from the field tables of docs/database-design.md.
Sequence, state, architecture, package and deployment diagrams are written here from the code paths.
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/orochi/steelsight")
OUT = os.path.join(ROOT, "docs/diagrams/src")
MANIFEST = os.path.join(ROOT, "docs/diagrams/manifest.json")

STYLE = """skinparam shadowing false
skinparam defaultFontName Arial
skinparam defaultFontSize 12
skinparam dpi 130
skinparam roundCorner 6
skinparam ArrowColor #37474F
skinparam titleFontSize 15
skinparam titleFontStyle bold
"""
CLASS_STYLE = STYLE + """skinparam classAttributeIconSize 0
skinparam class {
  BackgroundColor #FFFFFF
  BorderColor #546E7A
  HeaderBackgroundColor #ECEFF1
}
skinparam class<<Controller>> {
  BackgroundColor #E3F2FD
  BorderColor #1565C0
}
skinparam class<<Service>> {
  BackgroundColor #E8F5E9
  BorderColor #2E7D32
}
skinparam class<<Repository>> {
  BackgroundColor #FFF8E1
  BorderColor #F9A825
}
skinparam class<<Entity>> {
  BackgroundColor #F5F5F5
  BorderColor #616161
}
skinparam class<<Component>> {
  BackgroundColor #F3E5F5
  BorderColor #6A1B9A
}
skinparam class<<Interface>> {
  BackgroundColor #FFFFFF
  BorderColor #6A1B9A
}
skinparam class<<Database>> {
  BackgroundColor #FFEBEE
  BorderColor #C62828
}
skinparam class<<Python>> {
  BackgroundColor #E0F7FA
  BorderColor #00838F
}
hide empty members
"""
SEQ_STYLE = STYLE + """skinparam sequence {
  ParticipantBackgroundColor #E8F5E9
  ParticipantBorderColor #2E7D32
  ActorBackgroundColor #E3F2FD
  ActorBorderColor #1565C0
  LifeLineBorderColor #90A4AE
  DatabaseBackgroundColor #FFEBEE
  DatabaseBorderColor #C62828
  GroupBorderColor #78909C
  GroupBackgroundColor #FAFAFA
}
skinparam responseMessageBelowArrow true
autonumber
"""
STATE_STYLE = STYLE + """skinparam state {
  BackgroundColor #E8F5E9
  BorderColor #2E7D32
  ArrowColor #2E7D32
  StartColor #263238
  EndColor #263238
}
"""
ARCH_STYLE = STYLE + """skinparam rectangle {
  BorderColor #546E7A
}
skinparam package {
  BorderColor #546E7A
}
skinparam node {
  BackgroundColor #FAFAFA
  BorderColor #546E7A
}
skinparam database {
  BackgroundColor #FFEBEE
  BorderColor #C62828
}
"""

# ---------------------------------------------------------------------------------------------------
# Java source: operations and fields
# ---------------------------------------------------------------------------------------------------
_SRC = {}


def _source(rel):
    if rel not in _SRC:
        text = open(os.path.join(JAVA, rel + ".java"), encoding="utf8").read()
        text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        _SRC[rel] = re.sub(r"//[^\n]*", "", text)
    return _SRC[rel]


def _params(body, start):
    depth = 0
    for j in range(start, len(body)):
        if body[j] == "(":
            depth += 1
        elif body[j] == ")":
            depth -= 1
            if depth == 0:
                return body[start + 1:j]
    return ""


def _names(params):
    params = re.sub(r"@\w+(\.\w+)*\s*(\((?:[^()]|\([^()]*\))*\))?", "", params)
    parts, depth, cur = [], 0, ""
    for c in params:
        depth += c == "<"
        depth -= c == ">"
        if c == "," and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += c
    parts.append(cur)
    return [p.split()[-1] for p in (x.strip() for x in parts) if p]


def _short(t):
    t = re.sub(r"\s+", " ", t).strip()
    t = re.sub(r"\b(java\.util\.|java\.time\.|orochi\.steelsight\.[\w.]+\.)", "", t)
    return t.replace("<", "~<").replace("~~<", "~<")


def methods(rel):
    """Every method of a class or interface, as (name, signature)."""
    body = _source(rel)
    found = []
    pattern = r"\n\s+(?:public\s+|default\s+)?(?:static\s+)?(?:<[^>]+>\s+)?([\w<>\[\], .?]+?)\s+(\w+)\s*\("
    for m in re.finditer(pattern, body):
        ret, name = m.group(1).strip(), m.group(2)
        if not ret or ret.split()[-1] in ("new", "return", "throw", "else") or ret in ("class", "record", "interface", "enum"):
            continue
        if re.search(r"\b(private|protected)\s+$", body[max(0, m.start() - 20):m.start() + 1]):
            continue
        line = body[m.start():m.end()]
        if "private " in line or "protected " in line:
            continue
        found.append((name, f"{name}({', '.join(_names(_params(body, m.end() - 1)))}): {_short(ret)}"))
    return found


def fields(rel):
    body = _source(rel)
    out = []
    for m in re.finditer(r"\n\s+private\s+([\w<>, ]+?)\s+(\w+)\s*(=[^;]*)?;", body):
        t, f = m.group(1), m.group(2)
        if t.startswith("static") or "final" in t:
            continue
        out.append(f"-{f}: {_short(t)}")
    return out


def safe(name):
    """A PlantUML alias: names with spaces or symbols become letters, digits and underscores."""
    return re.sub(r"\W", "_", name)


def cls(stereo, name, rel=None, ops=(), flds=None, extra=(), alias=None):
    """A class box. ops are method names looked up in the source ("name#2" picks the second overload)."""
    lines = []
    if rel and flds is not None:
        all_fields = fields(rel)
        if flds == "all":
            lines += all_fields
        else:
            keep = {f"-{f}" for f in flds}
            chosen = [f for f in all_fields if f.split(":")[0] in keep]
            missing = keep - {f.split(":")[0] for f in chosen}
            if missing:
                sys.exit(f"{rel}: no field {sorted(missing)}")
            lines += chosen
    if ops:
        if lines:
            lines.append("--")
        available = methods(rel)
        for op in ops:
            nth = 1
            if "#" in op:
                op, nth = op.split("#")
                nth = int(nth)
            matches = [s for n, s in available if n == op]
            if len(matches) < nth:
                sys.exit(f"{rel}: no operation {op} (overload {nth})")
            lines.append("+" + matches[nth - 1])
    lines += list(extra)
    lines = [line.replace("\n", "\\n") for line in lines]
    kind = "interface" if stereo == "Interface" else "class"
    head = f'{kind} "{name}" as {alias or safe(name)} <<{stereo}>>'
    return head + " {\n  " + "\n  ".join(lines) + "\n}" if lines else head


# ---------------------------------------------------------------------------------------------------
# Database design: field tables to entity-relationship diagrams
# ---------------------------------------------------------------------------------------------------
def read_schema():
    text = open(os.path.join(ROOT, "docs/database-design.md"), encoding="utf8").read()
    domains, tables, order = {}, {}, []
    current = None
    for block in re.split(r"\n(?=#{3,4} )", text):
        head = block.split("\n", 1)[0]
        m = re.match(r"### 2\.(\d) (.+)", head)
        if m:
            current = m.group(2).strip()
            domains[current] = []
            order.append(current)
            continue
        m = re.match(r"#### 2\.\d\.\d+ (\w+)", head)
        if m and current:
            table = m.group(1)
            cols = []
            for row in re.findall(r"^\| \d+ \|(.+)\|\s*$", block, re.M):
                c = [x.strip() for x in row.split("|")]
                if len(c) < 6:
                    continue
                name, dtype, _size, key, nullable, unique = c[0], c[1], c[2], c[3], c[4], c[5]
                fk = re.search(r"FK → (\w+)\((\w+)\)", key)
                cols.append(dict(name=name, type=dtype, pk="PK" in key, fk=fk.group(1) if fk else None,
                                 null=nullable == "Yes", unique=unique in ("Yes",)))
            tables[table] = cols
            domains[current].append(table)
    return order, domains, tables


def entity(table, cols, stub=False):
    lines = []
    keys = [c for c in cols if c["pk"]]
    rest = [c for c in cols if not c["pk"]]
    for c in keys:
        lines.append(f'* {c["name"]} : {c["type"]} <<PK{", FK" if c["fk"] else ""}>>')
    if not stub:
        lines.append("--")
        for c in rest:
            mark = "" if c["null"] else "* "
            tag = " <<FK>>" if c["fk"] else ""
            lines.append(f'{mark}{c["name"]} : {c["type"]}{tag}')
    return f"entity {table} {{\n  " + "\n  ".join(lines) + "\n}"


def erd(domain, tables_in, tables):
    shown = set(tables_in)
    body, links, stubs = [], [], set()
    for t in tables_in:
        body.append(entity(t, tables[t]))
        for c in tables[t]:
            if not c["fk"]:
                continue
            parent = c["fk"]
            if parent not in shown:
                stubs.add(parent)
            left = "|o" if c["null"] else "||"
            right = "o|" if c["unique"] else "o{"
            links.append(f'{parent} {left}--{right} {t} : {c["name"]}')
    for s in sorted(stubs):
        body.append(entity(s, [c for c in tables[s] if c["pk"]], stub=True))
    return f"@startuml\n{STYLE}skinparam linetype ortho\nhide circle\ntitle {domain}\n" + "\n".join(body) + "\n" + "\n".join(links) + "\n@enduml\n"


DOMAIN_COLOURS = ["#E3F2FD", "#E8F5E9", "#FFF8E1", "#FFEBEE", "#F3E5F5", "#E0F7FA", "#ECEFF1"]


def full_erd(order, domains, tables):
    """Every table and every foreign key in one diagram. Each table lists its keys only (the full field
    lists are in the domain diagrams); tables are coloured by domain, with a legend."""
    body, links = [], []
    for i, d in enumerate(order):
        colour = DOMAIN_COLOURS[i % len(DOMAIN_COLOURS)]
        for t in domains[d]:
            cols = tables[t]
            lines = []
            for c in cols:
                if c["pk"]:
                    lines.append(f'* {c["name"]} <<PK{", FK" if c["fk"] else ""}>>')
            fks = [c for c in cols if c["fk"] and not c["pk"]]
            if fks:
                lines.append("--")
                for c in fks:
                    lines.append(f'{"" if c["null"] else "* "}{c["name"]} <<FK>>')
            body.append(f"entity {t} {colour} {{\n  " + "\n  ".join(lines) + "\n}")
            for c in cols:
                if c["fk"]:
                    left = "|o" if c["null"] else "||"
                    right = "o|" if c["unique"] else "o{"
                    links.append(f'{c["fk"]} {left}--{right} {t}')
    legend = ["skinparam legendBackgroundColor #FFFFFF", "legend right", "|= Colour |= Domain |"]
    for i, d in enumerate(order):
        legend.append(f"|<{DOMAIN_COLOURS[i % len(DOMAIN_COLOURS)]}>          | {d} |")
    legend.append("endlegend")
    return (f"@startuml\n{STYLE}skinparam linetype ortho\nskinparam nodesep 40\nskinparam ranksep 70\nhide circle\n"
            f"title Entity relationship diagram of the whole database\n"
            + "\n".join(body) + "\n" + "\n".join(links) + "\n" + "\n".join(legend) + "\n@enduml\n")


def domain_overview(order, domains, tables):
    """One box per domain, listing its tables; one arrow per pair of domains, with its foreign key count."""
    colours = ["#E3F2FD", "#E8F5E9", "#FFF8E1", "#FFEBEE", "#F3E5F5", "#E0F7FA", "#ECEFF1"]
    where = {t: d for d in order for t in domains[d]}
    alias = {d: f"D{i}" for i, d in enumerate(order)}
    lines = [f"@startuml\n{ARCH_STYLE}title Database domains and the links between them"]
    for i, d in enumerate(order):
        names = "\\n".join(domains[d])
        lines.append(f'rectangle "<b>{d}</b>\\n----\\n{names}" as {alias[d]} {colours[i % len(colours)]}')
    counts = {}
    for t, cols in tables.items():
        for c in cols:
            if c["fk"] and where.get(c["fk"]) and where.get(t) and where[c["fk"]] != where[t]:
                key = (where[t], where[c["fk"]])
                counts[key] = counts.get(key, 0) + 1
    for (child, parent), n in sorted(counts.items(), key=lambda kv: (order.index(kv[0][0]), order.index(kv[0][1]))):
        lines.append(f"{alias[child]} ..> {alias[parent]} : {n} FK{'s' if n > 1 else ''}")
    lines.append("@enduml")
    return "\n".join(lines) + "\n"


# ---------------------------------------------------------------------------------------------------
# Diagrams
# ---------------------------------------------------------------------------------------------------
DIAGRAMS = []


def add(did, title, kind, body, section):
    DIAGRAMS.append(dict(id=did, title=title, kind=kind, section=section, body=body))


def classdiagram(title, parts, relations):
    names = re.findall(r'^(?:class|interface) "([^"]+)"', "\n".join(parts), re.M)
    fixed = []
    for r in relations:
        for n in names:
            if safe(n) != n:
                r = r.replace(f'"{n}"', safe(n))
        fixed.append(r.replace("\n", "\\n"))
    relations = fixed
    return f"@startuml\n{CLASS_STYLE}title {title}\n" + "\n\n".join(parts) + "\n\n" + "\n".join(relations) + "\n@enduml\n"


def seq(title, text):
    return f"@startuml\n{SEQ_STYLE}title {title}\n{text.strip()}\n@enduml\n"


def state(title, text):
    return f"@startuml\n{STATE_STYLE}title {title}\nhide empty description\n{text.strip()}\n@enduml\n"


def arch(title, text):
    return f"@startuml\n{ARCH_STYLE}title {title}\n{text.strip()}\n@enduml\n"


# ===== 1. System design =============================================================================
add("1-1-architecture", "Technology architecture", "architecture", arch("Steelsight: technology architecture", r'''
left to right direction
actor "Worker, Team Leader,\nHSE Officer, Admin" as users
actor "Guard at the gate" as guard
node "Browser\n(phone or desktop)" as browser
node "Zalo app\n(phone)" as zaloapp
cloud "Render (free web service)" as render {
  node "Render HTTPS proxy\n(TLS, onrender.com)" as proxy
  node "Docker: web ERP\nJava 17 JRE, non-root" as erpc {
    rectangle "Spring Boot 4\nSpring MVC + Security\nThymeleaf + htmx\nTailwind CSS" as web
    rectangle "Services and\nscheduled jobs\n(JobScheduler)" as services
    rectangle "Spring Data JPA\nFlyway V1-V27" as data
  }
}
cloud "Supabase" as supa {
  database "PostgreSQL 17\npgvector, btree_gist\nRLS on every table\n27 tables, triggers" as pg
  storage "Storage (private buckets)\nface-photos\nticket-photos\nevidence-photos" as st
}
cloud "Zalo Bot Platform" as zbp
node "Site laptop (Windows, NVIDIA GPU)" as laptop {
  rectangle "Detection app (Python)\npywebview + FastAPI on 127.0.0.1" as det {
    rectangle "YOLO26\n(TensorRT / ONNX)" as yolo
    rectangle "InsightFace\nbuffalo_l" as face
    rectangle "PPE rules,\ngate scanner,\nvoice (edge-tts)" as rules
    database "SQLite\noutbox + local log" as outbox
    rectangle "ErpSync\n(psycopg)" as sync
  }
}
node "Cameras (RTSP 554)\nCAM-01 attendance\nCAM-02 gate PPE\nCAM-03, CAM-04 work areas" as cams
node "Gate screen\n+ speaker" as screen
node "GitHub Actions\n(GitLab CI later)\nmvn verify, image" as ci
node "Container registry\nghcr.io" as reg

users --> browser
browser --> proxy : HTTPS 443
proxy --> web : HTTP $PORT
web --> services
services --> data
data --> pg : JDBC/TLS 5432\nsession pooler\n(table owner)
services --> st : HTTPS REST\nserver key
services --> zbp : sendMessage,\nsetWebhook
zbp --> web : POST /zalo/webhook\n(secret header)
zbp --> zaloapp
cams --> yolo : RTSP
cams --> face
yolo --> rules
face --> rules
rules --> outbox
outbox --> sync
sync --> pg : psycopg/TLS 5432\nsteelsight_detection role
sync --> st : evidence photos up,\nenrolment photos down
rules --> screen
guard --> screen
ci --> reg : push image
reg --> render : deploy hook,\npull image
'''), "1.1")

add("1-2-layers", "Layered software architecture of the web ERP", "architecture", arch("Web ERP: layered architecture", r'''
skinparam linetype ortho
rectangle "1. Client layer" as L1 #E3F2FD {
  rectangle "Browser: server-rendered pages\n(Thymeleaf HTML, htmx swaps, Tailwind CSS)" as b
}
rectangle "2. Web layer (package web, api)" as L2 #E8F5E9 {
  rectangle "Page controllers\nTeam, Shift, Attendance, Violation,\nTicket, Report, Notification, AdminSettings" as pc
  rectangle "ZaloController\n(/zalo/webhook)" as zc
  rectangle "Forms, Display,\nSiteTime, Charts" as fh
}
rectangle "3. Security layer (package security)" as L3 #F3E5F5 {
  rectangle "SecurityConfig: page chain\n(form login, CSRF, sessions)\nand /api/** chain (API key)" as sc
  rectangle "SignInLimitFilter,\nPasswordChangeRequiredFilter,\nAppUserDetailsService" as sf
}
rectangle "4. Business layer (feature services)" as L4 #FFF8E1 {
  rectangle "identity\nUserService, AccessPolicy,\nEmployeeCodes" as s1
  rectangle "scheduling\nTeamService, ZoneService,\nShiftService" as s2
  rectangle "attendance\nCheckInService, AttendanceRules,\nPendingOvertimeService" as s3
  rectangle "ppe\nViolationService,\nSafetyScoreService" as s4
  rectangle "ticketing\nTicketService" as s5
  rectangle "notification\nNotificationService,\nZaloDispatcher, ZaloLinks" as s6
  rectangle "reporting\nMonthlyReportService" as s7
  rectangle "jobs\nJobScheduler, SiteJobs" as s8
}
rectangle "5. Persistence layer" as L5 #ECEFF1 {
  rectangle "Spring Data JPA repositories\nand JdbcTemplate queries" as repo
  rectangle "PhotoStorage\n(Local / Supabase)" as ps
}
rectangle "6. Data layer" as L6 #FFEBEE {
  database "PostgreSQL 17 (Supabase)\ntables, triggers, can_*() functions,\nsafety_scores(), RLS" as db
  storage "Supabase Storage\nprivate buckets" as bucket
}
b --> sc
sc --> pc
pc --> s1
pc --> s2
pc --> s3
pc --> s4
pc --> s5
pc --> s7
zc --> s6
s8 --> s3
s8 --> s6
s1 --> repo
s3 --> repo
s4 --> repo
s5 --> repo
s1 --> ps
repo --> db
ps --> bucket
'''), "1.1")

add("1-3-erp-packages", "Package diagram of the web ERP", "package", arch("Web ERP: package diagram (orochi.steelsight)", r'''
skinparam linetype ortho
package web #E3F2FD
package security #F3E5F5
package jobs #FFF8E1
package reporting #FFF8E1
package ticketing #FFF8E1
package attendance #FFF8E1
package ppe #FFF8E1
package scheduling #FFF8E1
package notification #FFF8E1
package identity #FFF8E1
package calendar #ECEFF1
package storage #ECEFF1
package "model, repository,\nexception, validation, config" as base #ECEFF1
web ..> security
web ..> reporting
web ..> ticketing
web ..> attendance
web ..> ppe
web ..> scheduling
web ..> notification
web ..> identity
web ..> storage
security ..> identity
jobs ..> attendance
jobs ..> notification
jobs ..> scheduling
jobs ..> calendar
reporting ..> attendance
reporting ..> ppe
ticketing ..> attendance
ticketing ..> ppe
ticketing ..> notification
attendance ..> ticketing
attendance ..> ppe
attendance ..> scheduling
attendance ..> notification
ppe ..> scheduling
ppe ..> notification
ppe ..> calendar
scheduling ..> ppe
scheduling ..> notification
scheduling ..> identity
notification ..> attendance
notification ..> identity
identity ..> attendance
identity ..> storage
identity ..> base
attendance ..> calendar
note bottom of base
  Every feature package also uses these.
end note
'''), "1.2")

add("1-4-detection-packages", "Package diagram of the detection app", "package", arch("Detection app: package diagram (steelsight)", r'''
skinparam linetype ortho
package "engine.py" as engine #E0F7FA
package web #E3F2FD
package vision #E0F7FA
package people #E0F7FA
package gate #E0F7FA
package safety #E0F7FA
package records #E0F7FA
package erp #FFF8E1
package core #ECEFF1
web ..> engine : polls frames\nand events
engine ..> vision : capture,\ndetector, tracker
engine ..> people : faces
engine ..> gate : scanner
engine ..> safety : PPE rules
engine ..> records : local log,\nevidence
gate ..> erp : outbox events
safety ..> erp : outbox events
erp ..> people : face gallery,\nenrolment
erp ..> core
vision ..> core
records ..> core
note right of erp
  outbox.py, sync.py (ErpSync),
  mapping.py: writes to Supabase
  as steelsight_detection
end note
'''), "1.2")

add("1-5-deployment", "Deployment diagram", "deployment", arch("Deployment and CI/CD", r'''
left to right direction
node "Developer machine" as dev {
  artifact "git repository" as repo
  artifact "scripts/local.sh\n(compose.local.yaml)" as local
}
node "GitHub (GitLab planned)" as gh {
  component "Actions: mvn verify\n(Testcontainers)" as test
  component "Build image\nfrom master" as build
  artifact "ghcr.io/owochiii/\nsteelsight_erp:<sha>" as image
}
node "Render free web service" as render {
  node "Container (512 MB)" as cont {
    artifact "app.jar\nSPRING_PROFILES_ACTIVE=prod" as jar
  }
}
node "Supabase" as supa {
  database "PostgreSQL 17" as pg
  storage "Storage buckets" as st
}
node "Uptime monitor" as up
node "Site laptop (Windows)" as laptop {
  artifact "SteelSightAI.exe\n(setup.bat, run.bat)" as exe
  database "SQLite outbox" as sqlite
}
node "Cameras CAM-01..04" as cams
node "Zalo Bot Platform" as zalo
repo --> test : push
test --> build : on master
build --> image
image --> jar : deploy hook,\npull
jar --> pg : Flyway at start-up,\nJDBC/TLS
jar --> st : HTTPS
jar <--> zalo : HTTPS
up --> jar : GET /actuator/health\nevery 5 min
cams --> exe : RTSP
exe --> sqlite
exe --> pg : psycopg/TLS
exe --> st : HTTPS
local ..> jar : same image,\nlocal database
'''), "1.3")

# ===== 2. Database design ===========================================================================
ORDER, DOMAINS, TABLES = read_schema()
add("2-0-domains", "Database domains and the links between them", "erd", domain_overview(ORDER, DOMAINS, TABLES), "2.1")
add("2-0-full-erd", "Entity relationship diagram of the whole database", "erd", full_erd(ORDER, DOMAINS, TABLES), "2.1")
for i, d in enumerate(ORDER, 1):
    add(f"2-{i}-erd", d, "erd", erd(d, DOMAINS[d], TABLES), f"2.2.{i}")

# ===== 3. Detailed design ===========================================================================
# 3.1 Sign-in and session
add("3-1-class", "Class diagram for Sign-in and Session", "class", classdiagram("Sign-in and Session", [
    cls("Controller", "AuthController", "web/AuthController", ["login"]),
    cls("Controller", "AccountController", "web/AccountController", ["passwordForm", "changePassword"]),
    cls("Component", "SecurityConfig", extra=["+apiFilterChain(http, limiter, apiKey): SecurityFilterChain",
                                              "+securityFilterChain(http, signInLimiter, users, sessionRegistry): SecurityFilterChain"]),
    cls("Component", "SignInLimitFilter", extra=["#doFilterInternal(request, response, chain): void"]),
    cls("Component", "SignInLimiter", extra=["~isLocked(code, address): boolean", "~recordFailure(code, address): void",
                                             "~recordSuccess(code, address): void", "-failures: Cache~<String, Integer>",
                                             "FAILURES_PER_CLIENT = 5", "FAILURES_PER_CODE = 20", "LOCK = 15 min"]),
    cls("Service", "AppUserDetailsService", "security/AppUserDetailsService", ["loadUserByUsername"]),
    cls("Component", "FirstSignInHandler", "security/FirstSignInHandler", ["onAuthenticationSuccess"]),
    cls("Component", "PasswordChangeRequiredFilter", extra=["PAGE = \"/account/password\"", "#doFilterInternal(request, response, chain): void"]),
    cls("Component", "UserSessions", "security/UserSessions", ["endAll", "endOthers"]),
    cls("Service", "UserService", "identity/service/UserService", ["changeOwnPassword", "resetPassword"]),
    cls("Component", "AuthenticatedUser", "security/AuthenticatedUser", ["can"]),
    cls("Entity", "AppUser", "identity/model/AppUser", flds=["employeeCode", "passwordHash", "isActive", "mustChangePassword", "role", "version"]),
], [
    "SecurityConfig ..> SignInLimitFilter", "SecurityConfig ..> PasswordChangeRequiredFilter",
    "SecurityConfig ..> FirstSignInHandler", "SignInLimitFilter --> SignInLimiter",
    "AppUserDetailsService ..> AuthenticatedUser : creates", "AppUserDetailsService ..> AppUser : reads",
    "AccountController --> UserService", "AccountController --> UserSessions",
    "FirstSignInHandler ..> AppUser", "UserService ..> AppUser",
]), "3.1")
add("3-1-seq-signin", "Sequence diagram for Sign in", "sequence", seq("Sign in", r'''
actor User
participant "Browser" as B
participant SignInLimitFilter as F
participant SignInLimiter as L
participant "Spring Security\nform login" as S
participant AppUserDetailsService as D
participant FirstSignInHandler as H
database PostgreSQL as DB
User -> B : employee code, password, Sign in
B -> F : POST /login (CSRF token)
F -> L : isLocked(code, address)
alt 5 failures from this client or 20 for this code in 15 min
  L --> F : true
  F --> B : redirect /login?locked
  B --> User : "Too many failed sign-ins. Try again in 15 minutes."
else not locked
  F -> S : continue
  S -> D : loadUserByUsername(code)
  D -> DB : active, not deleted account with its role's permissions
  DB --> D : row
  D --> S : AuthenticatedUser (authorities = permission codes)
  S -> S : BCrypt.matches(password, hash)
  alt wrong code or password
    S -> L : recordFailure(code, address)
    S --> B : redirect /login?error
    B --> User : "The employee code or password is wrong."
  else right password
    S -> L : recordSuccess(code, address)
    S -> H : onAuthenticationSuccess
    H -> DB : current password hash
    alt must_change_password
      H --> B : redirect /account/password
    else
      H --> B : redirect to the page asked for, or /
    end
  end
end
'''), "3.1")
add("3-1-seq-password", "Sequence diagram for Change password", "sequence", seq("Change password (forced or by choice)", r'''
actor User
participant Browser as B
participant PasswordChangeRequiredFilter as P
participant AccountController as C
participant UserService as U
participant UserSessions as S
database PostgreSQL as DB
User -> B : opens any page
B -> P : GET /any
alt session flagged "must change password"
  P --> B : redirect /account/password
end
User -> B : current, new and repeated password, Change password
B -> C : POST /account/password
C -> C : new == repeated?
alt no
  C --> B : "The new passwords don't match"
else
  C -> U : changeOwnPassword(actorId, current, new)
  U -> DB : read account
  alt current password wrong
    U --> C : BusinessRuleException "The current password is wrong"
  else under 8 characters, over 72 bytes, or the same as now
    U --> C : BusinessRuleException (rule message)
  else valid
    U -> DB : password_hash = BCrypt(new), must_change_password = false
    C -> S : endOthers(userId, this session)
    C --> B : redirect / with "Your password was changed."
  end
end
'''), "3.1")
add("3-1-state-account", "Account lifecycle", "state", state("Account lifecycle", r'''
[*] --> MustChangePassword : Admin creates the account\n(starting password)
MustChangePassword --> Active : the person chooses\ntheir own password
Active --> MustChangePassword : Admin resets the password
Active --> Deactivated : Admin deactivates\n(sessions ended)
Deactivated --> Active
note right of Deactivated
  Admin reactivates
  to return to Active
end note
MustChangePassword --> Deactivated : Admin deactivates
Active --> Deleted : Admin deletes\n(face data erased, Zalo unlinked,\nfuture shifts cancelled)
Deactivated --> Deleted : Admin deletes
Deleted --> [*]
state Active {
  [*] --> SignedOut
  SignedOut --> SignedIn : right password
  SignedIn --> SignedOut : sign out, or 30 min idle
  SignedOut --> LockedOut : 5 failures (client)\nor 20 (code)
  LockedOut --> SignedOut : 15 minutes pass
}
'''), "3.1")

# 3.2 Accounts, face photos and Zalo link
add("3-2-class", "Class diagram for Accounts, Face Photos and Zalo Link", "class", classdiagram("Accounts, Face Photos and Zalo Link", [
    cls("Controller", "TeamController", "web/TeamController", ["newAccount", "suggestCode", "create", "update", "changeRole", "changeTeam", "deactivate", "newZaloCode"]),
    cls("Controller", "ZaloController", "web/ZaloController", ["link", "unlink", "webhook"]),
    cls("Service", "UserService", "identity/service/UserService", ["create#2", "updateProfile", "changeRole", "assignTeam", "changeGroup",
                                                                   "changeFacePhoto", "changeFaceAngle", "removeFaceAngle", "resetPassword", "setActive", "softDelete"]),
    cls("Service", "EmployeeCodes", "identity/service/EmployeeCodes", ["suggest"], extra=["MAX_TRIES = 50"]),
    cls("Service", "AccessPolicy", "identity/service/AccessPolicy", ["has", "require", "canUpdateUser"]),
    cls("Service", "ZaloLinks", "notification/zalo/ZaloLinks", ["newCode", "currentCode", "handle", "unlink", "isLinked"]),
    cls("Interface", "PhotoStorage", "storage/PhotoStorage", ["store", "load", "delete"]),
    cls("Repository", "FaceEmbeddingRepository", extra=["+queueForEnrolment(userId, sourceFilePath): int", "+deleteAngle(userId, pose): int"]),
    cls("Repository", "AppUserRepository", extra=["+existsByEmployeeCodeIgnoreCase(code): boolean", "+requireForUpdate(id): AppUser"]),
    cls("Entity", "AppUser", "identity/model/AppUser", flds="all"),
    cls("Entity", "FaceEmbedding", "attendance/model/FaceEmbedding", flds="all"),
], [
    "TeamController --> UserService", "TeamController --> EmployeeCodes", "TeamController --> PhotoStorage",
    "TeamController --> ZaloLinks", "ZaloController --> ZaloLinks",
    "UserService --> AccessPolicy", "UserService --> AppUserRepository", "UserService --> FaceEmbeddingRepository",
    "UserService --> PhotoStorage : removes replaced\nphotos after commit", "EmployeeCodes --> AppUserRepository",
    "AppUser \"1\" -- \"0..3\" FaceEmbedding : FRONT, LEFT, RIGHT",
]), "3.2")
add("3-2-seq-create", "Sequence diagram for Create an account", "sequence", seq("Create an account (with Generate)", r'''
actor Admin
participant Browser as B
participant TeamController as C
participant EmployeeCodes as E
participant PhotoStorage as P
participant UserService as U
database PostgreSQL as DB
Admin -> B : New account, choose the role, Generate
B -> C : GET /team/new/code?roleName=WORKER (htmx)
C -> E : suggest(actorId, "WORKER")
loop up to 50 draws
  E -> E : "WRK-" + 5 random digits
  E -> DB : existsByEmployeeCodeIgnoreCase(code)\n(every account, deleted too)
  break code is free
  end
end
E --> C : WRK-48213
C --> B : code field with the value (swapped in)
Admin -> B : name, group, team, phone, starting password,\nphoto, consent, Create account
B -> C : POST /team/new (multipart)
C -> P : store(FACE, photo)
alt not JPEG or PNG by its bytes, over 5 MB, or under 480 x 480
  P --> C : BusinessRuleException
  C --> B : form again with the message
else stored
  P --> C : stored name
  C -> U : create(actorId, details, photoPath)
  U -> U : code pattern, unique code, role, consent,\nname, email, phone (10 digits, unique), password
  U -> DB : INSERT app_user (BCrypt hash, must_change_password = true,\nface_consent_at = now)
  U -> DB : queueForEnrolment(user, photo) -> face_embedding FRONT, PENDING
  U --> C : AppUser
  C --> B : redirect /team/{id}/edit, "The account was created."
end
'''), "3.2")
add("3-2-seq-enrol", "Sequence diagram for Face enrolment", "sequence", seq("Face enrolment (ERP to detection app)", r'''
actor Admin
participant TeamController as C
participant UserService as U
database PostgreSQL as DB
participant "Supabase Storage" as ST
participant "ErpSync\n(detection app)" as S
participant "FaceService\n(InsightFace)" as F
Admin -> C : POST /team/{id}/face/LEFT (photo)
C -> U : changeFaceAngle(actorId, userId, LEFT, path)
U -> DB : lock the account; FRONT photo exists?
alt no FRONT photo yet
  U --> C : "Add the photo looking at the camera first"
else
  U -> DB : face_embedding (user, LEFT): status PENDING, vector cleared
end
loop every ENROLL_POLL_SECONDS
  S -> DB : SELECT PENDING face_embedding rows
  S -> ST : download source photo
  S -> F : embed_photo(image)
  alt exactly one face found
    S -> DB : UPDATE status READY, embedding vector(512), model_name
  else no face, several faces, unreadable
    S -> DB : UPDATE status FAILED, error_message
  end
  S -> DB : reload READY embeddings into the gate gallery
end
'''), "3.2")
add("3-2-seq-zalo", "Sequence diagram for Link Zalo", "sequence", seq("Link Zalo", r'''
actor Employee
participant Browser as B
participant ZaloController as C
participant ZaloLinks as L
participant ZaloStore as S
participant "Zalo app\nand bot" as Z
database PostgreSQL as DB
Employee -> B : Dashboard, Link Zalo
B -> C : POST /account/zalo/link
C -> L : newCode(actorId)
L -> S : saveCode(user, 6 random digits, now + 24 h)\n(a new code replaces the old)
C --> B : code and QR of the bot link
Employee -> Z : scan the QR, send the 6 digits
Z -> C : POST /zalo/webhook (X-Bot-Api-Secret-Token)
C -> C : acceptsSecret(header)?
C -> L : handle(chatId, text)
alt 5 wrong codes in the last hour from this chat
  L --> C : (silence)
else code unknown or expired
  L --> C : "This code is not valid..."
else chat already linked to another active employee
  L --> C : "This Zalo account is already linked to another employee..."
else valid
  L -> S : useCode(code) (deleted, single use)
  L -> S : link(user, chatId)
  S -> DB : app_user.zalo_chat_id = chatId
  L --> C : "Your Zalo account is now linked..."
end
C -> Z : sendMessage(reply)
'''), "3.2")
add("3-2-state-face", "Face enrolment lifecycle", "state", state("Face photo (face_embedding row) lifecycle", r'''
[*] --> PENDING : Admin uploads a photo\n(FRONT, LEFT or RIGHT)
PENDING --> READY : detection app finds one face\n(vector + model saved)
PENDING --> FAILED : no face, several faces\nor unreadable (reason saved)
READY --> PENDING : Admin replaces the photo
FAILED --> PENDING : Admin replaces the photo
READY --> [*] : account deleted (trigger erases)\nor angle removed
FAILED --> [*] : account deleted or angle removed
PENDING --> [*] : account deleted
'''), "3.2")
add("3-2-state-zalo", "Zalo link lifecycle", "state", state("Zalo link lifecycle", r'''
[*] --> NotLinked
NotLinked --> CodeIssued : Link Zalo / New link code\n(6 digits, 24 h)
CodeIssued --> CodeIssued : new code\n(old one stops working)
CodeIssued --> NotLinked : 24 h pass
CodeIssued --> Linked : right code sent to the bot
Linked --> NotLinked : /stop, Stop Zalo notices,\nAdmin unlinks, or account deleted
Linked : notices are sent on Zalo
NotLinked : notices stay in the ERP\n(delivery SKIPPED)
'''), "3.2")

# 3.3 Teams, zones and cameras
add("3-3-class", "Class diagram for Teams, Zones and Cameras", "class", classdiagram("Teams, Zones and Cameras", [
    cls("Controller", "TeamController", "web/TeamController", ["list", "createTeam", "changeLeader", "deleteTeam"]),
    cls("Controller", "AdminSettingsController", "web/AdminSettingsController", ["zones", "createZone", "linkCamera", "removeCamera", "rename", "team"]),
    cls("Service", "TeamService", "scheduling/service/TeamService", ["create", "changeManager", "delete", "listLeaderCandidates", "listManaged"]),
    cls("Service", "ZoneService", "scheduling/service/ZoneService", ["create", "rename", "assignTeam", "linkCamera", "removeCamera", "list", "cameras"]),
    cls("Entity", "Team", "scheduling/model/Team", flds="all", ops=["isManagedBy"]),
    cls("Entity", "Zone", "scheduling/model/Zone", flds="all", extra=["code: read back from the database (@Generated)"]),
    cls("Entity", "Camera", "ppe/model/Camera", flds="all"),
    cls("Database", "zone_code trigger", extra=["zone_next_code(kind): text", "gives GATE-IN, GATE-PPE, AREA-A ...", "refuses any change of code"]),
], [
    "TeamController --> TeamService", "AdminSettingsController --> ZoneService",
    "Team \"0..1\" -- \"0..*\" Zone : owns work areas", "Zone \"1\" -- \"0..*\" Camera",
    "Zone ..> \"zone_code trigger\" : on insert / update",
]), "3.3")
add("3-3-seq-zone", "Sequence diagram for Add a zone and link a camera", "sequence", seq("Add a zone and link a camera", r'''
actor Admin
participant AdminSettingsController as C
participant ZoneService as Z
database PostgreSQL as DB
Admin -> C : POST /zones/new (name, kind, team)
C -> Z : create(actorId, name, kind, teamId)
Z -> Z : 2 to 50 characters, unique name;\nonly a work area has a team
Z -> DB : INSERT zone (name, kind, team_id)
DB -> DB : trg_zone_code: code = zone_next_code(kind)\n(first free GATE-IN / GATE-PPE / AREA-x)
DB --> Z : zone with its code
C --> Admin : "The zone was added." (Zones shows the code)
Admin -> C : POST /zones/cameras (code CAM-03, zone)
C -> Z : linkCamera(actorId, "CAM-03", zoneId)
Z -> Z : 2 to 50 letters, digits, . - _
Z -> DB : camera by code: new or moved;\nrole = ATTENDANCE for a gate attendance zone, else PPE
C --> Admin : "The camera was linked to the zone."
'''), "3.3")

# 3.4 Shift planning and timetable
add("3-4-class", "Class diagram for Shift Planning and Timetable", "class", classdiagram("Shift Planning and Timetable", [
    cls("Controller", "ShiftController", "web/ShiftController", ["week", "newShift", "create", "edit", "update", "cancel", "copyLastWeek"]),
    cls("Controller", "TimetableController", extra=["+timetable(user, week, model): String"]),
    cls("Service", "ShiftService", "scheduling/service/ShiftService", ["create", "update", "cancel", "copyLastWeek", "listTeam", "listAll", "listOwnInRange", "isOpenForManagers", "publishedAt"]),
    cls("Service", "AccessPolicy", "identity/service/AccessPolicy", ["requireCanAssignShift", "requireCanEditShift"]),
    cls("Service", "Notices", "notification/service/Notices", ["shiftAdded", "shiftChanged", "shiftCancelled", "weekSchedule"]),
    cls("Component", "SiteJobs", "jobs/SiteJobs", ["weeklySchedule", "hourly"]),
    cls("Component", "MonthLocks", "calendar/MonthLocks", ["requireOpen#1"]),
    cls("Entity", "Shift", "scheduling/model/Shift", flds="all", ops=["isOvertime", "hasStarted"]),
], [
    "ShiftController --> ShiftService", "TimetableController --> ShiftService", "ShiftService --> AccessPolicy",
    "ShiftService --> Notices", "ShiftService --> MonthLocks", "ShiftService ..> Shift", "SiteJobs --> Notices",
]), "3.4")
add("3-4-seq-plan", "Sequence diagram for Plan a shift", "sequence", seq("Plan a shift", r'''
actor "Team leader" as M
participant ShiftController as C
participant ShiftService as S
participant AccessPolicy as A
participant Notices as N
database PostgreSQL as DB
M -> C : POST /shifts (worker, zone, kind, day, start, end, reason)
C -> S : create(actorId, details)
S -> S : complete? 30 min to 12 h? overtime reason 10-500?
S -> A : requireCanAssignShift(actor, worker)
alt worker not in the leader's team
  A --> C : AccessDeniedException (403)
end
S -> S : requireAllowed(actor, kind, start)
alt leader and kind OVERTIME
  S --> C : "Only the Admin plans overtime shifts"
else leader and the week was sent (after Saturday 18:00)
  S --> C : "The schedule of this week has been sent..."
else shift already started
  S --> C : "This shift has already started and cannot be changed."
else allowed
  S -> DB : INSERT shift
  alt overlaps another shift of the worker
    DB --> S : exclusion constraint (btree_gist)
    S --> C : "This worker already has a shift at that time" (+ times)
  else saved
    S -> N : shiftAdded(shift)
    N -> DB : notice SHIFT_ADDED (if the week was already sent)
    C --> M : redirect /shifts, "The shift was added."
  end
end
'''), "3.4")
add("3-4-seq-weekly", "Sequence diagram for Weekly schedule", "sequence", seq("Weekly schedule (Saturday 18:00)", r'''
participant JobScheduler as J
participant SiteJobs as S
participant Notices as N
database PostgreSQL as DB
participant ZaloDispatcher as Z
J -> DB : INSERT job_run ('weekly-schedule', Saturday 18:00)\nON CONFLICT DO NOTHING
alt already ran for this time
  DB --> J : nothing claimed
else claimed
  J -> S : weeklySchedule(scheduledFor)
  loop every active employee
    S -> DB : next week's shifts
    S -> N : weekSchedule(userId, monday, shifts)
    N -> DB : notice WEEKLY_SCHEDULE or WEEKLY_EMPTY\n(+ delivery row by trigger)
  end
  J -> DB : job_run finished_at, result
end
J -> Z : sendDue() on the next minute
'''), "3.4")
add("3-4-state-shift", "Shift lifecycle", "state", state("Shift lifecycle", r'''
[*] --> SCHEDULED : leader (own team, regular,\nbefore the week is sent)\nor Admin (anyone, overtime)
SCHEDULED --> SCHEDULED : edited before it starts\n(version + 1)
SCHEDULED --> CANCELLED : cancelled before it starts,\nor the account is deleted
SCHEDULED --> COMPLETED : the worker checks out (V25 trigger)\nor the hourly job after it ends
COMPLETED --> [*]
CANCELLED --> [*]
note right of SCHEDULED
  Locked once it starts (BR-24),
  and when its month is locked (BR-70)
end note
'''), "3.4")

# 3.5 Attendance and pending overtime
add("3-5-class", "Class diagram for Attendance and Pending Overtime", "class", classdiagram("Attendance and Pending Overtime", [
    cls("Controller", "AttendanceController", "web/AttendanceController", ["mine", "team", "list", "newCheckIn", "record", "edit", "correct", "pendingOvertime", "cancelOvertime", "overtimeNote"]),
    cls("Service", "CheckInService", "attendance/service/CheckInService", ["recordManual", "correct#2", "listOwn", "listOnSiteInScope", "listCorrections", "listOwnIncomplete"]),
    cls("Service", "AttendanceRules", "attendance/service/AttendanceRules", ["assess"], extra=["GRACE = 5 min (BR-31)"]),
    cls("Service", "AttendanceCalculator", "attendance/service/AttendanceCalculator", ["rows", "summary"]),
    cls("Service", "PendingOvertimeService", "attendance/service/PendingOvertimeService", ["createFromVisits", "expire", "cancel", "leaderNote", "listOpen", "prefill"]),
    cls("Component", "CheckOutHours", "attendance/service/CheckOutHours", ["fill"]),
    cls("Service", "Notices", "notification/service/Notices", ["visitRecorded", "visitCorrected"]),
    cls("Entity", "CheckIn", "attendance/model/CheckIn", flds="all", ops=["flagIncomplete"]),
    cls("Entity", "CheckInCorrection", "attendance/model/CheckInCorrection", flds="all", ops=["of"]),
    cls("Entity", "PendingOvertime", extra=["-checkIn, user, shift", "-startAt, endAt: OffsetDateTime", "-status: PENDING | VALID | CANCELLED",
                                            "-expiresAt, decidedAt: OffsetDateTime", "-overtimeShift: Shift", "-leaderNote: String"]),
], [
    "AttendanceController --> CheckInService", "AttendanceController --> PendingOvertimeService", "AttendanceController --> AttendanceCalculator",
    "AttendanceCalculator --> AttendanceRules", "CheckInService --> Notices", "CheckInService ..> CheckInCorrection : writes",
    "CheckIn \"1\" -- \"0..*\" CheckInCorrection", "CheckIn \"1\" -- \"0..1\" PendingOvertime",
    "CheckOutHours ..> AttendanceCalculator",
]), "3.5")
add("3-5-seq-gate", "Sequence diagram for Gate check-in and check-out", "sequence", seq("Gate check-in and check-out", r'''
actor Worker
participant "CAM-01" as CAM
participant "ScannerMonitor\n(detection app)" as G
participant "Outbox\n(SQLite)" as O
participant ErpSync as S
database PostgreSQL as DB
participant JobScheduler as J
participant ZaloDispatcher as Z
Worker -> CAM : looks at the camera
CAM -> G : frames
G -> G : face matched twice ~1 s apart (BR-28);\nlast scan today IN and over 5 min ago? (BR-29)
alt first scan today, or last was OUT
  G -> O : put(gate_in, user, time, event_id)
else last was IN, 5 min passed
  G -> O : put(gate_out, user, time, event_id)
else within 5 min
  G --> Worker : gate screen: "scan again at ..."
end
S -> O : head("db")
S -> DB : SELECT close_stale_check_ins(time zone)
alt gate_in
  S -> DB : INSERT check_in (method FACE, camera, similarity, event_id)\nON CONFLICT (event_id) DO NOTHING
  DB -> DB : trg_check_in_attach_shift: shift starting within 60 min (BR-30),\nclose a visit left open from an earlier day
  DB -> DB : trg_check_in_notify: notice CHECK_IN "Check-in at 07:52, starting shift ..."
else gate_out
  S -> DB : UPDATE check_in SET check_out_at
  DB -> DB : trg_check_out_notify: notice CHECK_OUT
  DB -> DB : trg_check_out_complete_shift: shift COMPLETED (V25)
end
S -> O : mark_done(seq)
J -> J : every minute: CheckOutHours.fill()\n(paid hours into CHECK_OUT notices)
J -> Z : sendDue() -> Zalo message
'''), "3.5")
add("3-5-seq-correct", "Sequence diagram for Correct a visit", "sequence", seq("Record or correct a visit by hand", r'''
actor "HSE officer" as H
participant AttendanceController as C
participant CheckInService as S
participant MonthLocks as L
participant Notices as N
database PostgreSQL as DB
H -> C : POST /attendance/{id} (in, out, reason, version)
C -> S : correct(actorId, id, in, out, reason, version, ticketId)
S -> S : require CHECKIN_EDIT
S -> L : requireOpen(old and new check-in month)
S -> S : reason 10-500? out after in? version current?
alt stale version
  S --> C : StaleEditException (form shows the newer visit)
else overlaps another visit of the person
  S --> C : "This person already has a visit at that time."
else valid
  S -> DB : INSERT check_in_correction (old and new values, who, when)
  S -> DB : UPDATE check_in (recorded_by = HSE, version + 1)
  S -> N : visitCorrected(visit) -> notice VISIT_CORRECTED
  opt the form answers an attendance error ticket
    S -> DB : ticket RESOLVED, APPROVED (approveAttendanceError)
  end
  C --> H : "The check-in was corrected."
end
'''), "3.5")
add("3-5-seq-dayend", "Sequence diagram for Day end at 23:00", "sequence", seq("Day end at 23:00: Incomplete, pending overtime, overdue", r'''
participant JobScheduler as J
participant SiteJobs as S
participant PendingOvertimeService as P
database PostgreSQL as DB
participant NotificationService as N
J -> DB : claim job_run ('day-end', 23:00)
J -> S : dayEnd(scheduledFor)
S -> DB : close_stale_check_ins(): no check-out 20 min after the shift\n-> incomplete = true; open 4 h after -> closed, auto_closed (BR-35)
S -> N : notify INCOMPLETE_ATTENDANCE to each flagged person
S -> P : createFromVisits(): time after shift end + 20 min\nwithout an overtime shift -> pending_overtime PENDING (BR-34)
S -> DB : today's PENDING_REVIEW violations -> OVERDUE (BR-53)
J -> DB : job_run finished
'''), "3.5")
add("3-5-state-visit", "Visit lifecycle", "state", state("Visit (check_in) lifecycle", r'''
[*] --> OnSite : gate check-in (FACE)\nor HSE records it (MANUAL)
OnSite --> CheckedOut : gate check-out\nor HSE enters the check-out
OnSite --> Incomplete : no check-out 20 min\nafter the shift end (23:00 job)
Incomplete --> ClosedBySystem : 4 h after the shift end,\nor the next day's check-in
Incomplete --> CheckedOut : real check-out arrives
ClosedBySystem --> Explained : missing check-out ticket\napproved (time corrected)
ClosedBySystem --> Unexplained : no explanation within\n3 working days
CheckedOut --> CheckedOut : HSE corrects it\n(history kept)
Unexplained : 0 h and To review,\nor half day (company setting)
CheckedOut --> [*]
Explained --> [*]
Unexplained --> [*]
'''), "3.5")
add("3-5-state-overtime", "Pending overtime lifecycle", "state", state("Pending overtime lifecycle", r'''
[*] --> PENDING : check-out more than 20 min\nafter the shift end (BR-34)
PENDING --> VALID : Admin adds an overtime shift\ncovering it (BR-38)
PENDING --> CANCELLED : Admin cancels it
PENDING --> CANCELLED : 48 h pass, or the 2nd of\nnext month (BR-37)
VALID --> [*]
CANCELLED --> [*]
PENDING : paid at 0%,\nleader may add a note
'''), "3.5")

# 3.6 Violation review and appeal
add("3-6-class", "Class diagram for Violation Review and Appeal", "class", classdiagram("Violation Review and Appeal", [
    cls("Controller", "ViolationController", "web/ViolationController", ["mine", "review", "all", "detail", "photo", "take", "confirm", "dismiss", "dispute", "decide"]),
    cls("Service", "ViolationService", "ppe/service/ViolationService", ["queue", "get", "take", "candidates", "suggestion", "confirm", "dismiss", "appealDeadline", "whyNoAppeal", "markAppealed", "decideAppeal", "listOwn#2"]),
    cls("Service", "TicketService", "ticketing/service/TicketService", ["openDispute", "decideAppeal"]),
    cls("Component", "MonthLocks", "calendar/MonthLocks", ["requireOpen#1"]),
    cls("Interface", "PhotoStorage", "storage/PhotoStorage", ["load"]),
    cls("Entity", "Violation", "ppe/model/Violation", flds="all", ops=["confirm", "confirmStranger", "dismiss", "recordAppealDecision", "belongsTo"]),
    cls("Database", "violation triggers", extra=["trg_violation_worker: zone from camera,\nnamed person -> suggestion", "trg_violation_alert: VIOLATION_ALERT to\nHSE on duty (or all HSE and Admins)"]),
], [
    "ViolationController --> ViolationService", "ViolationController --> TicketService", "ViolationController --> PhotoStorage",
    "ViolationService --> MonthLocks", "ViolationService ..> Violation", "TicketService ..> Violation : appeal ticket",
    "Violation ..> \"violation triggers\" : on insert",
]), "3.6")
add("3-6-seq-detect", "Sequence diagram for Detect and alert", "sequence", seq("A PPE violation is detected and HSE is alerted", r'''
actor Worker
participant "CAM-03" as CAM
participant "SafetyMonitor\n(detection app)" as M
participant "Outbox" as O
participant ErpSync as S
participant "Supabase Storage" as ST
database PostgreSQL as DB
participant "Speaker" as SP
Worker -> CAM : works without a hard hat
CAM -> M : frames, person boxes, PPE classes
M -> SP : spoken reminder (BR-42)
M -> M : hard hat missing 10 s / vest 3 s (BR-43);\nboth missing = one NO_HARD_HAT_AND_VEST (BR-45);\ncooldown 60 s per person and item (BR-44)
M -> O : put(violation, type, time, box, photo,\nsuggested face + similarity, event_id)
S -> O : head("db")
S -> ST : upload evidence photo (evidence-photos)
S -> DB : INSERT violation (suggested_user_id, ...)\nON CONFLICT (event_id) DO NOTHING
DB -> DB : trg_violation_worker: zone from camera;\nreview_status PENDING_REVIEW, no person (BR-48)
DB -> DB : trg_violation_alert: HSE officers with a shift now\nget VIOLATION_ALERT; nobody on duty -> all HSE and Admins (BR-54)
S -> O : mark_done(seq)
'''), "3.6")
add("3-6-seq-review", "Sequence diagram for Review and confirm", "sequence", seq("Review: take and confirm (or dismiss)", r'''
actor "HSE officer" as H
participant ViolationController as C
participant ViolationService as V
participant MonthLocks as L
database PostgreSQL as DB
participant NotificationService as N
H -> C : GET /violations/review
C -> V : queue(actorId, zone, day, page)
V -> DB : PENDING_REVIEW and OVERDUE, oldest first
H -> C : POST /violations/{id}/take
C -> V : take(actorId, id) -> taken_by, taken_at ("... is going")
H -> C : POST /violations/{id}/confirm (person or stranger, note, version)
C -> V : confirm(actorId, id, personId, stranger, note, version)
V -> L : requireOpen(detected_at)
alt version changed (someone else decided)
  V --> C : "This violation was already decided by another officer..."
else neither person nor stranger
  V --> C : "Choose the person, or mark the violation as Stranger."
else
  V -> DB : user, subject EMPLOYEE, CONFIRMED (SITE)\nor REMINDER (OFFICE); reviewed_by, reviewed_at
  V -> DB : safety_scores(): new score and level
  V -> N : VIOLATION_CONFIRMED (score, level, appeal deadline)\nor REMINDER; LEVEL_WORSE if the level dropped
end
'''), "3.6")
add("3-6-seq-appeal", "Sequence diagram for Appeal and decision", "sequence", seq("Appeal a violation and the Admin's decision", r'''
actor Employee
actor Admin
actor "HSE officer" as H
participant ViolationController as C
participant TicketService as T
participant ViolationService as V
database PostgreSQL as DB
participant NotificationService as N
Employee -> C : POST /violations/{id}/dispute (reason)
C -> T : openDispute(actorId, id, subject, reason)
T -> V : whyNoAppeal(actor, violation)
alt not theirs, a reminder, already appealed, or past 3 working days
  V --> C : reason (no appeal)
else
  T -> DB : ticket PPE appeal (OPEN), violation DISPUTED
  T -> N : TICKET_CREATED to the Admins
end
Admin -> T : askHse(ticket, officer) -> ticket_participant
T -> N : ASKED_FOR_FACTS to the HSE officer
H -> T : saveHseFacts(ticket, facts)
Admin -> C : POST /violations/{id}/decision (facts, note, UPHELD | OVERTURNED, right person)
C -> T : decideAppeal(actorId, id, decision, facts, note, rightPerson)
alt the Admin opened the appeal
  T --> C : refused: another Admin decides
else note or facts too short
  T --> C : rule message
else
  T -> V : decideAppeal(actor, violation, decision, rightPerson)
  V -> DB : dispute UPHELD or OVERTURNED, appeal_decided_by/at\n(OVERTURNED: points back; right person gets it reopened)
  T -> DB : ticket RESOLVED with outcome and note (same transaction)
  T -> N : APPEAL_DECIDED to the employee
end
'''), "3.6")
add("3-6-state-review", "Violation review lifecycle", "state", state("Violation review status", r'''
[*] --> PENDING_REVIEW : detection app writes it\n(only a suggestion)
PENDING_REVIEW --> OVERDUE : not reviewed by 23:00\nof its day (BR-53)
PENDING_REVIEW --> CONFIRMED : HSE names a SITE employee
PENDING_REVIEW --> REMINDER : HSE names an OFFICE employee
PENDING_REVIEW --> CONFIRMED : HSE marks a Stranger\n(subject STRANGER, no person)
PENDING_REVIEW --> DISMISSED : HSE dismisses with a reason
OVERDUE --> CONFIRMED
OVERDUE --> REMINDER
OVERDUE --> DISMISSED
CONFIRMED --> [*]
REMINDER --> [*]
DISMISSED --> [*]
note right of CONFIRMED
  Only a confirmed violation of an
  employee counts in the score and
  can be appealed (BR-64)
end note
'''), "3.6")
add("3-6-state-dispute", "Appeal lifecycle", "state", state("Appeal (dispute_status) lifecycle", r'''
[*] --> NONE
NONE --> DISPUTED : the employee appeals within\n3 working days (once)
DISPUTED --> UPHELD : Admin upholds\n(the violation stands)
DISPUTED --> OVERTURNED : Admin overturns\n(5 points back)
UPHELD --> [*]
OVERTURNED --> [*]
'''), "3.6")

# 3.7 Safety score and bonus level
add("3-7-class", "Class diagram for Safety Score and Bonus Level", "class", classdiagram("Safety Score and Bonus Level", [
    cls("Controller", "ViolationController", "web/ViolationController", ["mine"]),
    cls("Controller", "SafetyController", "web/SafetyController", ["shifts"]),
    cls("Controller", "WorkerController", extra=["+list(user, q, page, model): String", "+detail(user, id, model): String"]),
    cls("Service", "SafetyScoreService", "ppe/service/SafetyScoreService", ["own#2", "of", "current", "inMonth", "listWorkers", "listTimeSlots"]),
    cls("Component", "SafetyLevel", extra=["LEVEL_0 (0%)", "LEVEL_1 (-2%)", "LEVEL_2 (-4%)", "LEVEL_3 (-6%)", "+reductionPercent(): int"]),
    cls("Component", "SiteJobs", "jobs/SiteJobs", ["lockMonth", "lock"]),
    cls("Database", "safety_scores(at)", extra=["live: 100 - 5 x confirmed, not overturned,", "SITE staff, WORKER or MANAGER role", "locked month: frozen monthly_score rows"]),
    cls("Database", "safety_policy", extra=["points_per_violation = 5", "level1_at = 80, level2_at = 70, level3_at = 60", "time_zone"]),
    cls("Database", "monthly_score", extra=["user_id, month (PK)", "violations, score, level, frozen_at"]),
], [
    "ViolationController --> SafetyScoreService", "SafetyController --> SafetyScoreService", "WorkerController --> SafetyScoreService",
    "SafetyScoreService ..> \"safety_scores(at)\"", "SafetyScoreService ..> SafetyLevel",
    "\"safety_scores(at)\" ..> \"safety_policy\"", "\"safety_scores(at)\" ..> \"monthly_score\"",
    "SiteJobs ..> \"monthly_score\" : freezes at the lock",
]), "3.7")
add("3-7-seq-score", "Sequence diagram for View my violations and score", "sequence", seq("View my violations and safety score", r'''
actor "Worker or team leader" as W
participant ViolationController as C
participant ViolationService as V
participant SafetyScoreService as S
database PostgreSQL as DB
W -> C : GET /violations?month=2026-11
C -> V : listOwn(actorId, month, page)
V -> V : require VIOLATION_READ_OWN (WORKER, MANAGER since V27)
V -> DB : own CONFIRMED and REMINDER violations of the month
C -> S : own(actorId, month)
S -> DB : SELECT * FROM safety_scores(month)
alt month locked
  DB --> S : frozen monthly_score row
else open month
  DB --> S : live score from violation rows
end
S --> C : score, level, reduction %, confirmed count
C --> W : list with the score, bonus level and appeal links
'''), "3.7")

# 3.8 Tickets
add("3-8-class", "Class diagram for Tickets", "class", classdiagram("Tickets", [
    cls("Controller", "TicketController", "web/TicketController", ["list", "queue", "newTicket", "create", "detail", "comment", "take", "assign", "askHse", "facts", "decide", "cancel", "photo"]),
    cls("Service", "TicketService", "ticketing/service/TicketService", ["create#2", "openDispute", "addComment", "assign", "askHse", "saveHseFacts", "decide",
                                                                       "prepareAttendanceApproval", "approveAttendanceError", "cancel", "listOwn#1", "listQueue#1", "listTeam"]),
    cls("Service", "AccessPolicy", "identity/service/AccessPolicy", ["requireCanReadTicket", "requireCanUpdateTicket", "requireCanCommentTicket"]),
    cls("Service", "CheckInService", "attendance/service/CheckInService", ["correct#2"]),
    cls("Entity", "Ticket", "ticketing/model/Ticket", flds="all", ops=["decide", "cancel", "assignTo"]),
    cls("Entity", "TicketCategory", "ticketing/model/TicketCategory", flds="all"),
    cls("Entity", "TicketComment", "ticketing/model/TicketComment", flds="all"),
    cls("Entity", "TicketParticipant", extra=["-ticket, user (PK)", "-askedBy: AppUser", "-askedAt: OffsetDateTime"]),
], [
    "TicketController --> TicketService", "TicketService --> AccessPolicy", "TicketService ..> CheckInService : attendance error\napproved by correcting",
    "TicketCategory \"1\" -- \"0..*\" Ticket", "Ticket \"1\" -- \"0..*\" TicketComment", "Ticket \"1\" -- \"0..*\" TicketParticipant : HSE asked for facts",
]), "3.8")
add("3-8-seq-error", "Sequence diagram for Attendance error report", "sequence", seq("Attendance error: report and approval", r'''
actor Employee
actor Admin
participant TicketController as C
participant TicketService as T
participant AttendanceController as AC
participant CheckInService as S
database PostgreSQL as DB
Employee -> C : POST /tickets (ATTENDANCE_ERROR, shift, IN/OUT, gate time, photo)
C -> T : create(actorId, details)
alt no photo / shift over 2 days ago / open ticket for this shift
  T --> C : "Attach a photo..." / "The period for this request has ended." /\n"You already have an open request for this..."
else
  T -> DB : INSERT ticket OPEN (+ TICKET_CREATED to the Admins)
end
Admin -> C : POST /tickets/{id}/take, then Approve
C -> T : prepareAttendanceApproval(actorId, id, note)
C --> Admin : Attendance form with the visit and "answers ticket #id"
Admin -> AC : POST /attendance/{visit} (corrected time, reason, ticketId)
AC -> S : correct(..., ticketId)
S -> DB : correction (ticket_id), visit updated
S -> T : approveAttendanceError(actorId, id)
T -> DB : ticket RESOLVED, outcome APPROVED (+ TICKET_DECIDED)
'''), "3.8")
add("3-8-state-ticket", "Ticket lifecycle", "state", state("Ticket lifecycle", r'''
[*] --> OPEN : employee sends it\n(PPE appeal from the violation)
OPEN --> IN_PROGRESS : an Admin takes it\nor it is assigned
OPEN --> CANCELLED : the author cancels it
IN_PROGRESS --> CANCELLED : the author cancels it
OPEN --> RESOLVED : decided
IN_PROGRESS --> RESOLVED : Admin decides:\nAPPROVED / REJECTED,\nor UPHELD / OVERTURNED for an appeal
RESOLVED --> [*]
CANCELLED --> [*]
note right of IN_PROGRESS
  The team leader reads and comments;
  an HSE officer who was asked adds facts;
  an Admin can't decide their own ticket
end note
'''), "3.8")

# 3.9 Notifications and the Zalo bot
add("3-9-class", "Class diagram for Notifications and the Zalo Bot", "class", classdiagram("Notifications and the Zalo Bot", [
    cls("Controller", "NotificationController", "web/NotificationController", ["list", "markRead", "markAllRead"]),
    cls("Controller", "ZaloController", "web/ZaloController", ["webhook"]),
    cls("Service", "NotificationService", "notification/service/NotificationService", ["notify", "notifyAll", "listOwn", "countUnread", "markRead", "markAllRead", "deliveries"]),
    cls("Service", "NoticeText", "notification/service/NoticeText", ["render#2", "json", "params"]),
    cls("Service", "ZaloDispatcher", "notification/service/ZaloDispatcher", ["sendDue"], extra=["RETRIES = 1, 5, 15 min"]),
    cls("Component", "ZaloBot", "notification/zalo/ZaloBot", ["send", "sendNow", "registerWebhook", "acceptsSecret", "botLink", "canSend"]),
    cls("Service", "ZaloLinks", "notification/zalo/ZaloLinks", ["handle"]),
    cls("Component", "ZaloCommands", "notification/zalo/ZaloCommands", ["reply", "split"]),
    cls("Entity", "Notification", "notification/model/Notification", flds="all", ops=["markRead"]),
    cls("Database", "notification_delivery", extra=["notification_id (PK, FK)", "status: PENDING | SENT | FAILED | SKIPPED", "attempts, next_attempt_at, sent_at, last_error", "added by trg_notification_delivery"]),
], [
    "NotificationController --> NotificationService", "NotificationService ..> Notification", "NotificationService --> NoticeText",
    "ZaloDispatcher --> ZaloBot", "ZaloDispatcher --> NoticeText", "ZaloDispatcher ..> \"notification_delivery\"",
    "ZaloController --> ZaloLinks", "ZaloController --> ZaloBot", "ZaloLinks --> ZaloCommands",
    "Notification \"1\" -- \"0..1\" \"notification_delivery\"",
]), "3.9")
add("3-9-seq-deliver", "Sequence diagram for Notice delivery", "sequence", seq("A notice is saved, then delivered on Zalo", r'''
participant "Service or\ndatabase trigger" as X
database PostgreSQL as DB
participant JobScheduler as J
participant CheckOutHours as H
participant ZaloDispatcher as Z
participant NoticeText as T
participant ZaloBot as B
participant "Zalo Bot API" as API
X -> DB : INSERT notification (kind, params, link)
DB -> DB : trg_notification_delivery: PENDING if the person\nlinked Zalo, else SKIPPED (BR-15, BR-16)
loop every minute
  J -> H : fill(): paid hours into CHECK_OUT params
  J -> Z : sendDue()
  Z -> DB : due PENDING rows (CHECK_OUT only once it has hours)\nFOR UPDATE SKIP LOCKED
  loop each due notice
    Z -> T : render(kind, params, vi) (cut at 1,900 chars + link)
    Z -> B : sendNow(chatId, text)
    B -> API : POST /sendMessage
    alt accepted
      Z -> DB : SENT, sent_at, attempts + 1
    else error
      Z -> DB : attempts + 1, next try after 1, 5, 15 min;\nafter the third retry FAILED
    end
  end
end
'''), "3.9")
add("3-9-seq-bot", "Sequence diagram for Bot commands", "sequence", seq("Bot commands (/lich, /chamcong)", r'''
actor Employee
participant "Zalo" as Z
participant ZaloController as C
participant ZaloBot as B
participant ZaloLinks as L
participant ZaloCommands as M
database PostgreSQL as DB
Employee -> Z : /chamcong
Z -> C : POST /zalo/webhook
C -> B : acceptsSecret(header)?
alt wrong secret, a group chat, or not text
  C --> Z : 200, nothing done
else
  C -> L : handle(chatId, "/chamcong")
  L -> DB : active owner of this chat?
  alt not linked
    L --> C : how to link (no data)
  else linked
    L -> M : reply(userId, "/chamcong")
    M -> DB : this month's shifts, visits, totals, safety score
    M -> M : one line per shift, totals first;\nsplit at line ends into <= 2,000 chars
    M --> C : messages
  end
  loop each message, in order
    C -> B : send(chatId, text)
  end
end
'''), "3.9")
add("3-9-state-delivery", "Notice delivery lifecycle", "state", state("Zalo delivery lifecycle", r'''
[*] --> PENDING : notice saved and the\nperson linked Zalo
[*] --> SKIPPED : notice saved, no Zalo link
PENDING --> SENT : Zalo accepted it
PENDING --> PENDING : error: retry after\n1, 5, 15 min
PENDING --> FAILED : third retry failed
PENDING --> SKIPPED : the chat was unlinked\nbefore sending
SENT --> [*]
FAILED --> [*]
SKIPPED --> [*]
'''), "3.9")

# 3.10 Scheduled jobs and month closing
add("3-10-class", "Class diagram for Scheduled Jobs and Month Closing", "class", classdiagram("Scheduled Jobs and Month Closing", [
    cls("Component", "JobScheduler", "jobs/JobScheduler", ["atStartup", "tick", "runOnce"], extra=["@Scheduled(fixedDelay = 60 s)"]),
    cls("Component", "SiteJobs", "jobs/SiteJobs", ["hourly", "dayEnd", "morning", "oldTickets", "weeklySchedule", "monthEnd", "cancelLastMonthOvertime", "lockMonth"]),
    cls("Component", "SiteCalendar", "calendar/SiteCalendar", ["now", "isWorkingDay", "nthWorkingDay", "lastWorkingDay", "deadline"]),
    cls("Component", "MonthLocks", "calendar/MonthLocks", ["isLocked#1", "requireOpen#1"]),
    cls("Component", "CheckOutHours", "attendance/service/CheckOutHours", ["fill"]),
    cls("Service", "ZaloDispatcher", "notification/service/ZaloDispatcher", ["sendDue"]),
    cls("Database", "job_run", extra=["job, scheduled_for (PK)", "started_at, finished_at, result"]),
    cls("Database", "month_lock", extra=["month (PK)", "locked_at"]),
], [
    "JobScheduler --> SiteJobs", "JobScheduler --> CheckOutHours", "JobScheduler --> ZaloDispatcher",
    "JobScheduler ..> job_run : claims each run once", "SiteJobs --> SiteCalendar", "SiteJobs ..> month_lock : lockMonth",
    "MonthLocks ..> month_lock",
]), "3.10")
add("3-10-seq-tick", "Sequence diagram for Job tick and catch-up", "sequence", seq("Every minute: due jobs, run once, caught up after sleep", r'''
participant "Spring @Scheduled" as SP
participant JobScheduler as J
participant SiteJobs as S
database PostgreSQL as DB
SP -> J : tick() (also at start-up after Render wakes)
J -> J : CheckOutHours.fill(), ZaloDispatcher.sendDue()
loop each job (hourly, day-end, morning, old-tickets,\nweekly-schedule, month-end, cancel-last-month-overtime, month-lock)
  J -> J : latest due time (Vietnam time)
  alt due within the catch-up window
    J -> DB : INSERT job_run (job, due) ON CONFLICT DO NOTHING
    alt claimed
      J -> S : run(due)
      S --> J : result or "FAILED: ..."
      J -> DB : finished_at, result
    else already ran
      J -> J : skip
    end
  end
end
'''), "3.10")
add("3-10-state-month", "Month lifecycle", "state", state("Month lifecycle", r'''
[*] --> Open
Open --> Open : scores live from violations;\nreports marked Provisional
Open --> Locked : 23:59 of the 2nd working day of\nthe next month (scores frozen)
Locked : shifts, visits, violations and scores\nof the month no longer change (BR-70)
Locked --> [*]
'''), "3.10")

# 3.11 Home page and monthly report
add("3-11-class", "Class diagram for Home Page and Monthly Report", "class", classdiagram("Home Page and Monthly Report", [
    cls("Controller", "HomeController", "web/HomeController", ["home"]),
    cls("Controller", "ReportController", "web/ReportController", ["page", "download"]),
    cls("Service", "MonthlyReportService", "reporting/service/MonthlyReportService", ["build#2", "logExport", "exports"]),
    cls("Component", "MonthlyReportWorkbook", "web/MonthlyReportWorkbook", ["write", "fileName"],
        extra=["sheets: Site summary, Office summary,", "Violations, Attendance, Absences"]),
    cls("Service", "AttendanceCalculator", "attendance/service/AttendanceCalculator", ["rows", "summary"]),
    cls("Service", "SafetyScoreService", "ppe/service/SafetyScoreService", ["inMonth"]),
    cls("Component", "Charts", extra=["bars worked out in Java,", "drawn with HTML and CSS"]),
    cls("Database", "report_export", extra=["user_id, month, scope, provisional, exported_at"]),
], [
    "HomeController ..> Charts", "ReportController --> MonthlyReportService", "ReportController --> MonthlyReportWorkbook",
    "MonthlyReportService --> AttendanceCalculator", "MonthlyReportService --> SafetyScoreService", "MonthlyReportService ..> report_export : logExport",
]), "3.11")
add("3-11-seq-export", "Sequence diagram for Export the monthly report", "sequence", seq("Export the monthly report", r'''
actor "Team leader or Admin" as U
participant ReportController as C
participant MonthlyReportService as R
participant AttendanceCalculator as A
participant SafetyScoreService as S
participant MonthlyReportWorkbook as W
database PostgreSQL as DB
U -> C : GET /team/report?month=2026-11&teamId=...
C -> R : build(actorId, month, teamId)
R -> R : REPORT_EXPORT_ALL, or REPORT_EXPORT_TEAM for the own team
alt month not started
  R --> C : "This month has not started yet."
else
  R -> A : rows(employees, month) -> paid hours, late, early,\nabsences, overtime, Incomplete, leave
  R -> S : inMonth(each SITE employee) -> score, level, reduction
  R -> DB : violations and visits of the month
  R --> C : Report (provisional unless the month is locked)
  C -> W : write(report) (fastexcel, streamed, reader's language)
  C -> R : logExport(actorId, report) -> report_export
  C --> U : .xlsx download
end
'''), "3.11")

# 3.12 Photo storage
add("3-12-class", "Class diagram for Photo Storage", "class", classdiagram("Photo Storage", [
    cls("Interface", "PhotoStorage", "storage/PhotoStorage", ["store", "load", "delete", "contentType"], extra=["MAX_BYTES = 5 MB"]),
    cls("Component", "LocalPhotoStorage", "storage/LocalPhotoStorage", ["store", "load", "delete"]),
    cls("Component", "SupabasePhotoStorage", "storage/SupabasePhotoStorage", ["store", "load", "delete"]),
    cls("Component", "PhotoAlbum", extra=["TICKET -> ticket-photos", "FACE -> face-photos", "EVIDENCE -> evidence-photos (read only)"]),
    cls("Component", "PhotoFormat", extra=["JPEG or PNG from the file's bytes", "at least 480 x 480"]),
    cls("Controller", "TeamController", "web/TeamController", ["create"]),
    cls("Controller", "TicketController", "web/TicketController", ["photo"]),
    cls("Controller", "ViolationController", "web/ViolationController", ["photo"]),
], [
    "PhotoStorage <|.. LocalPhotoStorage", "PhotoStorage <|.. SupabasePhotoStorage", "PhotoStorage ..> PhotoAlbum",
    "LocalPhotoStorage ..> PhotoFormat", "SupabasePhotoStorage ..> PhotoFormat",
    "TeamController --> PhotoStorage", "TicketController --> PhotoStorage", "ViolationController --> PhotoStorage",
]), "3.12")
add("3-12-seq-photo", "Sequence diagram for Show an evidence photo", "sequence", seq("Show an evidence photo (permission first)", r'''
actor User
participant ViolationController as C
participant ViolationService as V
participant SupabasePhotoStorage as P
participant "Supabase Storage" as ST
User -> C : GET /violations/{id}/photo
C -> V : get(actorId, id)
alt not HSE/Admin and not the person's own decided violation
  V --> C : AccessDeniedException (403)
else allowed
  C -> P : load(EVIDENCE, snapshot_path)
  P -> P : path without ".."
  P -> ST : GET /storage/v1/object/evidence-photos/... (server key)
  ST --> P : bytes
  C --> User : image (private, no Storage URL exposed)
end
'''), "3.12")

# 3.13 Detection app
add("3-13-class", "Class diagram for the Detection App", "class", classdiagram("Detection app (Python)", [
    cls("Python", "Engine", extra=["+start(): void", "+stop(): void", "+frame(cam_index): tuple", "+stats(): dict"]),
    cls("Python", "CaptureWorker", extra=["+start(): void", "+read_latest(): (seq, frame)"]),
    cls("Python", "Detector", extra=["+predict_batch(frames): list", "+violation_type(cls_id): ViolationType"]),
    cls("Python", "FaceService", extra=["+update(cam, frame, tracked): dict", "+embed_photo(img, user_id): (vector, error)"]),
    cls("Python", "ErpGallery", extra=["+set_rows(rows): void", "+match(embedding): (id, code, score)"]),
    cls("Python", "ScannerMonitor", extra=["+process(tracked, identities, ...): list", "+direction_for(uid, now): (IN|OUT, wait)"]),
    cls("Python", "SafetyMonitor", extra=["+process(tracked, ppe_by_pid, ...): list", "-cooldown: CooldownBook (60 s)"]),
    cls("Python", "Outbox", extra=["+put(kind, payload, lane): int", "+head(lane): dict", "+mark_done(seq): void",
                                   "+mark_retry(seq, error, delay): void", "+mark_failed(seq, error): void"]),
    cls("Python", "ErpSync", extra=["+start(): void", "+apply(conn, item): void", "-close_stale(): void", "-enrol_pending(): void", "-refresh_reference(): void"]),
    cls("Python", "StorageClient", extra=["+upload(bucket, obj, local_path): void", "+download(bucket, obj): bytes"]),
], [
    "Engine --> CaptureWorker", "Engine --> Detector", "Engine --> FaceService", "Engine --> ScannerMonitor", "Engine --> SafetyMonitor",
    "FaceService --> ErpGallery", "ScannerMonitor --> Outbox : gate_in / gate_out", "SafetyMonitor --> Outbox : violation",
    "ErpSync --> Outbox", "ErpSync --> StorageClient", "ErpSync --> ErpGallery : READY embeddings",
]), "3.13")
add("3-13-seq-sync", "Sequence diagram for Offline outbox and sync", "sequence", seq("Offline outbox and sync", r'''
participant "Monitors" as M
participant "Outbox (SQLite)" as O
participant ErpSync as S
participant StorageClient as ST
database PostgreSQL as DB
M -> O : put(kind, payload, event_id) (always, online or not)
loop while running
  S -> O : head("db") (oldest first)
  alt nothing due
    S -> O : wait()
  else event
    opt violation with a photo
      S -> ST : upload(evidence-photos, path)
    end
    S -> DB : INSERT ... ON CONFLICT (event_id) DO NOTHING
    alt stored (or already there)
      S -> O : mark_done(seq)
    else network down, timeout, ERP asleep (TransientError)
      S -> O : mark_retry(seq, error, delay)
      note right : order kept: a check-out\nnever passes its check-in
    else refused by the database (PermanentError)
      S -> O : mark_failed(seq, error)
    end
  end
end
'''), "3.13")

# ===== 4. Integration ===============================================================================
add("4-1-integration", "Integration overview", "architecture", arch("Interfaces between the parts", r'''
left to right direction
rectangle "Browser" as b
rectangle "Web ERP" as e #E8F5E9
database "PostgreSQL" as db
storage "Supabase Storage" as st
rectangle "Zalo Bot Platform" as z
rectangle "Detection app" as d #E0F7FA
rectangle "Cameras" as c
b --> e : 4.1 pages and htmx\n(session + CSRF)
e --> db : JDBC (owner)
d --> db : 4.2 psycopg\n(steelsight_detection)
e --> st : 4.3 REST (server key)
d --> st : 4.3 evidence photos
e --> z : 4.4 sendMessage
z --> e : 4.4 webhook
c --> d : 4.5 RTSP
'''), "4")

# ===== 5. Security ==================================================================================
add("5-1-authz", "Authorization by role", "architecture", arch("What each role reaches", r'''
left to right direction
actor Worker
actor "Team leader" as TL
actor "HSE officer" as HSE
actor Admin
rectangle "Own: timetable, attendance,\nviolations and score, tickets,\nnotifications, Zalo link" as own
rectangle "Team: shift grid (regular),\nteam attendance, team tickets\n(read, comment), team report" as team
rectangle "Safety: review queue, violation\nlist, time slots, record and\ncorrect visits, facts when asked" as safety
rectangle "Administration: accounts, teams,\nzones, cameras, ticket queue,\nappeals, overtime, settings, site report" as admin
Worker --> own
TL --> own
TL --> team
HSE --> own
HSE --> safety
Admin --> own
Admin --> team
Admin --> safety
Admin --> admin
'''), "5")


def main():
    os.makedirs(OUT, exist_ok=True)
    for old in glob.glob(os.path.join(OUT, "*.puml")):
        os.remove(old)
    for d in DIAGRAMS:
        with open(os.path.join(OUT, d["id"] + ".puml"), "w", encoding="utf8") as f:
            f.write(d["body"])
    with open(MANIFEST, "w", encoding="utf8") as f:
        json.dump([{k: v for k, v in d.items() if k != "body"} for d in DIAGRAMS], f, indent=1)
    print(f"{len(DIAGRAMS)} diagrams written to {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
