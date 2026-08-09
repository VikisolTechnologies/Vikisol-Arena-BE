# ARENA — VISUAL DESIGN SYSTEM (Luxury Marketplace)
### Paste together with ARENA-MASTER-ARCHITECTURE.md. This file REPLACES that file's PART 3
### and overrides every visual/styling instruction in it. Architecture, routes, data model,
### API, page structure and behaviour in that file stay exactly as written.

---

# 1. THE CHANGE IN ONE LINE
Arena stops looking like a dark SaaS dashboard with neon orange and becomes a **premium,
light, image-rich marketplace that feels like a social app** — Apple × Airbnb × premium
fashion editorial, with the ease of a modern chat app.

**Two personalities, deliberately:**
- **Marketing Arena** (`/`, `/how-it-works`, `/for-companies`, `/pricing`, `/auth`) —
  stays cinematic and dark: black canvas, ivory type, champagne accents, the 3D orb.
- **Product Arena** (everything after sign-in) — **light**: warm ivory canvas, white cards,
  black type, champagne/gold accents, photography everywhere.
Signing in is a deliberate transition from the cinematic dark into the bright product.

**Kill list — remove from the product entirely:** neon orange `#FF6B35`/`#FF8A5B` as a UI
colour, dark cards on dark backgrounds, glowing borders, heavy glassmorphism, generic SaaS
card grids, LinkedIn/Naukri-style list rows, and large expanses of empty black.

**Ratio target:** 60–70% ivory/white · 15–20% black (type, nav, primary buttons) · 5–10%
champagne/gold accents · the rest is photography and imagery.
**Content-to-space target:** ~80% content, ~20% breathing room. Never 30/70 like today.

---

# 2. COLOUR TOKENS (implement as CSS variables + Tailwind theme; no hardcoded hex anywhere)

```css
/* PRODUCT (light) — default theme */
--canvas:        #F7F1EA;  /* app background — warm ivory */
--canvas-alt:    #FBF7F2;  /* subtle banding, sticky headers */
--surface:       #FFFFFF;  /* cards, sheets, inputs */
--surface-sunk:  #F2ECE4;  /* wells, chat input tray, skeletons */

--ink:           #000000;  /* primary type, primary buttons */
--ink-800:       #111111;  /* headings on ivory */
--ink-600:       #2F2F2F;  /* strong body */
--ink-400:       #6B6B6B;  /* secondary type */
--ink-300:       #9A9A9A;  /* tertiary, placeholders */

--line:          rgba(0,0,0,0.08);
--line-strong:   rgba(0,0,0,0.14);

--champagne:     #FFE6BC;  /* fills, unread, highlights */
--gold-soft:     #F3D79B;  /* gradient stop, hover wash */
--gold:          #D6A84F;  /* accent, borders, icons */
--gold-deep:     #A87C2C;  /* gold TEXT on ivory (accessible) */

--success:       #2E7D5B;
--warning:       #B8791F;
--danger:        #C0392B;
--info:          #2F5D8C;

/* MARKETING (dark) — only on public pages */
--night:         #000000;
--night-800:     #111111;
--night-600:     #2F2F2F;
--night-ink:     #F7F1EA;  /* type on black */
```

**Gold discipline — gold is meaning, not decoration.** Use it ONLY for: verified badges,
premium/Pro states, high match scores, featured/promoted content, achievements, the Arena
Score, the selected navigation item, and premium CTAs. If gold appears more than ~3 times
on a screen, it's wrong.

**Contrast rules (do not break).** Body and UI text is `--ink`/`--ink-600` on ivory —
never gold. Gold text is allowed only as `--gold-deep` at 14px+ semibold. Champagne and
gold are *fills* with black text on top. Every text/background pair must pass WCAG AA
(4.5:1 body, 3:1 large). Verify with a contrast checker, not by eye.

---

# 3. TYPOGRAPHY — Poppins
Load via `next/font` (self-hosted, `display: swap`, preload). Weights: 400, 500, 600, 700
only. No other families in the product (Space Grotesk may remain on the marketing hero
only if it reads as editorial; otherwise Poppins everywhere).

| Token | Size/Line | Weight | Use |
|---|---|---|---|
| display | 44/52 | 600 | marketing hero, profile name |
| h1 | 32/40 | 600 | page titles |
| h2 | 24/32 | 600 | section titles, post titles |
| h3 | 20/28 | 600 | card titles |
| body-lg | 17/28 | 400 | post body, long copy |
| body | 15/24 | 400 | default UI text |
| small | 13.5/20 | 400 | meta, timestamps |
| label | 12/16 | 600 | uppercase, letter-spacing .08em — badges, section labels |

