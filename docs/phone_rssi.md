# Testing WiFi motion sensing with NO extra hardware

You do not need an ESP32 to see real-radio motion sensing today. You can log
**RSSI** (signal strength) from a device you already own and feed it to
`rssi_demo.py`. RSSI is the WiFi signal collapsed to a single number, so it is
much coarser than CSI — expect to catch *someone walking through the room*, not
fine motion. That is exactly the point: it shows the concept works on real
signals, and shows the resolution gap vs. real CSI.

> **Setup that gives the clearest result (any method below):**
> 1. Put the logging device on one side of the room, the WiFi router on the other.
> 2. Start logging. **Stand still for the first ~5 seconds** (baseline).
> 3. Walk back and forth across the line between device and router for ~5 seconds.
> 4. Stand still again for ~5 seconds. Stop logging.
> The "walk" segment should light up as motion; the still segments should not.

---

## Option A — Windows laptop (no phone, no install)

Your laptop already knows its signal strength. In **PowerShell**, this logs it
to `rssi_log.csv` (converting Windows' 0–100% to approximate dBm):

```powershell
while ($true) {
  $line = netsh wlan show interfaces | Select-String 'Signal'
  if ($line) {
    $pct  = [int](($line.ToString().Split(':')[1].Trim()).Replace('%',''))
    $dbm  = $pct/2 - 100                      # % -> approx dBm
    $ts   = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()/1000
    "$ts,$dbm" | Tee-Object -FilePath rssi_log.csv -Append
  }
  Start-Sleep -Milliseconds 300
}
```

Press **Ctrl+C** to stop, then:

```cmd
python rssi_demo.py rssi_log.csv
```

Note: `netsh` updates roughly ~1 Hz, so this is the coarsest option — good for a
"did it notice me cross the room" check.

---

## Option B — Android phone (better sample rate)

Needs **Termux** + **Termux:API** (install both from **F-Droid**, not the Play
Store version which is outdated).

```bash
pkg install termux-api jq
# log timestamp,rssi(dBm) a few times per second:
while true; do
  echo "$(date +%s.%N),$(termux-wifi-connectioninfo | jq -r .rssi)"
  sleep 0.2
done | tee rssi_log.csv
```

Stop with **Ctrl+C**. Copy `rssi_log.csv` to your PC (run `termux-setup-storage`
once, then it's under shared storage), and run:

```cmd
python rssi_demo.py rssi_log.csv
```

---

## iPhone

Not possible without hardware: Apple blocks WiFi RSSI access for third-party
apps. Use Option A or B instead.

---

## Try it right now on the bundled sample

A pre-recorded-style sample (simulated ~5 Hz phone log) ships with the repo so
you can exercise the real-file path before logging your own:

```cmd
python rssi_demo.py examples\sample_rssi_log.csv
```

## What you can and cannot conclude

* ✅ Motion **present / absent**, and roughly **when** — visible on RSSI.
* ⚠️ Fine motion, multiple people, "which limb" — **not** recoverable from RSSI.
* For better resolution you need real **CSI** (ESP32 `esp-csi`, Intel 5300, or
  Nexmon), which is the natural next hardware step.
