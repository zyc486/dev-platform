import json
import time
from datetime import datetime
from urllib import request, parse, error


BASE = "http://127.0.0.1:8080"


def http_json(method: str, path: str, token: str | None = None, body: dict | None = None):
    url = BASE + path
    data = None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = request.Request(url, method=method, data=data, headers=headers)
    try:
        with request.urlopen(req, timeout=15) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            return json.loads(raw)
    except error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path} -> HTTP {e.code}: {raw[:400]}")


def post(path: str, token: str | None = None, body: dict | None = None):
    return http_json("POST", path, token=token, body=body or {})


def get(path: str, token: str | None = None):
    return http_json("GET", path, token=token, body=None)


def try_register(username: str, password: str):
    try:
        post(
            "/api/user/register",
            body={"username": username, "password": password, "email": f"{username}@test.local"},
        )
    except Exception:
        pass


def login(username: str, password: str) -> str:
    for i in range(10):
        try:
            r = post("/api/user/login", body={"username": username, "password": password})
            if int(r.get("code") or 0) != 200 or not r.get("data") or not r["data"].get("token"):
                raise RuntimeError(f"login failed for {username}: {r}")
            return r["data"]["token"]
        except Exception as e:
            msg = str(e)
            if "HTTP 429" in msg and i < 9:
                time.sleep(min(20, 5 * (i + 1)))
                continue
            raise


def me(token: str) -> dict:
    return get("/api/user/info", token=token)["data"]


def main():
    pw = "Pass@123456"
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    A, B, C, D = f"qaA_{ts}", f"qaB_{ts}", f"qaC_{ts}", f"qaD_{ts}"
    for u in (A, B, C, D):
        try_register(u, pw)

    tA = login(A, pw)
    time.sleep(2)
    tB = login(B, pw)
    time.sleep(2)
    tC = login(C, pw)
    time.sleep(2)
    tD = login(D, pw)

    c_id = me(tC)["id"]
    d_id = me(tD)["id"]

    # A create room (pre-add C as member so C can be promoted to admin)
    r_create = post("/api/chat/room/create", token=tA, body={"name": "验收群聊", "memberUserIds": [c_id]})
    room_id = r_create["data"]["roomId"]
    chat_no = r_create["data"]["chatNo"]
    print(f"roomId={room_id} chatNo={chat_no}")

    # B search and apply
    r_search = get("/api/chat/search?" + parse.urlencode({"chatNo": str(chat_no)}), token=tB)
    print(f"search isMember={r_search['data'].get('isMember')} memberCount={r_search['data'].get('memberCount')}")
    r_apply = post(f"/api/chat/room/{room_id}/apply", token=tB, body={"reason": "想加入测试"})
    apply_id = r_apply["data"]["applyId"]
    print(f"applyId={apply_id} status={r_apply['data'].get('status')}")

    # A approve
    r_applies = get(f"/api/chat/room/{room_id}/applies?" + parse.urlencode({"status": "pending"}), token=tA)
    print(f"pendingApplies={len(r_applies.get('data') or [])}")
    post(f"/api/chat/apply/{apply_id}/review?" + parse.urlencode({"action": "approved"}), token=tA, body={})
    print("apply approved")

    # A set C admin
    post(f"/api/chat/room/{room_id}/members/{c_id}/role", token=tA, body={"role": "admin"})
    print("C set admin")

    # C invite D
    r_inv = post(f"/api/chat/room/{room_id}/invite", token=tC, body={"inviteeUserId": d_id})
    if int(r_inv.get("code") or 0) != 200 or not r_inv.get("data"):
        raise RuntimeError(f"invite failed: {r_inv}")
    invite_id = r_inv["data"]["inviteId"]
    print(f"inviteId={invite_id}")

    # D accept invite
    r_my_inv = get("/api/chat/invites/my?" + parse.urlencode({"status": "pending"}), token=tD)
    print(f"myInvites={len(r_my_inv.get('data') or [])}")
    post(f"/api/chat/invite/{invite_id}/respond?" + parse.urlencode({"action": "accepted"}), token=tD, body={})
    print("invite accepted")

    # D leave
    post(f"/api/chat/room/{room_id}/leave", token=tD, body={})
    print("D left")

    # A close
    post(f"/api/chat/room/{room_id}/close", token=tA, body={})
    print("A closed")

    # Check notifications
    nB = get("/api/message/list", token=tB)["data"] or []
    nD = get("/api/message/list", token=tD)["data"] or []
    typesB = [x.get("type") for x in nB[:5]]
    typesD = [x.get("type") for x in nD[:5]]
    print("B notices types(top5)=" + ",".join([t or "" for t in typesB]))
    print("D notices types(top5)=" + ",".join([t or "" for t in typesD]))
    print("done")


if __name__ == "__main__":
    main()

