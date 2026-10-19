QuestGaze UI Refresh
====================

This update refreshes the visual design across the existing frontend and makes the prediction results more readable with bold topic labels, colorful recurrence bars, importance badges, and clearer metric styling. Existing Java backend, Maven dependencies, database schema, API endpoints, and drag-and-drop upload behavior are preserved.

Install:
1. Back up your existing QuestGaze project.
2. Extract this ZIP.
3. Open the extracted QuestGaze folder in IntelliJ IDEA.
4. Reload Maven projects.
5. Ensure MySQL Server is running and your QUESTGAZE_DB_PASSWORD environment variable is set in the run configuration.
6. Run src/main/java/questgaze/web/QuestGazeWebServer.java.
7. Open http://localhost:8080 and press Ctrl+F5.

Notes:
- This is a UI refresh; no backend/database migrations are included.
- Recurrence percentages describe historical paper-level recurrence, not guaranteed probability of appearing in a future exam.
- PDF upload currently requires selectable text. Scanned PDFs need OCR, which is not included.
- Build/runtime verification against your local MySQL server has not been performed in this environment.
