"""Builds the Report 5 test case workbooks from the real test results.

Run the suites first, then this script:

    # ERP (Docker must be running for Testcontainers); JaCoCo is attached from the command line
    ./mvnw org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.13:report

    # Detection app, twice (round 1 and round 2), from its repository
    python -m pytest tests --junitxml=/tmp/det-round1.xml
    python -m pytest tests --junitxml=/tmp/det-round2.xml

    # Workbooks (needs openpyxl)
    python scripts/report5_test_cases.py . /tmp/det-round1.xml /tmp/det-round2.xml /tmp/report5-stats.json 25/11/2026

Inputs: the ERP's Maven Surefire XML reports (target/surefire-reports) and the detection app's pytest
JUnit XML for two rounds. Outputs: 04_testing/Report5_Unit_Test.xlsx, 04_testing/Report5_Test_Report.xlsx
and a JSON file of the figures used in Report5_Test_Documentation.md.

Each case gets a module (MF01-MF09) from its test class, or from keywords in its method name for classes
that test several features; a level (unit when no Spring context or database starts, system for the SRS
workflow tests, integration otherwise); a testing type and an N/A/B type from keywords in its name; and
the SRS rule its method name starts with (br31_, msg22_, cr10_, workflow124_).
"""
import collections
import glob
import json
import re
import sys
import xml.etree.ElementTree as ET

from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter

ERP, DET1, DET2, OUT_STATS, RUN_DATE = sys.argv[1:6]

MODULES = collections.OrderedDict([
    ("MF01", "Accounts and Access (IAM)"),
    ("MF02", "Teams, Zones and Shifts (SCH)"),
    ("MF03", "Attendance and Overtime (ATT)"),
    ("MF04", "PPE Violations and Safety Score (PPE)"),
    ("MF05", "Tickets (TKT)"),
    ("MF06", "Notifications and Zalo (NTF)"),
    ("MF07", "Reports and Month Closing (RPT)"),
    ("MF08", "Photo Storage (STO)"),
    ("MF09", "Platform: errors, logging, schema, jobs (PLT)"),
])

CLASS_MODULE = {
    "AttendanceRulesTest": "MF03", "AutoCloseCheckInTest": "MF03", "srs.ShiftAttendanceTest": "MF03",
    "SafetyScoreTest": "MF04", "SrsTicketRulesTest": "MF05",
    "LoginIntegrationTest": "MF01", "AccountPagesTest": "MF01", "AccountSecurityTest": "MF01",
    "FacePhotoCleanupTest": "MF01", "security.FirstSignInHandlerTest": "MF01", "security.SignInLimiterTest": "MF01",
    "security.ApiKeyFilterTest": "MF09", "security.ApiLimiterTest": "MF09", "DetectionApiWithoutKeyTest": "MF09",
    "storage.LocalPhotoStorageTest": "MF08", "storage.PhotoStorageSelectionTest": "MF08",
    "storage.SupabasePhotoStorageTest": "MF08", "RealUploadTest": "MF08", "web.MultipartLimitFilterTest": "MF08",
    "ZaloNoticesTest": "MF06", "notification.zalo.ZaloCommandsTest": "MF06", "web.QrCodesTest": "MF06",
    "ErrorPagesTest": "MF09", "RequestLoggingTest": "MF09", "DeploymentEndpointsTest": "MF09",
    "TranslationsTest": "MF09", "validation.TextTest": "MF09", "SteelsightErpApplicationTests": "MF09",
    "SchemaChecksSqlTest": "MF09", "ServiceAtomicityTest": "MF09", "ShiftOverlapPageTest": "MF02",
}

# Keyword rules for classes that test several modules, first match wins.
KEYWORDS = [
    ("MF06", r"zalo|notice|notification|bell|toldThe|isTold"),
    ("MF07", r"report|export|monthLock|locksAndTheReport|provisional|lockedMonth"),
    ("MF05", r"ticket|comment|categor|leave|safetyConcern|assignee|queue(?!ListsOnlyOpenViolations)"),
    ("MF04", r"violation|dispute|appeal|score|level|hseConfirms|stranger|dismiss|reminder|unsafe|timeSlot|detect"),
    ("MF03", r"checkIn|checkOut|check-in|visit|attendance|correction|overtime|incomplete|autoClose|grace|absent|gate"),
    ("MF02", r"shift|team|zone|camera|grid|timetable|week|leader"),
    ("MF01", r"login|password|signIn|account|user|face|embedding|angle|consent|role|permission|employeeCode|phone|deactivat|delete|profile|session|admin|people|worker"),
    ("MF09", r"."),
]