Numbers (salary, match %, Arena Score) use tabular figures at 600.

---

# 4. FORM, DEPTH, RHYTHM
**Radii:** chips/badges 999 · buttons 999 (pill) or 14 for wide blocks · inputs 14 ·
cards 20 · feature cards & sheets 28 · media 16 · avatars 999.
**Shadows (soft and expensive, never heavy):**
`sm 0 1px 2px rgba(0,0,0,.04)` · `md 0 6px 20px rgba(0,0,0,.06)` ·
`lg 0 14px 40px rgba(0,0,0,.10)` · `xl 0 28px 70px rgba(0,0,0,.14)`.
Cards use `sm` at rest, `md` on hover with a 3–4px rise. Modals/sheets use `xl`.
**Borders:** 1px `--line`. Selected/active elements get a 1px `--gold` border, never a glow.
**Spacing:** 4/8 grid. Card padding 20–24. Gap between cards 14–16. Section rhythm 40–56.
**Layout widths:** feed column 640 centered · app shell 1440 max · right rail 320 ·
side nav 248 (icon rail 76 on tablet).

---

# 5. IMAGERY IS PART OF THE UI (this is what removes the "empty" feeling)
Every card that can carry an image, does: avatars on every author row, company logos on
every company row, cover images on profiles and company pages, project/job thumbnails,
post photos and video, a static map thumbnail on activity cards, and illustrated (not
text-only) empty states. Images sit at radius 16 with a 1px `--line` inset. Always set
explicit width/height (zero layout shift). Portraits use a subtle warm duotone-free
treatment — no filters, real photography.
Where a user has no photo: deterministic champagne/ivory initial avatar with black
monogram — never a grey silhouette.

---

# 6. CORE COMPONENTS (restyle every one)

**Buttons** (height 48 desktop / 52 mobile, pill, 15/600, icon 18):
- *Primary* — black fill, white text. Hover: lift 2px + champagne outer wash. Active: 0.97.
- *Premium* — gradient `--champagne → --gold`, black text. For Get matched / Upgrade /
  Promote / premium CTAs only.
- *Secondary* — transparent, 1px `--ink` border, black text. Hover: `--surface-sunk` fill.
- *Ghost* — no border, `--ink-600` text, hover `--surface-sunk`.
- *Danger* — `--danger` text, 1px danger border; solid fill only in confirm dialogs.
- Disabled: 40% opacity, no shadow. Loading: inline spinner, label stays, width locked.

**Inputs** — white fill, 1px `--line`, radius 14, height 48, 15/400, placeholder
`--ink-300`. Focus: 1px `--ink` border + 3px champagne ring. Error: danger border + message
below. Search fields are pills with a leading icon.

**Chips / filters** — pill, 13.5/500. Rest: white, 1px `--line`. Selected: black fill,
white text (or champagne fill with black text for "premium" filters). Removable chips get
an × with a 32px hit area.

**Badges** — `label` type. Neutral: `--surface-sunk` + `--ink-600`. Type badges use a 1px
tinted border + 8% tint fill. **Verified / Featured / Top match / Pro**: champagne fill,
black text, tiny gold icon.

**Cards** — white, radius 20, 1px `--line`, shadow `sm`. Hover: rise 3px, shadow `md`,
image scales 1.02 (image inside `overflow:hidden`). Never a border-glow.

**Avatars** — 24/32/40/56/96/144. Verified users get a small gold tick badge bottom-right.
Group/stack avatars overlap −8px with a 2px white ring.

**Navigation** — desktop side nav: white surface, items 44 tall, radius 12; selected item
gets `--surface-sunk` fill + 1px gold left indicator + black icon; unselected `--ink-400`.
Top bar: `--canvas-alt` with 1px bottom `--line`, no blur needed.

**Mobile tab bar** — white, top hairline, 5 items with 24px icons and 11/500 labels;
selected = black icon + label with a 3px champagne dot under it; the centre **+** is a
56px black circle with a white plus, raised 8px with shadow `lg`.

**Sheets / modals** — white, radius 28 top (sheet) or all (modal), shadow `xl`, drag
handle on mobile, backdrop `rgba(0,0,0,.45)`.

**Toasts** — black pill, white text, gold icon, bottom-centre mobile / bottom-right desktop.

**Skeletons** — `--surface-sunk` blocks with a slow champagne shimmer.

**Empty states** — a light line illustration or a soft champagne-tinted graphic, an h3, a
sentence, and one primary button. Never a bare sentence in the middle of a void.

