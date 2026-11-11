---
name: Wallpaper Shift product page
description: A quiet study of light for time-based Android wallpapers.
colors:
  ink: "#272930"
  charcoal: "#24262c"
  paper: "#eae8e6"
  gold: "#efbd50"
  muted: "#605b60"
  plum: "#75557c"
  download-gold: "#e5b24a"
  download-hover: "#f0c56a"
  download-ink: "#252329"
  closing-plum: "#34303c"
  closing-gold: "#e1b66a"
  focus-plum: "#a875ac"
  focus-light: "#ffe0a1"
  sunset-blue: "#b8bdda"
typography:
  display:
    fontFamily: "Noto Sans SC, sans-serif"
    fontSize: "4.35vw"
    fontWeight: 600
    lineHeight: 1.12
    letterSpacing: "0.05em"
  headline:
    fontFamily: "Noto Sans SC, sans-serif"
    fontSize: "clamp(34px, 3.5vw, 60px)"
    fontWeight: 600
    lineHeight: 1.4
  title:
    fontFamily: "Noto Sans SC, sans-serif"
    fontSize: "21px"
    fontWeight: 600
  body:
    fontFamily: "Noto Sans SC, sans-serif"
    fontSize: "17px"
    fontWeight: 400
    lineHeight: 1.9
  label:
    fontFamily: "Noto Sans SC, sans-serif"
    fontSize: "14px"
    fontWeight: 400
  step-number:
    fontFamily: "BIZ UDPGothic, sans-serif"
    fontSize: "36px"
    fontWeight: 400
    lineHeight: 1.3
rounded:
  square: "0"
  device: "2.8vw"
  device-screen: "2.3vw"
  mobile-device: "30px"
  mobile-screen: "24px"
  small-device: "24px"
  small-screen: "20px"
spacing:
  icon-gap: "10px"
  action-gap: "20px"
  copy-gap: "22px"
  row-gap: "24px"
  mobile-section: "58px"
  section-gutter: "7vw"
components:
  button-primary:
    backgroundColor: "{colors.download-gold}"
    textColor: "{colors.download-ink}"
    rounded: "{rounded.square}"
    padding: "17px 27px"
    height: "60px"
  button-primary-hover:
    backgroundColor: "{colors.download-hover}"
  button-closing:
    backgroundColor: "{colors.closing-gold}"
    textColor: "#27232c"
    rounded: "{rounded.square}"
    padding: "17px 27px"
  button-text:
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    padding: "10px 0"
  input-blur:
    textColor: "{colors.plum}"
    height: "44px"
    width: "100%"
  device:
    backgroundColor: "#27272d"
    rounded: "{rounded.device}"
    padding: "7px"
  archive-tag:
    textColor: "#705670"
    size: "12px"
---

# Design System: Wallpaper Shift product page

## Overview

**Creative North Star: "一间书房，六种光线"**

The page gives a quiet, tactile frame to changing light: charcoal fields hold the artwork and solar explanation; plaster-light paper holds reading and practical steps. Warm gold marks action and the active afternoon, with plum supporting the adjustable blur control and cool blue distinguishing sunset. The visual language is spacious, ruled, and geometric.

This system documents the finished static product page only. Leo’s book-reaching study artwork is demonstration content, not the product identity. The existing product icon, Android download, user-owned wallpaper sets, and honest device limitations anchor the page. Android application styling remains governed separately by ../design/README.md.

**Key Characteristics:**
- Two-tone charcoal and paper reading fields.
- Whole-heading oblique geometry with crisp Chinese sans lettering.
- Chamfered gold download actions and straight divider rules.
- Real six-state artwork, native controls, and primitive phone silhouettes.

## Colors

Warm amber and muted violet sit between cool charcoal and textured light paper. Color values in the frontmatter follow the shipped CSS, including distinct action gold and time gold.

### Primary

- **Time Gold** (`gold`): active afternoon, time progress, sunrise line, and solar icon.
- **Download Gold / Download Hover** (`download-gold`, `download-hover`): the clipped action plate and its hover fill; `download-ink` keeps text dark. The closing action uses `closing-gold`.

### Secondary

- **Quiet Plum** (`plum`): native blur-slider accent and text-link hover; `closing-plum` supplies the final action field.
- **Sunset Blue** (`sunset-blue`): sunset line, numbers, and moon emphasis.

### Neutral

- **Charcoal** (`charcoal`): masthead, hero base, and solar field.
- **Plaster Paper** (`paper`): reading sections, supplemented by assets/plates/paper.png.
- **Ink / Muted Ink** (`ink`, `muted`): primary reading and secondary explanations.
- **Focus Plum / Focus Light** (`focus-plum`, `focus-light`): keyboard outlines on light and dark fields respectively.

**The Gold Action Rule.** Use gold for download actions, the time rail, and solar emphasis; the active time label may adopt its frame’s light color. Keep download labels dark.

