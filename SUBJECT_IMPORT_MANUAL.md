# Subject Curriculum Import Manual (Ghana Edition)

This guide explains how to manage and import subject curriculum data for the Lesson Planner app. This version is designed for manual curriculum management by teachers.

## 1. Manual CSV Import
Since this app does not bundle default curriculums, you must import your own subject data using CSV files.

### Procedure:
1.  Go to the **Curriculum** tab in the app.
2.  Tap the **Import** button (top right).
3.  Select your CSV file from your device's storage.
4.  The app will process the file and add the subject to your list.

---

## 2. CSV File Structure
For a successful import, your CSV must follow a specific column order. The first row (header) is skipped automatically.

### Required Column Order:
1.  **Subject Name** (e.g., Mathematics)
2.  **Grade/Class** (e.g., B1, B7)
3.  **Strand**
4.  **Sub-strand**
5.  **Content Standard**
6.  **Indicator Code** (e.g., B7.1.1.1.1)
7.  **Indicator Description**
8.  **Exemplars** (Learning activities)
9.  **Core Competencies**

### Critical Formatting Rules:
- **Encoding**: Save your CSV with **UTF-8** encoding to ensure special characters are displayed correctly.
- **Empty Rows**: Completely empty rows are skipped.
- **Fill Down**: If a Grade or Strand spans multiple rows, please ensure every row has its respective value. The current importer does not "fill down" empty cells automatically.
- **Validation**: Subject names must be under 50 characters.

---

## 3. Subject Management
- **View**: Tap a subject card to see its details.
- **Delete**: Use the **Trash** icon on the subject card to remove a specific subject and all its associated indicators.
- **Wipe Data**: If you wish to clear everything, use the **Factory Reset** option in the **Settings** menu.