UNIT_CLASSES = {
    "AttendanceRulesTest", "TranslationsTest", "notification.zalo.ZaloCommandsTest", "security.ApiKeyFilterTest",
    "security.ApiLimiterTest", "security.FirstSignInHandlerTest", "security.SignInLimiterTest",
    "srs.ShiftAttendanceTest", "storage.LocalPhotoStorageTest", "storage.PhotoStorageSelectionTest",
    "storage.SupabasePhotoStorageTest", "validation.TextTest", "web.MultipartLimitFilterTest", "web.QrCodesTest",
}
SYSTEM_CLASSES = {"SrsWorkflowTest"}

SECURITY = r"forbidden|NoOneElse|cannot|Cannot|only|Only|csrf|Csrf|escape|Escaped|permission|private|Private|tamper|Tamper|login|Login|password|Password|lock|Lock|key|Key|someoneElse|SomeoneElse|another|Another|refusedWithout|secret|Secret|bypass|signIn|SignIn|menu|Menu|cannotReach|outsiders|Outsiders"
INTEGRITY = r"stale|Stale|overlap|Overlap|duplicate|Duplicate|database|Database|once|Once|twice|Twice|atomic|rollback|Rollback|constraint|unique|Unique|halfResolve|HalfResolve|equalById|RowLevelSecurity|computedDuration|retried|Retried"
BOUNDARY = r"upTo|UpTo|exactly|Exactly|Minute|minute|Hours|hours|midnight|Midnight|edge|Edge|limit|Limit|max|Max|longest|Longest|shortest|Shortest|first|First|oddPage|tenDigits|TenDigits|overnight|Overnight|1900|2000|cut|Cut|split|Split|Seconds|seconds|grace|Grace|early|Early|late|Late|before|Before|after|After"
ABNORMAL = r"refuse|Refuse|reject|Reject|cannot|Cannot|wrong|Wrong|invalid|Invalid|bad|Bad|[nN]ot|never|Never|fail|Fail|fake|Fake|missing|Missing|blank|Blank|tamper|Tamper|forbidden|Forbidden|only|Only|unknown|Unknown|error|Error|deny|Deny|stale|Stale|without|Without|expired|Expired"


def describe(method):
    ref = ""
    m = re.match(r"((?:br|msg|cr|workflow)\d+(?:_\d+)*)_(.*)", method)
    if m:
        ref, method = m.group(1), m.group(2)
    words = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", method).replace("_", " ").lower()
    words = words[:1].upper() + words[1:]
    return words, srs_ref(ref)


def srs_ref(ref):
    if not ref:
        return ""
    kind = re.match(r"[a-z]+", ref).group(0)
    nums = re.findall(r"\d+", ref)
    if kind == "workflow":
        out = []
        for n in nums:
            out.append("WF 1.2." + n[2:] if n.startswith("12") and len(n) > 2 else "WF 1.2." + n)
        return ", ".join(out)
    return ", ".join(f"{kind.upper()}-{int(n):02d}" for n in nums)


METHOD_MODULE = {
    "aFewWrongPasswordsDoNotBlockTheRightOne": "MF01",
    "repeatedWrongPasswordsLockTheSignInEvenWithTheRightPassword": "MF01",
    "aDecisionMustBeUpheldOrOverturned": "MF04", "aSecondDecisionIsRefused": "MF04",
    "msg37_aSecondDecisionIsRefused": "MF04", "takeShowsWhoIsGoingButDoesNotLock": "MF04",
    "aManagerCannotEditOutsidersOrThemselves": "MF01", "aFileThatIsNotAnImageIsRefused": "MF08",
    "newPhotoResetsEnrolmentAndClearsTheOldVector": "MF01", "br70_theLockJobLocksLastMonth": "MF07",
    "aZoneMayHaveSeveralCamerasAndEachReportsInIt": "MF02",
}


def module_of(cls, method):
    if method in METHOD_MODULE:
        return METHOD_MODULE[method]
    if cls in CLASS_MODULE:
        return CLASS_MODULE[cls]
    for mod, pattern in KEYWORDS:
        if re.search(pattern, method, re.I):
            return mod
    return "MF09"


def type_of(cls, method):
    if cls in ("SchemaChecksSqlTest", "RepositoryIntegrationTest", "ServiceAtomicityTest") or re.search(INTEGRITY, method):
        return "Data and persistence integrity"
    if cls in ("LoginIntegrationTest", "AccountSecurityTest", "DetectionApiWithoutKeyTest") or re.search(SECURITY, method):
        return "Security and authorization"
    return "Functional (business rule)"


