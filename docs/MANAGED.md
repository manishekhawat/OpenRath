# Managed settings for fleets

Taxi fleets, rental companies and dealers can set up many units the same way from a device-management (EMM or MDM) console. OpenRath reads Android's standard app restrictions (`res/xml/app_restrictions.xml`), so any console that supports managed configurations can set these without a custom build.

With no management, none of this applies and every switch stays the owner's.

## Settings

| Key | Type | What it does |
| --- | --- | --- |
| `level` | choice: `simple`, `full`, `custom`, or empty | Picks the OpenRath level once, the way the owner would in Settings > OpenRath level. Empty leaves it to the driver. |
| `lockLevel` | bool, default false | With a level set, the driver can't change it. |
| `featuresOn` | string | Feature ids, separated by commas, turned on and locked. |
| `featuresOff` | string | Feature ids, separated by commas, turned off and locked. |
| `devMode` | bool, default true | False hides developer mode (the seven taps on Version) and its diagnostics. |
| `reportUrl` | string | An https address of the fleet's own. With the `reports` feature on, the unit posts its crash and usage report there once a day. Plain http is ignored. |

A locked feature shows "Set by your organisation" in Settings, and tapping it changes nothing. When the console stops locking a feature, it stays as it was and the driver can change it again. OpenRath applies changes at start and as soon as the console sends them.

Feature ids are the first argument of each `new F(...)` line in `java/org/openrath/launcher/Features.java`, for example `backup`, `speedAlerts`, `news`, `cab`, `cabMeter`, `dashcam` and `reports`. Unknown ids are skipped.

## Example: a cab fleet

```
level        = full
lockLevel    = true
featuresOn   = cab, cabMeter, cabSos, backup, reports
featuresOff  = kids, news, readAloud
devMode      = false
reportUrl    = https://fleet.example.com/openrath/reports
```

## Usage reports

The report is plain text, posted as `text/plain; charset=utf-8`. It holds the app and Android versions, the unit's make and model, a random id made when reports were turned on, how often each setting was opened, how often the app started, and the crash reports kept on the unit. It never holds a position, a name, a phone number or anything typed. A 2xx answer marks the day as sent. Drivers can read the same report in Settings > Backup and about > Usage report.

Turning the `reports` feature off forgets everything counted. Tell drivers when a fleet turns reports on; see [PRIVACY.md](../PRIVACY.md).

## Trying it without a console

On a test unit with adb, a device owner app such as Test DPC can set app restrictions for `org.openrath.launcher`.
