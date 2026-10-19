# Making a OpenRath theme

A theme is a folder of plain files. No app source, Android Studio or rebuild is needed.

## Built-in themes

**Settings → Themes** shows every theme as a picture of the home screen in its colours. Eight come with the app:

| Theme | Look |
| --- | --- |
| Magma Grey | The original dark chrome look; its accent follows the car paint colour |
| Light | Light grey and white boxes, dark text, blue accent |
| Minimal | Pure black, borderless boxes, one blue accent (Tesla-style) |
| Auto | Soft dark grey cards and a light blue accent (Android Auto-style) |
| Gen Z | Purple night gradient, see-through boxes, neon pink and mint |
| Mission control | Black like space, white type and a cool blue, square corners, like a launch console |
| Electric | Clean white cards on light grey, no borders, one blue, like an electric car's screen |
| Roadster | Carbon black and racing red for a sports car |

Their `theme.json` files are in `assets/themes/` in the source and make good starting points.

## Quick start

1. On the unit, pick the theme closest to what you want in **Settings → Themes**, then tap **Make your own theme**. This copies it to `OpenRath/themes/my-theme` on the unit's storage, with every picture.
2. Edit the files there, either on a PC over USB or with a file manager. `HOW-TO.txt` in the folder explains each one.
3. In **Settings → Themes**, tap `my-theme`. Tap it again to reload after each edit.

## Without a theme

An owner can also change the car picture, logo and car name in **Settings → Car**, and the button icons and the bar's position in **Settings → Buttons bar**, with no theme needed. Their picks override any theme.

## Sharing

Zip the folder and share the `.zip`. To install one, put the `.zip` in **Download** and choose **Settings → Themes → Import a theme from Download**.

## Format

The `theme.json` file:

| Key | Meaning | Default |
| --- | --- | --- |
| `name`, `author`, `version` | Shown in Settings → Themes | folder name |
| `description` | One line under the theme's picture | none |
| `colours.text` | Main text and numbers | `#D8DCE2` |
| `colours.textDim` | Quieter text | `#8A8F96` |
| `colours.accent` | Labels and highlights | the colour theme's accent |
| `colours.edge` | Box border | `#4C5056` |
| `colours.panelTop`, `colours.panel` | Box fill, top to bottom | `#111215`, `#0B0C0E` |
| `colours.background` | Behind everything | `#000000` |
| `colours.icons` | One flat colour for the buttons bar and media buttons | chrome pictures |
| `colours.road`, `colours.roadLine` | The road under the car, and its dashes and edges | `#1D1F23`, `#C9CDD3` |
| `backgroundGradient` | 2 or 3 colours, top-left to bottom-right, instead of a plain background | none |
| `chromeText` | Silver shine on the car name and welcome line | `true` |
| `cornerRadius` | Box corner radius, in units of a 600 px tall screen | `14` |
| `road` | Show the moving road under the car | `true` |
| `font` | A `.otf` or `.ttf` file in the folder, used for the welcome line | Inter Display Light |
| `welcomeTitle` | The word above the name | `WELCOME` |
| `carName` | Name beside the logo | the car's model |

Colours are `#RRGGBB` or `#AARRGGBB`.

## Pictures

Each picture replaces the built-in PNG of the same name. Anything a theme leaves out keeps the built-in picture.

- `car_off.png`, `car_on.png`
- `logo.png`: the brand logo. The car name beside it is text, set by `carName` or by the owner in Settings → Car
- `dock_navi.png`, `dock_music.png`, `dock_radio.png`, `dock_bt.png`, `dock_phone.png`, `dock_apps.png`, `dock_settings.png`
- `btn_play.png`, `btn_pause.png`, `btn_prev.png`, `btn_next.png`
- `w_sun.png`, `w_moon.png`, `w_partly.png`, `w_cloud.png`, `w_rain.png`, `w_snow.png`, `w_storm.png`, `w_fog.png`
- `background.jpg` or `background.png`: an optional wallpaper, cropped to fill the screen
- `preview.png`: an optional picture for Settings → Themes; without it the app draws one from the colours

Built-in pictures are drawn at 2.4× a 600 px tall screen. Starting from the copies that "Make your own theme" writes keeps sizes right.

## Example

`themes/oxford-blue.zip` is a colours-only theme to try the import.
