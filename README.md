# QuestGaze 🎯
### Intelligent Exam Question Prediction System

QuestGaze is a Java and MySQL application that analyzes historical exam question papers to identify recurring topics and help students prioritize their exam preparation.

## ✨ Features

- 📚 Manage subjects and historical question papers
- 📄 Extract question content from PDF papers
- 🔎 Organize questions by detected topics
- 📊 Analyze topic recurrence across previous papers
- 📝 Estimate typical marks from stored questions
- 🌐 Web interface for interacting with the system

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| Java | Application logic and backend |
| JDBC | Database connectivity |
| MySQL | Persistent data storage |
| Maven | Dependency management and build |
| Apache PDFBox | PDF text extraction |
| Tess4J / Tesseract OCR | Text extraction from scanned PDFs |
| HTML, CSS, JavaScript | Web interface |

## 🏗️ Project Structure

```text
QuestGaze/
├── frontend/
│   ├── index.html
│   └── QuestGaze_Reference_Theme.html
├── src/
│   └── main/java/questgaze/
│       ├── database/
│       ├── main/
│       ├── model/
│       ├── service/
│       └── web/
├── pom.xml
└── .gitignore
```

## ⚙️ Prerequisites

- Java JDK
- Maven
- MySQL Server
- Tesseract OCR for scanned-PDF processing

## 🚀 Getting Started

1. Clone this repository:

   ```bash
   git clone https://github.com/Ashish5605/QuestGaze.git
   cd QuestGaze
   ```

2. Create the `questgaze` database in MySQL and configure the required tables.

3. Configure your database connection using environment variables supported by the application:

   - `QUESTGAZE_DB_URL`
   - `QUESTGAZE_DB_USER`
   - `QUESTGAZE_DB_PASSWORD`

4. Build the project:

   ```bash
   mvn clean package
   ```

5. Run the application using the main class and configuration appropriate to your local setup.

## 📊 How It Works

QuestGaze stores historical exam questions, groups them by detected topics, and calculates how frequently those topics appear across available papers.

**Note:** Topic recurrence is based on the uploaded papers. It is an indicator for study planning, not a guarantee of future exam questions. Prediction quality and topic-classification accuracy require further evaluation.

## 🔒 Security

Never commit database passwords, private configuration files, or personal exam documents. Use environment variables for credentials.

## 👨‍💻 Author

**Ashish** — [GitHub Profile](https://github.com/Ashish5605)
**Abhirami R** — [GitHub Profile](https://github.com/Ashish5605)
**Divakar D**

---

*Built as a learning project to explore Java, databases, PDF processing, and data-driven exam preparation.*