---

# 7. MAKE IT FEEL LIKE A SOCIAL / CHAT APP (not a dashboard)
- **Nearby rail** at the top of Home: horizontally scrolling circular avatars with a
  champagne ring (like stories) showing people/activities near you right now — tap to open
  the post. This one element does more for "alive" than any other.
- **Swipe deck in Discover** (Tinder-style, alongside the list view — a toggle): full-bleed
  card with a large photo (person, company or activity), name/title over a soft bottom
  gradient, match % as a champagne badge, skills as chips. Swipe right = interested/apply/
  join, left = pass, up = open detail. Physics-based drag with rotation, a colour wash on
  intent (champagne right, neutral left), and big round action buttons underneath
  (✕ pass · ★ save · ♥ interested) at 56px.
- **Chat** looks like a premium messenger, not a support console: ivory conversation
  background, **own messages = black bubble, white text**; **others = white bubble, black
  text, 1px line**; radius 20 with a 6px tail corner; avatars only on the other side's
  first message in a group; day dividers as small centred labels; unread rows in the list
  carry a champagne left bar and a champagne count pill; input tray is a white pill with
  attach + emoji + send (black circle) on `--surface-sunk`.
- **Everything is tappable and generous:** minimum 44px hit targets, pill buttons,
  bottom sheets instead of dropdowns on mobile, swipe-to-action on list rows (save,
  archive), pull-to-refresh on feed/inbox.
- **Counts and reactions feel playful:** heart animates with a small scale pop and a
  champagne burst; counts roll rather than jump.

---

# 8. SCREEN-BY-SCREEN VISUAL TREATMENT
(Structure/behaviour stays as ARENA-MASTER-ARCHITECTURE.md PART 7 — this is how each looks.)

**Landing / marketing (dark).** Black canvas, ivory type, champagne rule lines, the 3D orb
with warm gold-lit material. H1 display 56–72. Primary CTA: **ivory/white pill with black
text**; secondary: ivory outline. Example post cards render in the LIGHT product style,
floating on the black — showing people the product they'll get. A champagne hairline
separates sections. Footer black on `--night-800`.

**Auth.** Left: black panel with the orb and one line of copy. Right: **ivory panel**, white
card, Poppins, black pill CTA. The visual handoff from dark to light happens here.

**Onboarding.** Full ivory. One question per screen, huge Poppins h1, generous white space,
photo upload first with a champagne ring, skill chips that fill champagne when selected,
progress as a thin champagne bar.

**Home feed.** Ivory canvas. Nearby rail (§7) — composer trigger (white pill: avatar +
"What do you need?" + a black + circle) — segmented tabs (For you · Nearby · Following) as
a pill group with a black selected pill — single 640 column of **white cards**.
PostCard: author row (44 avatar, name 15/600 black, meta 13.5 `--ink-400`, gold tick if
verified, type badge right) · title h3 · body 15/24 `--ink-600` clamped · **media edge-to-
edge inside the card at radius 16** · chips · type block (JOB → ₹ range + location + gold
match %; PROJECT → ₹ budget + deadline + bids; ACTIVITY → time + area + spots + map thumb)
· hairline · reaction row (heart, comment, share, save — 20px icons, `--ink-400`, counts
13.5) · primary action as a black pill on the right. Promoted cards: champagne 1px border
+ "Promoted" label chip.
Right rail cards: white, radius 20, with photography.

**Composer.** White sheet, radius 28. Type selector as six image-backed tiles. Live card
preview to the side. Media uploader shows real thumbnails with a champagne progress ring.
Publish = black pill full-width on mobile.

**Post detail.** Ivory page, white content column, media gallery large with lightbox.
Comments: white rows, 32 avatars, black names, `--ink-600` text, champagne highlight on the
author's own comments. Right context panel white with a map or company card.

**Discover.** Ivory. Toggle: **List | Swipe** (see §7). Facet panel white with black section
labels; selected facets = black chips. Results as white cards with photography, gold match
badges.

**Map.** **Ivory map style** (light basemap, black roads/labels, muted landmass) — this is
the signature screen, so it must look designed. Pins: champagne circles with a black glyph;
selected pin = black circle with champagne glyph and a gentle pulse; clusters = champagne
with black count. Floating white result cards over the map with shadow `lg`. Filter chips
float at the top on white pills. Desktop split map/list; mobile map + draggable white sheet.