def nab(method):
    if re.search(BOUNDARY, method):
        return "B"
    if re.search(ABNORMAL, method):
        return "A"
    return "N"


def erp_cases():
    cases = []
    for path in sorted(glob.glob(ERP + "/target/surefire-reports/TEST-*.xml")):
        for tc in ET.parse(path).getroot().iter("testcase"):
            cls = tc.get("classname").replace("orochi.steelsight.", "")
            method = tc.get("name")
            failed = tc.find("failure") is not None or tc.find("error") is not None
            skipped = tc.find("skipped") is not None
            level = "Unit" if cls in UNIT_CLASSES else "System" if cls in SYSTEM_CLASSES else "Integration"
            desc, ref = describe(method)
            cases.append(dict(cls=cls, method=method, desc=desc, ref=ref, module=module_of(cls, method), level=level,
                              ttype=type_of(cls, method), nab=nab(method),
                              result="Skipped" if skipped else "Failed" if failed else "Passed",
                              time=float(tc.get("time") or 0)))
    return cases


DET_MODULE = {
    "test_engine_flow": "DET-VIS", "test_engine_fps": "DET-VIS", "test_hikvision": "DET-VIS", "test_camera_add": "DET-VIS",
    "test_live_still": "DET-WEB", "test_desktop_layout": "DET-WEB", "test_launch_token": "DET-WEB",
    "test_gate": "DET-IDG", "test_gate_board": "DET-IDG", "test_scanner": "DET-IDG", "test_faceid_debug": "DET-IDG",
    "test_faceid_providers": "DET-IDG", "test_gallery_cache": "DET-IDG", "test_gallery_names": "DET-IDG",
    "test_vest_marker": "DET-IDG", "test_vest_ocr": "DET-IDG",
    "test_safety": "DET-SAF", "test_srs_desktop": "DET-SAF", "test_zone_summary": "DET-SAF",
    "test_outbox": "DET-SYN", "test_sync": "DET-SYN", "test_erp_mapping": "DET-SYN", "test_erp_queue_cleanup": "DET-SYN",
    "test_report_day": "DET-REC",
}
DET_NAMES = {
    "DET-VIS": "Vision: cameras, detector, tracker", "DET-IDG": "Identity and gate", "DET-SAF": "PPE rules and voice",
    "DET-SYN": "Outbox and sync", "DET-REC": "Local records and reports", "DET-WEB": "Desktop pages",
}


def det_cases():
    rounds = []
    for path in (DET1, DET2):
        res = {}
        for tc in ET.parse(path).getroot().iter("testcase"):
            key = (tc.get("classname"), tc.get("name"))
            failed = tc.find("failure") is not None or tc.find("error") is not None
            res[key] = "Failed" if failed else "Skipped" if tc.find("skipped") is not None else "Passed"
        rounds.append(res)
    cases = []
    for key in rounds[0]:
        cls, name = key
        file = cls.split(".")[-1]
        desc = name.removeprefix("test_").replace("_", " ")
        desc = desc[:1].upper() + desc[1:]
        cases.append(dict(cls=cls, method=name, desc=desc, module=DET_MODULE.get(file, "DET-DPL"),
                          r1=rounds[0][key], r2=rounds[1].get(key, "Not run"), nab=nab(name.replace("_", " "))))
    return cases


# ---------------------------------------------------------------------------------------------------
HEAD_FILL = PatternFill("solid", fgColor="D9E2F3")


def sheet(wb, title, headers, rows, widths):
    ws = wb.create_sheet(title[:31])
    ws.append(headers)
    for c in ws[1]:
        c.font = Font(bold=True)
        c.fill = HEAD_FILL
        c.alignment = Alignment(wrap_text=True, vertical="top")
    for r in rows:
        ws.append(r)
    for i, w in enumerate(widths, 1):
        ws.column_dimensions[get_column_letter(i)].width = w
    for row in ws.iter_rows(min_row=2):
        for c in row:
            c.alignment = Alignment(wrap_text=True, vertical="top")
    ws.freeze_panes = "A2"
    return ws


erp = erp_cases()
det = det_cases()

# Unit workbook --------------------------------------------------------------------------------------
wb = Workbook()
wb.remove(wb.active)
unit = [c for c in erp if c["level"] == "Unit"]
summary = []
for mod, name in MODULES.items():
    cs = [c for c in unit if c["module"] == mod]
    if not cs:
        continue
    n = collections.Counter(c["nab"] for c in cs)
    summary.append([f"{mod} - {name}", len(cs), sum(c["result"] == "Passed" for c in cs),
                    sum(c["result"] == "Failed" for c in cs), n["N"], n["A"], n["B"]])