## Typography

**Display and Body Font:** locally bundled Noto Sans SC, sans-serif fallback; weights 400, 500, 600, and 700.
**Step Number Font:** locally bundled BIZ UDPGothic 400, sans-serif fallback.

Chinese headings gain momentum from a uniform geometric lean. Body, controls, and practical information stay upright. The final cascade overrides the earlier BIZ/oblique heading declarations with Noto Sans SC 600 and disables heading font synthesis.

### Hierarchy

- **Display:** 4.35vw / 1.12, weight 600, 0.05em tracking. At ≤900px it is 44px / 1.3 with 0.025em tracking; at ≤480px it is 37px. At ≥1700px it is 4.2vw. Hero transform is `skewX(-9deg) scaleX(1.08)`.
- **Headline:** section-dependent rather than one universal size: solar `clamp(34px, 3.5vw, 60px)` / 1.4; collections `clamp(38px, 4.2vw, 66px)` / 1.35; start `clamp(34px, 3.3vw, 52px)` / 1.35; closing `clamp(34px, 3.4vw, 54px)` / 1.4. Comparison is 2.65vw and FAQ `clamp(26px, 2.1vw, 36px)`. All use weight 600 and `skewX(-9deg)` about the left center.
- **Title:** base h3 21px / 600; feature lines 20px and solar rows 17px / 500.
- **Body:** explanatory solar/collection paragraphs 17px / 1.9, typically 25–26em wide; comparison 1.22vw / 1.8, max 32em. Mobile prose is predominantly 14–15px. No universal fixed body size is applied to the page root.
- **Label:** toolbar/text buttons 14px; supporting notes 12–13px. Clock and schedule numerals use tabular figures.
- **Step number:** BIZ UDPGothic 36px / 1.3, muted plum `#8d6b8b`.

**The One Heading Voice Rule.** Use Noto Sans SC 600 for h1 and h2, with whole-element skewX(-9deg); the hero adds scaleX(1.08). Keep body copy upright.

## Layout

The desktop layout fills the viewport; there is no global max-width wrapper. Masthead padding is 4.65vw, height 5.313vw clamped by 68px and 88px. Hero height is 45.73vw, with six equal columns and top-centered cover crops. The hero copy sits at left 4.55vw/top 1.62vw; the download is centered above a rail inset roughly 9–10% from the sides.

The paper comparison is an asymmetric copy/phone composition, min-height 58vw, padding 5.8vw 4vw 0. Two phone figures are 20.45vw wide with a 4.4vw gap. Later sections use 7vw horizontal gutters: solar has two equal columns with 8vw gap and 8vw vertical padding; collections uses 1fr/1.05fr with 12vw gap. Three ruled steps form a desktop row; FAQ uses 1fr/1.22fr. Spacing is content-led, mixing viewport-scaled section spacing with 10px, 20px, 22px, 24px and other observed local gaps, not a universal spacing multiplier.

- **901–1100px:** comparison grows to 64vw; solar/collection gutters reduce to 5vw and column gaps tighten. Toolbar text stacks its secondary phrase.
- **≤900px:** masthead becomes 68px. Hero is `min(950px, calc(100svh - 68px))`, min-height 710px; only `.slice.is-active` is displayed, with the full-height image centered. Six time buttons and the native rail remain available. Comparison becomes normal document flow; phones remain side by side at 36vw, max 255px. Solar, collections, steps, FAQ, and closing stack; common reading gutters stay 7vw, section padding often 58px.
- **≤480px:** masthead gutters become 18px and GitHub navigation hides; heading and phones tighten (38vw phone figures), with 24px outer/20px screen radii.
- **≥1700px:** comparison min-height becomes 55vw and hero type 4.2vw.

## Elevation & Depth

Depth is a hybrid of tonal fields, physical paper texture, artwork overlays, and restrained ambient shadows. The hero’s dark top/bottom gradient protects text and controls. Phone shells, rather than generic content cards, receive the meaningful lift. The simulated home wallpaper uses a live blur and slight overscan, not a blurred screenshot.

### Shadow Vocabulary

- **Phone desktop:** `0 16px 30px rgba(48, 35, 38, 0.16)`.
- **Phone ≤900px:** `0 12px 24px rgba(48, 35, 38, 0.14)`.
- **Hero lettering:** `0 2px 8px rgba(16, 16, 20, 0.1)` text-shadow.

**The Material Depth Rule.** Reserve ambient shadows for the phone demonstrations and subtle hero text separation; reading sections use texture, tone, and rules.

## Shapes

