# Validation: what each claim means
- **Software correctness:** unit tests cover the scheduling and safety rules (stale data, quiet hours, duplicates, targets, snoozes). Not yet executed.
- **Sensor-data quality:** every reading is range-checked, timestamped and expired (heart rate 30 min, environment 2 h). Unverified with real hardware.
- **Reminder adherence:** the Insights tab reports confirmed / snoozed / swiped-away / ignored counts. Descriptive only.
- **Prediction accuracy:** none claimed. The score is a scheduling heuristic, not a prediction.
- **Clinical validity:** none. No study has been done.
If a study were ever proposed: reference standard (e.g., clinician-reviewed 24 h urine volume or validated intake diary), a pre-registered design,
sample size from an expected effect and variance, error metrics (MAE, agreement limits), and stated limits (small sample, self-report bias).
