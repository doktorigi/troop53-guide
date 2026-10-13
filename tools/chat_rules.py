"""Deploy firestore.rules and check them against the live Firebase project.

    python tools/chat_rules.py deploy   # publish firestore.rules
    python tools/chat_rules.py check    # create throwaway accounts, assert allow/deny, clean up
    python tools/chat_rules.py leader EMAIL   # approve EMAIL's account and make it a leader

Needs the Google Cloud CLI signed in (`gcloud auth login`) as a project owner.
Admin calls use that OAuth token and bypass the rules; test-user calls go through them.
"""
import json, os, pathlib, shutil, subprocess, sys, uuid, urllib.error, urllib.request

PROJECT = "troop53-guide"
ROOT = pathlib.Path(__file__).resolve().parent.parent
KEY = json.loads((ROOT / "app/google-services.json").read_text())["client"][0]["api_key"][0]["current_key"]
NAME = f"projects/{PROJECT}/databases/(default)/documents/"
DOCS = "https://firestore.googleapis.com/v1/" + NAME
AUTH = "https://identitytoolkit.googleapis.com/v1/accounts:"


def gcloud_token():
    exe = shutil.which("gcloud") or os.path.expandvars(r"%LOCALAPPDATA%\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd")
    return subprocess.run([exe, "auth", "print-access-token"], capture_output=True, text=True, check=True).stdout.strip()


ADMIN = None


def call(method, url, body=None, token=None, admin=False):
    headers = {"Content-Type": "application/json"}
    if admin:
        headers |= {"Authorization": f"Bearer {ADMIN}", "x-goog-user-project": PROJECT}
    elif token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, None if body is None else json.dumps(body).encode(), headers, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"{}")


def enc(v):
    if isinstance(v, bool): return {"booleanValue": v}
    if isinstance(v, str): return {"stringValue": v}
    if isinstance(v, list): return {"arrayValue": {"values": [enc(x) for x in v]}}
    raise TypeError(v)


def create(path, fields, time_field=None, server_time=True):
    w = {"update": {"name": NAME + path, "fields": {k: enc(v) for k, v in fields.items()}}, "currentDocument": {"exists": False}}
    if time_field and server_time:
        w["updateTransforms"] = [{"fieldPath": time_field, "setToServerValue": "REQUEST_TIME"}]
    elif time_field:
        w["update"]["fields"][time_field] = {"timestampValue": "2020-01-01T00:00:00Z"}
    return w


def update(path, fields):
    return {"update": {"name": NAME + path, "fields": {k: enc(v) for k, v in fields.items()}},
            "updateMask": {"fieldPaths": list(fields)}, "currentDocument": {"exists": True}}


def commit(token, *writes, admin=False):
    return call("POST", DOCS + ":commit", {"writes": list(writes)}, token, admin)[0]


def deploy():
    source = (ROOT / "firestore.rules").read_text()
    base = f"https://firebaserules.googleapis.com/v1/projects/{PROJECT}"
    status, r = call("POST", base + "/rulesets", {"source": {"files": [{"name": "firestore.rules", "content": source}]}}, admin=True)
    if status != 200:
        sys.exit(f"Ruleset rejected: {json.dumps(r, indent=1)}")
    release = {"name": f"projects/{PROJECT}/releases/cloud.firestore", "rulesetName": r["name"]}
    status, _ = call("PATCH", base + "/releases/cloud.firestore", {"release": release}, admin=True)
    if status == 404:
        status, _ = call("POST", base + "/releases", release, admin=True)
    if status != 200:
        sys.exit(f"Release failed: {status}")
    print("Deployed", r["name"])


