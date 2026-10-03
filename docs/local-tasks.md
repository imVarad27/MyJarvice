# Phone-native tasks

Today includes an explicit task list stored privately in `local-tasks.json` in the
app's internal storage. It works offline and does not call a model.

Users can:

- add a task from Today or the shortcut browser;
- choose no due date, today, tomorrow, or a calendar date;
- edit the title, notes, and due date;
- complete or reopen a task directly from Today;
- review recently completed tasks;
- delete a task after a separate confirmation;
- type `add a phone task to …` or `show my phone tasks` in chat.

Phone tasks and PC tasks are deliberately separate and labeled as such. Due dates
organize Today but do not schedule alarms or notifications. Task records are merged
into local `.jarvisbackup` archives without exposing credentials or model files.

Storage is bounded to 1,000 tasks, 180 characters per title, and 2,000 characters
per note. Imported backup records receive the same bounds.