**Inbox / chat.** Exactly §7. Conversation list: white rows, 48 avatars, name 15/600,
preview 13.5 `--ink-400`, time top-right, unread = champagne bar + count pill. Work threads
show a top strip: project/job title, ₹ value, member count, and a "View" link — champagne
tinted.

**Work.** Reframe as a marketplace, not an ATS. Header "Opportunities — work that fits
you." Stat cards white with big tabular numbers and a gold underline for the key metric.
Opportunity cards carry company logos/photography and a champagne match badge. Pipeline
board: ivory columns, white draggable cards, champagne column headers with counts.

**Profile.** The showpiece. Full-bleed cover photo — large circular portrait (144)
overlapping the cover with a 4px white ring — name display 44/600 — role · city —
chips (5+ Years · Available · **Arena Verified** in champagne with a gold tick).
**Skill universe:** the 3D graph reskinned — the portrait at the centre, skill nodes as
white/ivory pills with black text, **thin champagne connecting lines**, slow float. Below:
Experience as a vertical timeline with gold dots; Work as an image grid; **Arena Score** as
a circular champagne progress ring with the number in black at 44/600.

**Company page & workspace.** Cover + logo card, black Follow pill, tabs. Workspace uses
the same light system: white tables with generous rows, champagne row-hover, black primary
actions, gold only for premium/promoted/limits. Analytics charts in black/gold/champagne on
white — no rainbow palettes.

**Admin.** Same light system with a **slate/graphite** accent instead of gold, so internal
screens are never mistaken for customer UI.

**404 / errors / loading.** Ivory with the orb reimagined in champagne-gold on ivory, a
short line, and a black pill CTA.

---

# 9. THE 3D UNDER THE NEW PALETTE (keep every scene — restyle it)
The orb currently reads as dark-brown/orange and will look wrong on ivory. Re-material it:
- **Marketing (dark pages):** warm gold-lit sphere on black, champagne rim light —cinematic.
- **Product (light pages):** ivory/porcelain material with soft champagne subsurface glow
  and a gold rim; shadows soft grey. It should look like a polished object on a bright
  studio backdrop, not a glowing lamp.
- **Identity graph:** ivory canvas, white node pills, `--gold` 1px connectors at 40%
  opacity, portrait at centre. No emissive glow.
- **Map:** light basemap; the only luminance is the champagne pins.
All tiering/lazy-mount/reduced-motion rules from the architecture file still apply.

---

# 10. MOTION (premium micro-motion, nothing bouncy)
Enter: `opacity 0→1` + `translateY(12px→0)`, 180–260ms, `cubic-bezier(.22,.61,.36,1)`.
Card hover: rise 3–4px, shadow `sm→md`, inner image 1.02, 200ms.
Buttons: 1–2px press movement, 120ms.
Skill nodes: slow float, 8–14s loops. Map pins: gentle 3s pulse. Feed images: subtle
parallax on scroll (≤8px). Chat bubbles: 140ms scale-in from 0.98. Page transitions:
crossfade + 8px slide, 220ms. Nothing over 320ms. All disabled under
`prefers-reduced-motion`.

---

# 11. MIGRATION — how to change it without breaking the app
1. Create `tokens.css` + Tailwind theme with §2/§3/§4. **Delete the orange and dark-surface
   tokens** so nothing can reference them; the build should fail on any leftover hardcoded
   hex (add a lint rule).
2. Restyle the primitives in §6 first (Button, Input, Chip, Badge, Card, Avatar, Nav,
   Sheet, Toast, Skeleton, EmptyState). Every screen inherits most of the change for free.
3. Set the product shell to `--canvas`; keep the marketing routes on the dark tokens via a
   `data-theme="night"` wrapper.
4. Then apply §8 screen by screen, in the build order of the architecture file.
5. Re-material the 3D per §9.
6. Sweep for regressions: any dark-on-dark leftovers, unreadable gold text, images without
   dimensions, icons that vanished on light backgrounds.

---

# 12. DEFINITION OF DONE (visual)
- Zero neon orange in the product; zero dark-on-dark cards; no hardcoded hex outside tokens.
- Product screens read as ivory-dominant with black type and sparing champagne/gold;
  marketing stays dark and cinematic.
- Every screen shows real photography/avatars; no screen is more than ~20% empty space.
- Poppins throughout, correct scale, tabular numbers on metrics.
- All text/background pairs pass WCAG AA; gold never used as body text.
- Chat, swipe deck, nearby rail and bottom tab bar all feel like a social app on a phone.
- Every 3D scene present and re-materialled for its background.
- Screenshots of every route at 1440px and 390px reviewed against §8 before tagging.
