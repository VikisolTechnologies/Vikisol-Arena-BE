# Load-test seed: 2,000 people, 50 companies, 10,000 posts, joins, 1,000 jobs, 10,000 chats.
# Dummy data only, for the throwaway local database.
import csv, random, uuid, datetime, json, os
random.seed(42)
OUT = "/tmp/arena-pgload/seed"
now = datetime.datetime.now(datetime.timezone.utc)
B32 = "0123456789bcdefghjkmnpqrstuvwxyz"

def geohash(lat, lng, p=7):
    la, lo = [-90.0, 90.0], [-180.0, 180.0]; h = ""; even = True; bit = 0; ch = 0
    while len(h) < p:
        r = lo if even else la; v = lng if even else lat; mid = (r[0] + r[1]) / 2
        if v >= mid: ch |= 1 << (4 - bit); r[0] = mid
        else: r[1] = mid
        even = not even
        if bit < 4: bit += 1
        else: h += B32[ch]; bit = 0; ch = 0
    return h

def decode(h):
    la, lo = [-90.0, 90.0], [-180.0, 180.0]; even = True
    for c in h:
        cd = B32.index(c)
        for m in (16, 8, 4, 2, 1):
            r = lo if even else la; mid = (r[0] + r[1]) / 2
            if cd & m: r[0] = mid
            else: r[1] = mid
            even = not even
    return (la[0] + la[1]) / 2, (lo[0] + lo[1]) / 2

CITIES = [(17.40, 78.45, "Hyderabad", 0.45), (12.97, 77.59, "Bengaluru", 0.2), (19.07, 72.88, "Mumbai", 0.15),
          (28.61, 77.21, "Delhi", 0.12), (13.08, 80.27, "Chennai", 0.08)]
def spot():
    r = random.random(); acc = 0
    for lat, lng, name, w in CITIES:
        acc += w
        if r <= acc: break
    return lat + random.uniform(-0.25, 0.25), lng + random.uniform(-0.25, 0.25), name

def ts(days_ago_max):
    return (now - datetime.timedelta(seconds=random.randint(0, days_ago_max * 86400))).isoformat()

def w(name, rows):
    with open(os.path.join(OUT, name + ".csv"), "w", newline="") as f:
        csv.writer(f).writerows(rows)

BCRYPT = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BR5sXHn1e8Y4O6e9bGmhFzZzHh8y"  # never used to sign in
INDUSTRIES = ["ENGINEERING", "DESIGN", "SALES", "HEALTHCARE", "LOGISTICS"]
WORDS = ["cricket", "badminton", "run", "yoga", "trek", "chess", "potluck", "coding", "design", "music", "football", "cycling",
         "ladder", "moving", "tutor", "plants", "photo", "book", "quiz", "cleanup"]

users, profiles, posts, tags, joins, follows = [], [], [], [], [], []
ids = []
for i in range(2000):
    uid = str(uuid.uuid4()); ids.append(uid); lat, lng, city = spot(); g = geohash(lat, lng); clat, clng = decode(g)
    c = ts(120)
    users.append([uid, c, c, f"load{i}@load.invalid", f"Person {i}", BCRYPT, "TALENT", "1990-01-01"])
    profiles.append([str(uuid.uuid4()), c, c, "SUPERVISED", "🙂", 50, False, random.random() < 0.7, random.randint(0, 15),
                     random.choice(INDUSTRIES), city, f"Person {i}", 0, random.random() < 0.3, random.choice(["Designer", "Engineer", "Teacher", "Analyst"]),
                     uid, "CITY", g, clat, clng, city])
companies, recruiters = [], []
for i in range(50):
    uid = str(uuid.uuid4()); c = ts(200)
    users.append([uid, c, c, f"company{i}@load.invalid", f"Company admin {i}", BCRYPT, "COMPANY_ADMIN", "1985-01-01"])
    companies.append([str(uuid.uuid4()), c, c, f"Company {i}", random.choice(INDUSTRIES), "🏢", "ENTERPRISE", 10, 1, "S_11_50", 100, 0, uid])
    recruiters.append(uid)

