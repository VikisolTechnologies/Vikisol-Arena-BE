# Mints HS256 access tokens for the 50 load-test people with the throwaway load-test secret
# (the same dummy JWT_SECRET run-app.sh passes). Valid for 24 h.
import base64, hmac, hashlib, json, time, uuid
SECRET = b"load-test-only-secret-0123456789abcdef0123456789abcdef"
old = json.load(open("/tmp/arena-pgload/seed/tokens.json"))
def b64(d): return base64.urlsafe_b64encode(d).rstrip(b"=")
def claims(tok):
    p = tok.split(".")[1]; return json.loads(base64.urlsafe_b64decode(p + "=" * (-len(p) % 4)))
now = int(time.time()); out = []
for row in old:
    c = claims(row["token"]); c.update(jti=str(uuid.uuid4()), iat=now, nbf=now, exp=now + 86400)
    h = b64(json.dumps({"alg": "HS256"}).encode()); p = b64(json.dumps(c).encode())
    sig = b64(hmac.new(SECRET, h + b"." + p, hashlib.sha256).digest())
    out.append({"id": row["id"], "token": (h + b"." + p + b"." + sig).decode()})
json.dump(out, open("/tmp/arena-pgload/seed/tokens.json", "w"))
print(len(out), "tokens; claims:", sorted(claims(out[0]["token"]).keys()))
