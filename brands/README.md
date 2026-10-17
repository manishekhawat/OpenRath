# Brand packs

A brand pack adds a car maker's badge to OpenRath: beside the car name, on the grille of the built-in car picture, on the twin-dials layout and in the boot logo and animation maker. It is used when the car setup names that brand.

Badges are the car makers' trademarks, so **brand packs are not part of OpenRath's source and are not published**. This folder is listed in `.gitignore` except for this file. Without any pack, OpenRath uses its own wheel mark and a plain oval emblem, and owners can still pick their own logo picture in Settings > Car.

A pack is a folder named after the brand's id (as in `catalogue/cars.json`, e.g. `maruti-suzuki`) holding `badge.py` with:

- `badge(svg_subpaths)`: returns `(width, polygons)`, the badge outlines in a box 24 units tall.
- `emblem()`: one outline in a 24-unit square for the grille.
- `small()`: one outline for small places.

Only build a pack for your own use, with marks you are allowed to use.

To build an APK to share, without any pack: `OPENRATH_PUBLIC=1 python3 build.py package`. Badges may have holes (rings, letter counters): outlines are filled with the nonzero rule, as in SVG.