def check():
    run = uuid.uuid4().hex[:8]
    users = {}

    def signup(label, name, role, approve=False, leader=False):
        status, r = call("POST", f"{AUTH}signUp?key={KEY}", {"email": f"rules-{run}-{label}@example.com", "password": "rules-" + run, "returnSecureToken": True})
        assert status == 200, r
        uid, tok = r["localId"], r["idToken"]
        users[label] = (uid, tok)
        expect(f"{label} creates own unapproved profile", 200, commit(tok, create(f"users/{uid}", {"name": name, "role": role, "approved": False, "leader": False})))
        if approve:
            assert commit(None, update(f"users/{uid}", {"approved": True, "leader": leader}), admin=True) == 200
        return uid, tok

    failures = []

    def expect(what, want, got):
        ok = (got == 200) == (want == 200)
        print(("PASS " if ok else "FAIL ") + what + f" (HTTP {got})")
        if not ok: failures.append(what)

    try:
        a, ta = signup("leader", "Leader A", "adult", approve=True, leader=True)
        b, tb = signup("adult", "Adult B", "adult", approve=True)
        s1, t1 = signup("scout1", "Scout One", "scout", approve=True)
        s2, t2 = signup("scout2", "Scout Two", "scout", approve=True)
        p, tp = signup("pending", "Pending P", "scout")

        status, r = call("POST", f"{AUTH}signUp?key={KEY}", {"email": f"rules-{run}-sneaky@example.com", "password": "rules-" + run, "returnSecureToken": True})
        users["sneaky"] = (r["localId"], r["idToken"])
        expect("new account cannot self-approve", 403, commit(r["idToken"], create(f"users/{r['localId']}", {"name": "Sneaky", "role": "adult", "approved": True, "leader": True})))

        expect("pending user cannot list members", 403, call("GET", DOCS + "users", token=tp)[0])
        expect("pending user can read own profile", 200, call("GET", DOCS + f"users/{p}", token=tp)[0])
        expect("scout cannot approve someone", 403, commit(t1, update(f"users/{p}", {"approved": True, "leader": False})))
        expect("scout cannot make self leader", 403, commit(t1, update(f"users/{s1}", {"approved": True, "leader": True})))
        expect("leader cannot make a scout a leader", 403, commit(ta, update(f"users/{s1}", {"approved": True, "leader": True})))
        expect("leader approves pending user", 200, commit(ta, update(f"users/{p}", {"approved": True, "leader": False})))
        expect("approved user lists members", 200, call("GET", DOCS + "users", token=t1)[0])

        expect("adult + scout two-person group is blocked", 403, commit(tb, create(f"groups/g{run}x", {"name": "DM", "members": [b, s1], "createdBy": b}, "lastAt")))
        expect("cannot create group without self", 403, commit(t1, create(f"groups/g{run}y", {"name": "Y", "members": [s2, p], "createdBy": s1}, "lastAt")))
        expect("cannot fake lastAt", 403, commit(t1, create(f"groups/g{run}z", {"name": "Z", "members": [s1, s2], "createdBy": s1}, "lastAt", server_time=False)))
        sg, ag = f"g{run}s", f"g{run}a"
        expect("two scouts can make a group", 200, commit(t1, create(f"groups/{sg}", {"name": "Scouts", "members": [s1, s2], "createdBy": s1}, "lastAt")))
        expect("adult can make a 3-person group", 200, commit(tb, create(f"groups/{ag}", {"name": "Patrol", "members": [b, s1, s2], "createdBy": b}, "lastAt")))

        expect("non-member adult cannot read scouts' group", 403, call("GET", DOCS + f"groups/{sg}", token=tb)[0])
        expect("leader can read any group", 200, call("GET", DOCS + f"groups/{sg}", token=ta)[0])
        expect("member can read own group", 200, call("GET", DOCS + f"groups/{sg}", token=t2)[0])

        def msg(tok, uid, name, gid, mid, server_time=True):
            return commit(tok, create(f"groups/{gid}/messages/{mid}", {"uid": uid, "name": name, "text": "hi"}, "at", server_time),
                          {"transform": {"document": NAME + f"groups/{gid}", "fieldTransforms": [{"fieldPath": "lastAt", "setToServerValue": "REQUEST_TIME"}]}})

        expect("member posts a message", 200, msg(t1, s1, "Scout One", sg, "m1"))
        expect("non-member cannot post", 403, msg(tb, b, "Adult B", sg, "m2"))
        expect("cannot post as someone else", 403, msg(t2, s1, "Scout One", sg, "m3"))
        expect("cannot use a fake display name", 403, msg(t2, s2, "Leader A", sg, "m4"))
        expect("cannot fake message time", 403, msg(t2, s2, "Scout Two", sg, "m5", server_time=False))
        expect("non-member adult cannot read messages", 403, call("POST", DOCS + f"groups/{sg}:runQuery", {"structuredQuery": {"from": [{"collectionId": "messages"}]}}, tb)[0])
        expect("member cannot delete another's message", 403, call("DELETE", DOCS + f"groups/{sg}/messages/m1", token=t2)[0])
        expect("leader deletes a message", 200, call("DELETE", DOCS + f"groups/{sg}/messages/m1", token=ta)[0])

        expect("leaving cannot leave an adult alone with a scout", 403, commit(t2, update(f"groups/{ag}", {"name": "Patrol", "members": [b, s1]})))
        expect("member adds someone", 200, commit(t1, update(f"groups/{ag}", {"name": "Patrol", "members": [b, s1, s2, p]})))
        expect("non-creator cannot delete group", 403, call("DELETE", DOCS + f"groups/{ag}", token=t1)[0])
        expect("creator deletes group", 200, call("DELETE", DOCS + f"groups/{ag}", token=tb)[0])
    finally:
        for gid in (f"g{run}s", f"g{run}a"):
            call("DELETE", DOCS + f"groups/{gid}/messages/m1", admin=True)
            call("DELETE", DOCS + f"groups/{gid}", admin=True)
        for uid, tok in users.values():
            call("DELETE", DOCS + f"users/{uid}", admin=True)
            call("POST", f"{AUTH}delete?key={KEY}", {"idToken": tok})
    if failures:
        sys.exit(f"{len(failures)} rule check(s) failed")
    print("All rule checks passed; test accounts removed.")


def make_leader(email):
    status, r = call("POST", f"https://identitytoolkit.googleapis.com/v1/projects/{PROJECT}/accounts:lookup", {"email": [email]}, admin=True)
    found = r.get("users") or sys.exit(f"No account for {email}. Create it in the app first.")
    uid = found[0]["localId"]
    status = commit(None, update(f"users/{uid}", {"approved": True, "leader": True}), admin=True)
    print("Leader set" if status == 200 else f"Failed ({status}); the user must finish sign-up in the app first.")


if __name__ == "__main__":
    ADMIN = gcloud_token()
    cmd = sys.argv[1:] or ["check"]
    {"deploy": lambda: deploy(), "check": lambda: check(), "leader": lambda: make_leader(cmd[1])}[cmd[0]]()
