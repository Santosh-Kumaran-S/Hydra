# Device compatibility guide
**Phone:** Android 8.0+ (API 26). Health Connect is built in on Android 14+; Android 9–13 need the Health Connect app from Play Store.
**Reminder reliability:** phone makers (Xiaomi, Oppo, Vivo, Samsung, OnePlus, etc.) may kill or delay background apps. Set Hydra to
"Unrestricted" battery use, allow notifications and exact alarms, and avoid Do Not Disturb. Reminders cannot override a silenced channel.
**Watch (boAt Wave Style via boAt Crest):** proprietary Bluetooth link, no Wear OS, no standard live heart-rate feed. Hydra only reads
heart rate that Crest copies into Health Connect. UNVERIFIED. If Crest does not, Hydra works without heart rate.
Heart rate is synced in batches (not live), so readings are often older than 30 minutes and then ignored.
**Not tested on any real device or watch yet.**