summary.append(["TOTAL", len(unit), sum(c["result"] == "Passed" for c in unit), sum(c["result"] == "Failed" for c in unit),
                sum(c["nab"] == "N" for c in unit), sum(c["nab"] == "A" for c in unit), sum(c["nab"] == "B" for c in unit)])
sheet(wb, "Summary", ["Module", "Cases", "Passed", "Failed", "Normal", "Abnormal", "Boundary"], summary, [50, 8, 8, 8, 9, 9, 9])
for mod, name in MODULES.items():
    cs = [c for c in unit if c["module"] == mod]
    if not cs:
        continue
    rows = [[f"{mod}-UT-{i:03d}", c["desc"], c["ref"], f"{c['cls']}#{c['method']}", c["nab"], c["result"], RUN_DATE, ""]
            for i, c in enumerate(cs, 1)]
    sheet(wb, f"{mod} unit", ["UTCID", "Condition and confirmation (from the test)", "SRS rule", "Test (class#method)",
                               "Type N/A/B", "Result", "Executed date", "Defect ID"], rows, [14, 60, 12, 55, 9, 9, 12, 12])
wb.save(ERP + "/04_testing/Report5_Unit_Test.xlsx")

# Test report workbook (integration, system, detection) ----------------------------------------------
wb = Workbook()
wb.remove(wb.active)
integ = [c for c in erp if c["level"] == "Integration"]
system = [c for c in erp if c["level"] == "System"]
rows = []
for mod, name in MODULES.items():
    ci = [c for c in integ if c["module"] == mod]
    cs = [c for c in system if c["module"] == mod]
    rows.append([f"{mod} - {name}", len(ci), sum(c["result"] == "Passed" for c in ci), len(cs),
                 sum(c["result"] == "Passed" for c in cs)])
rows.append(["TOTAL", len(integ), sum(c["result"] == "Passed" for c in integ), len(system),
             sum(c["result"] == "Passed" for c in system)])
sheet(wb, "Summary", ["Module", "Integration cases", "Passed", "System (automated) cases", "Passed"], rows, [50, 12, 9, 12, 9])

PRE_INT = "Spring Boot context; PostgreSQL 17 + pgvector in Testcontainers; Flyway V1-V26; seeded roles and accounts"
for mod, name in MODULES.items():
    cs = [c for c in integ if c["module"] == mod]
    if not cs:
        continue
    out = [[f"{mod}-IT-{i:03d}", c["desc"], c["ref"], c["ttype"], f"Automated: {c['cls']}#{c['method']}", PRE_INT,
            "As described; asserted by the test", c["result"], RUN_DATE, "Maven Surefire", ""] for i, c in enumerate(cs, 1)]
    sheet(wb, f"{mod} integration", ["Case ID", "Description", "SRS rule", "Testing type", "Procedure", "Pre-conditions",
                                     "Expected result", "Round result", "Date", "Tester", "Defect ID"], out,
          [14, 55, 12, 22, 50, 30, 22, 10, 11, 14, 10])
out = [[f"ST-AUTO-{i:03d}", c["desc"], c["ref"], f"Automated: {c['cls']}#{c['method']}",
        "Several roles sign in and drive the workflow over HTTP (MockMvc) on a real database", c["result"], RUN_DATE]
       for i, c in enumerate(system, 1)]
sheet(wb, "System automated", ["Case ID", "Workflow", "SRS", "Procedure", "Pre-conditions", "Result", "Date"], out,
      [13, 60, 14, 55, 40, 9, 11])