Reading fields are rectangular and divided by thin straight rules; there is no generic rounded-card grid. The gold download is a real CSS pseudo-element clipped to `polygon(4% 0, 96% 0, 100% 30%, 96% 100%, 0 100%, 0 70%)`, with square anchor radius and an unclipped focus outline. The desktop phone uses 2.8vw outer/2.3vw inner radii; ≤900px uses 30px/24px and ≤480px 24px/20px. Screen aspect ratio is 1440/3168. Circular time ticks, slider thumbs, camera holes, and small rounded app tiles are functional geometry inside the demonstrations.

## Components

### Download actions

Confident gold plates with dark labels, inline Android and arrow SVGs. Base padding is 17px 27px, 20px gap, 60px minimum height, 21px/500. The hero adapts these dimensions to viewport units and to a minimum 265px width/55px height on mobile. Hover shifts the action up 2px and changes the pseudo-element fill; active returns it to the baseline. Closing uses a quieter gold. Both anchors target the real local APK; no deployment is implied.

### Time panorama and rail

Six labelled native buttons share state with an integer range input (0–5, default 3: afternoon). Selection updates `aria-pressed`, `aria-valuetext`, a polite live announcement, both phone sources/clocks, and the active frame. After interaction, inactive desktop images dim to brightness(.74)/saturate(.85), while the active image scales to 1.015. Each state has its own light color in app.js. The custom rail is 2px with circular ticks; the native range retains a 44px input height. On mobile, time labels have at least 44px height.

### Blur input and playback

The blur control is a native range input, 0–25, default 8, accented plum, with a visible numeric output and descriptive `aria-valuetext`. It applies pixel blur to the home-screen picture only; that picture scales to 1.065 to hide filter edges. Lock-screen detail stays clear. The six-state cycle starts automatically after images are ready, holding each state for 2500ms. A visible 44px hero play/pause control mirrors the lower control. Manual selection pauses playback; scrolling both demos offscreen or hiding the document suspends it, with return resuming only when the user has not paused. Small-screen artwork and paired phone previews dissolve over 750ms. The native range retains keyboard semantics while a transformed visual cursor and progress layer track its state; dragging follows immediately.

### Phone comparison

Primitive CSS shells, camera holes, gesture bars, clocks, and SVG app tiles frame the actual local artwork. WebP pictures have PNG fallbacks. Captions distinguish clear lock screen from adjustable home screen; these are illustrations of effects, not the Android app UI.

### Navigation and text links

The masthead uses the existing product icon and a short inline navigation. Links gain a 2px gold underline over .25s; the first link is statically marked current, not a scroll-spy. Inline links and the text button have a 44px minimum height, underline, and plum hover. At ≤480px GitHub hides from the masthead but remains accessible in the footer.

### Ruled rows, archive tag, and FAQ

The ZIP example and feature explanations are flat rows separated by thin muted rules. The small vertical archive tag uses 12px type and .16em tracking; it is a label, not a pill. FAQ uses native details/summary: opening rotates the inline plus SVG by 45deg. There is no invented dialog, error form, pricing card, or empty state.

### Focus and motion

Links, buttons, inputs, and summaries use a 3px focus-visible outline offset 6px; the hero and masthead switch to the light focus color. A focusable skip link moves into view. The shared easing is `cubic-bezier(0.22, 1, 0.36, 1)`; interactions use .2–.9s transitions. Under `prefers-reduced-motion: reduce`, smooth scrolling becomes auto, animations/transitions are removed, and active-image scaling is disabled. Reduced motion disables default autoplay. The user can explicitly start a discrete cycle without spatial or opacity transitions. Automatic updates keep live announcements off; manual selections retain polite feedback.

## Do's and Don'ts

### Do:

- **Do** preserve the charcoal/paper alternation and generous section spacing.
- **Do** keep real buttons, native range inputs, details/summary, visible keyboard focus, and the skip link.
- **Do** show all six image slices on desktop and one active image at widths of 900px or less.
- **Do** keep the wallpaper and blur demonstrations synchronized while preserving clear lock-screen detail.
- **Do** retain reduced-motion CSS, disable default autoplay for that preference, and expose pause controls for the normal automatic cycle.

### Don't:

- **Don't** turn Leo’s demonstration artwork into the product’s identity.
- **Don't** replace live page controls or text with baked screenshots.
- **Don't** invent ratings, pricing, license terms, device guarantees, or automatic verification receipts.
- **Don't** apply this page’s heading transforms or marketing palette as Android application rules.

Documentation evidence: current index.html, styles.css, app.js, PRODUCT.md, the confirmed surface contract, and desktop/mobile captures in .impeccable/review. This describes implementation. The independent verdict is ship at the scope of the four requested fixes: uniform oblique display type, chamfered accessible primary action, honest manual verification persistence, and the known seed reference. All four were resolved with no correction-batch regressions observed; this is not a fresh whole-surface review (see .impeccable/review/final-verdict.md). Manual screenshot/interaction checks are recorded in verification-outcome.json. Automatic phase gates remain not passed (build state plates-open); this document neither changes nor substitutes for those receipts.
