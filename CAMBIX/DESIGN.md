# CAMBIX design decisions

Direction comes from the owner's brief: Liquid Glass, deep black, graphite, translucent white,
soft titanium, electric blue as the only accent. The camera preview dominates the screen.

## Decisions and reasons

| Decision | Reason |
|---|---|
| Glass is limited to control layers (toolbar, zoom, mode selector, nav, panels). The preview and list screens stay flat. | anti-slop R-10 caps glassmorphism. The owner asked for Liquid Glass, so it is kept, but only where controls float over the image. |
| Glass is a 60% black underlay + 10% white tint + a 1 dp specular edge. No backdrop blur. | Compose cannot blur what is behind a TextureView without a library, and a full-screen blur would cost frames. The dark underlay keeps text above 4.5:1 even over a white sky. |
| One accent, electric blue, used for active toggles, the progress dot and the Pro focus state. Record red is reserved for recording. | One accent keeps the hierarchy readable. Red keeps its meaning. |
| System sans-serif only. | No font files are bundled, so there is no loading flash and nothing to license. |
| Icons are drawn on Canvas at one stroke width, with round caps. | One consistent set, no emoji, no icon library. |
| Tap feedback is a 7% scale-down, not a ripple. | Visible on dark glass, no extra dependency. |
| The UI is locked to portrait. Photos and Live Photos still follow how the phone is held (orientation sensor). | A locked layout keeps the viewfinder stable. The sensor keeps saved files upright. |
| Every tab does real work. Controls that depend on hardware are disabled with the reason shown, never hidden behind a fake state. | R-26: every control must work or be removed. |
| Built-in LUTs are generated from small grading recipes (lift, gamma, contrast, split tone). | The catalogue is original work, so no licensed film profile is implied. |
| The live shader and the CPU grader share one tone formula, kept side by side in code. | The viewfinder must match the saved file. Effects that need neighbouring pixels (sharpen, clarity, glow, aberration, sun, HSL) are CPU-only and are not offered in the live panel. |
| Look thumbnails render the user's newest photo through each look. | A look is judged on a real picture, not a stock image. |
| Monitor scopes read a 48x64 sample four times a second. | Cheap enough to leave on, and it reads the filtered surface the user sees. |
| AI is optional, user-supplied, and asks before every upload. | The brief forbids silent uploads and a hardcoded provider. |
| Status chips only show values read back from the active camera session (MP, 1080p 60 fps). | Nothing is claimed before the camera confirms it. |

## Open collision

R-10 vs the owner's Liquid Glass direction. Current answer: keep, limited as above. Reverse it
by replacing `Modifier.glass` in `ui/components/Glass.kt` with a flat graphite surface.