INTENTS = ["ACTIVITY"] * 60 + ["ASK"] * 20 + ["UPDATE"] * 10 + ["OFFER"] * 5 + ["COLLAB"] * 5
post_ids = []
for i in range(10000):
    pid = str(uuid.uuid4()); intent = random.choice(INTENTS); author = random.choice(ids); lat, lng, city = spot()
    g = geohash(lat, lng); clat, clng = decode(g); c = ts(60)
    status = random.choices(["OPEN", "FULL", "CLOSED", "EXPIRED"], [80, 5, 10, 5])[0]
    starts = (now + datetime.timedelta(hours=random.randint(-48, 24 * 14))).isoformat() if intent == "ACTIVITY" else ""
    cap = random.choice([4, 6, 8, 10, 20]) if intent == "ACTIVITY" else ""
    body = " ".join(random.sample(WORDS, 4)) + f" in {city} #{i}"
    posts.append([pid, c, c, author, intent, f"Post {i}", body, city, "GLOBAL", random.choice(["PUBLIC", "APPROVAL"]),
                  cap, 0, status, starts, g, clat, clng, False])
    post_ids.append((pid, intent, author))
    for t in random.sample(WORDS, 2): tags.append([pid, t])
pairs = set()
for pid, intent, author in post_ids:
    if intent in ("ACTIVITY", "COLLAB"):
        for u in random.sample(ids, random.randint(0, 6)):
            if u != author and (pid, u) not in pairs:
                pairs.add((pid, u)); c = ts(30)
                joins.append([str(uuid.uuid4()), c, c, pid, u, random.choice(["APPROVED", "APPROVED", "PENDING"])])
fpairs = set()
for u in ids:
    for v in random.sample(ids, 20):
        if u != v and (u, v) not in fpairs:
            fpairs.add((u, v)); c = ts(90); follows.append([str(uuid.uuid4()), c, c, u, v, "USER"])

jobs, skills = [], []
for i in range(1000):
    jid = str(uuid.uuid4()); c = ts(60); co = random.choice(companies)
    jobs.append([jid, c, c, "Do good work", "FULL_TIME", co[4], random.choice(["Hyderabad", "Bengaluru", "Remote"]), random.random() < 0.3,
                 1200000, 600000, random.choices(["OPEN", "PAUSED", "CLOSED"], [85, 5, 10])[0], f"Job {i} " + random.choice(["Designer", "Engineer", "Analyst"]),
                 co[0], random.choice(["ONSITE", "HYBRID", "REMOTE"])])
    for s in random.sample(["Java", "React", "Figma", "SQL", "Excel", "Python"], 3): skills.append([jid, s])

convs, msgs = [], []
cpairs = set()
for u in ids[:500]:
    for v in random.sample(ids, 20):
        key = tuple(sorted((u, v)))
        if u == v or key in cpairs: continue
        cpairs.add(key); cid = str(uuid.uuid4()); c = now - datetime.timedelta(days=random.randint(1, 60)); last = c
        for k in range(10):
            last = last + datetime.timedelta(minutes=random.randint(1, 600))
            msgs.append([str(uuid.uuid4()), last.isoformat(), last.isoformat(), f"message {k} " + random.choice(WORDS), cid, random.choice([u, v])])
        convs.append([cid, c.isoformat(), c.isoformat(), last.isoformat(), key[0], key[1], "Direct message"])

w("users", users); w("profiles", profiles); w("companies", companies); w("posts", posts); w("tags", tags); w("joins", joins)
w("follows", follows); w("jobs", jobs); w("skills", skills); w("convs", convs); w("msgs", msgs)
# The first 50 people (all have conversations) are the load-test users.
json.dump([{"id": u, "email": f"load{i}@load.invalid"} for i, u in enumerate(ids[:50])], open(os.path.join(OUT, "vus.json"), "w"))
print({k: len(v) for k, v in dict(users=users, posts=posts, joins=joins, follows=follows, jobs=jobs, convs=convs, msgs=msgs).items()})
