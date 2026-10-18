# Privacy and limitations
**Privacy:** profile, drinks, readings and reminder history live in a local database on the phone (backup disabled). No account, ads, analytics
or uploads. The only network call is a weather lookup that sends a typed city name (and its coordinates) to Open-Meteo over HTTPS. Export writes a
JSON file only where she chooses. "Delete all data" erases everything. Health Connect access can be revoked in Android settings.
**Limitations:** not a medical device; does not measure hydration, blood volume, dehydration or kidney-stone risk. Heart rate and weather are
indirect and can only nudge the schedule by at most 25% earlier. Weather is local observation, not her immediate surroundings. No fluid target is
derived from height/weight/heat/heart rate; only a clinician-entered value is used, and reaching it stops reminders for that day.
Reminders are prompts, not instructions. She should follow her doctor's advice over the app. No activity detection; heart-rate use during exercise
cannot be told apart, so the effect is kept small. No comparisons like "warmer days" are shown (not enough validated data).
Snooze/last-fired state uses SharedPreferences (not DataStore); Room has no migrations yet; entry times cannot be edited.