MANUAL = [
    ("WF 1.2.1", "Log in, wrong password five times, forced password change on first sign-in", "Browser, deployed ERP"),
    ("WF 1.2.2", "Admin creates an account with consent and FRONT/LEFT/RIGHT photos; the person links Zalo by scanning the QR", "Browser + phone with Zalo"),
    ("WF 1.2.3", "Admin creates a team; the leader plans next week before Saturday 18:00; schedule arrives on Zalo", "Browser + phone"),
    ("WF 1.2.4", "A worker checks in and out at CAM-01; the visit shows on My Attendance; Zalo check-in and check-out messages", "Site laptop, CAM-01, phone"),
    ("WF 1.2.5", "Gate PPE check at CAM-02: a worker without a hard hat gets a spoken reminder and a violation with a suggestion", "Site laptop, CAM-02"),
    ("WF 1.2.6", "Work-zone PPE at CAM-03/04: a violation alerts the HSE Officer on duty in the ERP and on Zalo", "Site laptop, CAM-03, phone"),
    ("WF 1.2.7", "HSE takes, confirms or dismisses a violation in the Review Queue; an unreviewed one becomes OVERDUE at 23:00", "Browser"),
    ("WF 1.2.8", "The worker appeals; the Admin asks HSE for facts and decides UPHELD or OVERTURNED", "Browser"),
    ("WF 1.2.9", "A worker reports an attendance error; the Admin approves and the visit is corrected", "Browser"),
    ("WF 1.2.10", "A forgotten check-out is flagged Incomplete; the worker explains; the Admin approves", "Browser"),
    ("WF 1.2.11", "Time after the shift end becomes pending overtime; the Admin adds an overtime shift within 48 hours", "Browser"),
    ("WF 1.2.12", "A leave request is sent, read by the leader and approved by the Admin", "Browser"),
    ("WF 1.2.13", "The month locks on the 2nd working day; the leader and the Admin export the report and open it in Excel", "Browser + Excel"),
    ("WF 1.2.14", "The laptop goes offline; events wait in the outbox and arrive in order when it reconnects; Zalo retries end in FAILED for a blocked bot", "Site laptop, network off/on"),
    ("UI", "Every page in English and Vietnamese, light and dark theme, on a phone width", "Browser, phone"),
]
out = [[f"ST-MAN-{i:03d}", ref, desc, env, "Not run", "", "", ""] for i, (ref, desc, env) in enumerate(MANUAL, 1)]
sheet(wb, "System manual", ["Case ID", "SRS", "Scenario", "Environment", "Round 1", "Round 2", "Tester", "Defect ID"], out,
      [12, 10, 80, 26, 10, 10, 12, 10])

out = [[f"{c['module']}-PT-{i:03d}", c["desc"], f"{c['cls']}::{c['method']}", c["nab"], c["r1"], c["r2"],
        RUN_DATE, "pytest", "DEF-04" if c["r1"] != "Passed" else ""] for i, c in enumerate(det, 1)]
sheet(wb, "Detection app", ["Case ID", "Description", "Test (module::function)", "Type N/A/B", "Round 1", "Round 2",
                            "Date", "Tester", "Defect ID"], out, [16, 60, 55, 9, 9, 9, 11, 9, 10])
wb.save(ERP + "/04_testing/Report5_Test_Report.xlsx")

# Stats ------------------------------------------------------------------------------------------------
stats = {"erp_total": len(erp), "levels": collections.Counter(c["level"] for c in erp),
         "by_module": {}, "types": collections.Counter(c["ttype"] for c in integ),
         "types_all": collections.Counter((c["level"], c["ttype"]) for c in erp),
         "nab_unit": collections.Counter(c["nab"] for c in unit), "nab_all": collections.Counter(c["nab"] for c in erp),
         "failed": [c for c in erp if c["result"] != "Passed"], "srs_refs": len({c["ref"] for c in erp if c["ref"]}),
         "det_total": len(det), "det_r1": collections.Counter(c["r1"] for c in det),
         "det_r2": collections.Counter(c["r2"] for c in det), "det_by_module": {},
         "classes": len({c["cls"] for c in erp}), "time": round(sum(c["time"] for c in erp), 1)}
for mod in MODULES:
    cs = [c for c in erp if c["module"] == mod]
    stats["by_module"][mod] = {lvl: sum(c["level"] == lvl for c in cs) for lvl in ("Unit", "Integration", "System")}
    stats["by_module"][mod]["nab"] = collections.Counter(c["nab"] for c in cs if c["level"] == "Unit")
    stats["by_module"][mod]["types"] = collections.Counter(c["ttype"] for c in cs if c["level"] == "Integration")
for mod in DET_NAMES:
    cs = [c for c in det if c["module"] == mod]
    stats["det_by_module"][mod] = {"name": DET_NAMES[mod], "cases": len(cs),
                                   "r1": sum(c["r1"] == "Passed" for c in cs), "r2": sum(c["r2"] == "Passed" for c in cs)}
stats["types_all"] = {f"{k[0]}|{k[1]}": v for k, v in stats["types_all"].items()}
json.dump(stats, open(OUT_STATS, "w"), indent=1, default=str)
print(json.dumps({k: stats[k] for k in ("erp_total", "levels", "types", "nab_unit", "det_total", "det_r1", "det_r2", "srs_refs")}, default=str))
for mod, v in stats["by_module"].items():
    print(mod, v["Unit"], v["Integration"], v["System"], dict(v["nab"]), dict(v["types"]))
for mod, v in stats["det_by_module"].items():
    print(mod, v)
