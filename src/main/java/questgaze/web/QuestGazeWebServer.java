
package questgaze.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import questgaze.service.PdfExtractionService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuestGazeWebServer {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String URL = System.getenv().getOrDefault(
            "QUESTGAZE_DB_URL",
            "jdbc:mysql://localhost:3306/questgaze"
    );

    private static final String USER = System.getenv().getOrDefault(
            "QUESTGAZE_DB_USER", "root"
    );

    private static final String PASS = System.getenv().getOrDefault(
            "QUESTGAZE_DB_PASSWORD", ""
    );

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        ensureSyllabusTable();

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 8080), 0
        );

        server.createContext("/api/", QuestGazeWebServer::handle);

        server.createContext("/", ex -> {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())
                    && "/".equals(ex.getRequestURI().getPath())) {

                byte[] page = Files.readAllBytes(
                        Path.of("frontend", "index.html")
                );

                ex.getResponseHeaders().set(
                        "Content-Type", "text/html; charset=utf-8"
                );

                ex.sendResponseHeaders(200, page.length);
                ex.getResponseBody().write(page);
                ex.close();
            } else {
                send(ex, 404, "{\"error\":\"Not found\"}");
            }
        });

        server.setExecutor(null);
        server.start();

        System.out.println(
                "QuestGaze is running at http://localhost:8080"
        );
        System.out.println("Database: " + URL);
    }

    private static Connection db() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    // ==================================================
    // API REQUEST HANDLER
    // ==================================================

    private static void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);

            if ("OPTIONS".equals(method)) {
                send(ex, 204, "");
                return;
            }

            if ("GET".equals(method) && "/api/subjects".equals(path)) {
                send(ex, 200, subjects());
                return;
            }

            if ("POST".equals(method) && "/api/subjects".equals(path)) {
                addSubject(readBody(ex));
                send(ex, 201, "{\"saved\":true}");
                return;
            }

            if ("GET".equals(method)
                    && "/api/question-papers".equals(path)) {
                send(ex, 200, papers());
                return;
            }

            if ("POST".equals(method)
                    && "/api/question-papers".equals(path)) {
                addPaper(readBody(ex));
                send(ex, 201, "{\"saved\":true}");
                return;
            }

            if ("GET".equals(method) && "/api/dashboard".equals(path)) {
                send(ex, 200, dashboard());
                return;
            }

            if ("GET".equals(method) && "/api/predictions".equals(path)) {
                send(ex, 200,
                        predictions(ex.getRequestURI().getRawQuery()));
                return;
            }

            if ("POST".equals(method)
                    && "/api/upload-papers".equals(path)) {
                send(ex, 201, uploadPapers(readBody(ex)));
                return;
            }

            if ("GET".equals(method) && "/api/syllabus".equals(path)) {
                send(ex, 200, syllabusTopics(ex.getRequestURI().getRawQuery()));
                return;
            }

            if ("POST".equals(method) && "/api/syllabus".equals(path)) {
                send(ex, 201, saveSyllabus(readBody(ex)));
                return;
            }

            if ("POST".equals(method) && "/api/reclassify".equals(path)) {
                send(ex, 200, reclassifyQuestions(readBody(ex)));
                return;
            }

            if ("DELETE".equals(method) && "/api/subjects".equals(path)) {
                deleteSubject(ex.getRequestURI().getRawQuery());
                send(ex, 200, "{\"deleted\":true}");
                return;
            }

            if ("DELETE".equals(method)
                    && "/api/question-papers".equals(path)) {
                deletePaper(ex.getRequestURI().getRawQuery());
                send(ex, 200, "{\"deleted\":true}");
                return;
            }

            send(ex, 404, jsonError("Not found"));

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            send(ex, 400, jsonError(e.getMessage()));

        } catch (SQLException e) {
            e.printStackTrace();
            send(ex, 500, jsonError(
                    "Database error: " + e.getMessage()));

        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, jsonError(
                    "Server error: "
                            + e.getClass().getSimpleName()
                            + ": " + e.getMessage()));
        }
    }

    // ==================================================
    // SUBJECTS
    // ==================================================

    private static String subjects() throws SQLException {
        List<String> out = new ArrayList<>();

        try (Connection c = db();
             PreparedStatement p = c.prepareStatement(
                     "SELECT subject_id, subject_name, subject_code "
                             + "FROM subjects ORDER BY subject_name");
             ResultSet r = p.executeQuery()) {

            while (r.next()) {
                out.add("{\"subjectId\":" + r.getInt(1)
                        + ",\"subjectName\":" + q(r.getString(2))
                        + ",\"subjectCode\":" + q(r.getString(3))
                        + "}");
            }
        }

        return "[" + String.join(",", out) + "]";
    }

    private static void addSubject(String body) throws SQLException {
        String name = value(body, "subjectName");
        String code = value(body, "subjectCode");

        if (name.isBlank() || code.isBlank()) {
            throw new IllegalArgumentException(
                    "Subject name and code are required.");
        }

        try (Connection c = db();
             PreparedStatement p = c.prepareStatement(
                     "INSERT INTO subjects(subject_name,subject_code) "
                             + "VALUES(?,?)")) {

            p.setString(1, name);
            p.setString(2, code);
            p.executeUpdate();
        }
    }

    // ==================================================
    // QUESTION PAPERS
    // ==================================================

    private static String papers() throws SQLException {
        List<String> out = new ArrayList<>();

        String sql =
                "SELECT qp.paper_id, qp.subject_id, s.subject_name, "
                        + "qp.exam_year, qp.semester "
                        + "FROM question_papers qp "
                        + "JOIN subjects s ON s.subject_id=qp.subject_id "
                        + "ORDER BY qp.paper_id DESC";

        try (Connection c = db();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet r = p.executeQuery()) {

            while (r.next()) {
                out.add("{\"paperId\":" + r.getInt(1)
                        + ",\"subjectId\":" + r.getInt(2)
                        + ",\"subjectName\":" + q(r.getString(3))
                        + ",\"examYear\":" + q(r.getString(4))
                        + ",\"semester\":" + q(r.getString(5))
                        + "}");
            }
        }

        return "[" + String.join(",", out) + "]";
    }

    private static void addPaper(String body) throws SQLException {
        int subjectId;

        try {
            subjectId = Integer.parseInt(value(body, "subjectId"));
        } catch (Exception e) {
            throw new IllegalArgumentException("Choose a subject.");
        }

        String year = value(body, "examYear");
        String semester = value(body, "semester");

        if (year.isBlank() || semester.isBlank()) {
            throw new IllegalArgumentException(
                    "Year and semester are required.");
        }

        try (Connection c = db();
             PreparedStatement p = c.prepareStatement(
                     "INSERT INTO question_papers"
                             + "(subject_id,exam_year,semester) "
                             + "VALUES(?,?,?)")) {

            p.setInt(1, subjectId);
            p.setString(2, year);
            p.setString(3, semester);
            p.executeUpdate();
        }
    }

    // ==================================================
    // DASHBOARD
    // ==================================================

    private static String dashboard() throws SQLException {
        try (Connection c = db();
             Statement s = c.createStatement()) {

            int subjectCount = count(s, "subjects");
            int paperCount = count(s, "question_papers");
            int questionCount = count(s, "questions");

            return "{\"subjects\":" + subjectCount
                    + ",\"papers\":" + paperCount
                    + ",\"questions\":" + questionCount + "}";
        }
    }

    private static int count(Statement s, String table)
            throws SQLException {
        try (ResultSet r = s.executeQuery(
                "SELECT COUNT(*) FROM " + table)) {
            r.next();
            return r.getInt(1);
        }
    }

    // ==================================================
    // PREDICTIONS
    // ==================================================

    private static String predictions(String raw) throws SQLException {
        Map<String, String> params = new HashMap<>();

        if (raw != null) {
            for (String part : raw.split("&")) {
                String[] kv = part.split("=", 2);

                if (kv.length == 2) {
                    params.put(
                            java.net.URLDecoder.decode(
                                    kv[0], StandardCharsets.UTF_8),
                            java.net.URLDecoder.decode(
                                    kv[1], StandardCharsets.UTF_8)
                    );
                }
            }
        }

        int subjectId;

        try {
            subjectId = Integer.parseInt(
                    params.getOrDefault("subjectId", ""));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Select a subject first.");
        }

        String semester = params.getOrDefault("semester", "");

        String paperSql =
                "SELECT COUNT(DISTINCT p.paper_id) "
                        + "FROM question_papers p "
                        + "JOIN questions q ON q.paper_id=p.paper_id "
                        + "WHERE p.subject_id=? "
                        + "AND (?='' OR p.semester=?)";

        int totalPapers;

        try (Connection c = db();
             PreparedStatement p = c.prepareStatement(paperSql)) {

            p.setInt(1, subjectId);
            p.setString(2, semester);
            p.setString(3, semester);

            try (ResultSet r = p.executeQuery()) {
                r.next();
                totalPapers = r.getInt(1);
            }
        }

        String sql =
                "SELECT q.topic, "
                        + "COUNT(DISTINCT p.paper_id) AS papers_seen, "
                        + "COUNT(*) AS question_count, "
                        + "ROUND(AVG(q.marks),1) AS avg_marks "
                        + "FROM questions q "
                        + "JOIN question_papers p ON p.paper_id=q.paper_id "
                        + "WHERE p.subject_id=? "
                        + "AND (?='' OR p.semester=?) "
                        + "GROUP BY q.topic "
                        + "ORDER BY papers_seen DESC, avg_marks DESC, "
                        + "question_count DESC";

        List<String> out = new ArrayList<>();

        try (Connection c = db();
             PreparedStatement p = c.prepareStatement(sql)) {

            p.setInt(1, subjectId);
            p.setString(2, semester);
            p.setString(3, semester);

            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    int seen = r.getInt(2);
                    int questionCount = r.getInt(3);

                    int percent = totalPapers == 0
                            ? 0
                            : (int) Math.round(
                            seen * 100.0 / totalPapers);

                    String importance = percent >= 70
                            ? "High"
                            : percent >= 40 ? "Medium" : "Watch";

                    out.add(
                            "{\"topic\":" + q(r.getString(1))
                                    + ",\"papersSeen\":" + seen
                                    + ",\"totalPapers\":" + totalPapers
                                    + ",\"recurrencePercent\":" + percent
                                    + ",\"questionCount\":" + questionCount
                                    + ",\"importance\":" + q(importance)
                                    + ",\"marks\":" + q(r.getString(4))
                                    + "}"
                    );
                }
            }
        }

        return "[" + String.join(",", out) + "]";
    }

    // ==================================================
    // PDF UPLOAD WITH OCR
    // ==================================================

    private static String uploadPapers(String body) throws Exception {
        JsonNode root = JSON.readTree(body);

        int subjectId = root.path("subjectId").asInt(0);
        String semester = root.path("semester").asText("").trim();
        JsonNode files = root.path("files");

        if (subjectId <= 0 || semester.isBlank()) {
            throw new IllegalArgumentException(
                    "Choose a subject and semester.");
        }

        if (!files.isArray() || files.isEmpty()) {
            throw new IllegalArgumentException(
                    "Choose at least one PDF file.");
        }

        int saved = 0;
        int questionTotal = 0;
        List<String> summaries = new ArrayList<>();

        try (Connection c = db()) {
            try (PreparedStatement check = c.prepareStatement(
                    "SELECT subject_id FROM subjects WHERE subject_id=?")) {

                check.setInt(1, subjectId);

                try (ResultSet r = check.executeQuery()) {
                    if (!r.next()) {
                        throw new IllegalArgumentException(
                                "Subject not found.");
                    }
                }
            }

            List<String> syllabusTopics = loadSyllabusTopicNames(c, subjectId);

            for (JsonNode file : files) {
                String filename = file.path("filename")
                        .asText("question-paper.pdf");
                String base64 = file.path("contentBase64").asText("");

                if (!filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    throw new IllegalArgumentException(
                            "Only PDF files are supported: " + filename);
                }

                byte[] pdf;

                try {
                    pdf = Base64.getDecoder().decode(base64);
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Could not read uploaded file: " + filename);
                }

                if (pdf.length == 0) {
                    throw new IllegalArgumentException(
                            "The file is empty: " + filename);
                }

                String extracted;
                Path tempPdf = Files.createTempFile(
                        "questgaze-upload-", ".pdf");

                try {
                    Files.write(tempPdf, pdf);

                    PdfExtractionService extractor =
                            new PdfExtractionService();

                    extracted = extractor.extractText(tempPdf);
                } finally {
                    Files.deleteIfExists(tempPdf);
                }

                if (extracted == null || extracted.trim().length() < 20) {
                    throw new IllegalArgumentException(
                            "Could not extract enough text from "
                                    + filename
                                    + ". Check the PDF quality or OCR language.");
                }

                String year = findYear(filename);

                try (PreparedStatement paper = c.prepareStatement(
                        "INSERT INTO question_papers"
                                + "(subject_id,exam_year,semester) "
                                + "VALUES(?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {

                    paper.setInt(1, subjectId);
                    paper.setString(2, year);
                    paper.setString(3, semester);
                    paper.executeUpdate();

                    int paperId;

                    try (ResultSet keys = paper.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                    "Could not create paper record.");
                        }
                        paperId = keys.getInt(1);
                    }

                    List<QuestionRecord> questions =
                            parseQuestions(extracted, syllabusTopics);

                    if (questions.isEmpty()) {
                        String cleaned = extracted.trim()
                                .replaceAll("\\s+", " ");

                        questions.add(new QuestionRecord(
                                cleaned.substring(
                                        0, Math.min(1200, cleaned.length())),
                                inferTopic(extracted, syllabusTopics),
                                0
                        ));
                    }

                    try (PreparedStatement qst = c.prepareStatement(
                            "INSERT INTO questions"
                                    + "(paper_id,question_text,topic,marks) "
                                    + "VALUES(?,?,?,?)")) {

                        for (QuestionRecord question : questions) {
                            qst.setInt(1, paperId);
                            qst.setString(2, question.text);
                            qst.setString(3, question.topic);
                            qst.setInt(4, question.marks);
                            qst.addBatch();
                        }

                        qst.executeBatch();
                    }

                    saved++;
                    questionTotal += questions.size();

                    summaries.add(
                            "{\"filename\":" + q(filename)
                                    + ",\"year\":" + q(year)
                                    + ",\"questionsExtracted\":"
                                    + questions.size() + "}"
                    );
                }
            }
        }

        return "{\"savedPapers\":" + saved
                + ",\"questionsExtracted\":" + questionTotal
                + ",\"files\":[" + String.join(",", summaries) + "]}";
    }

    private static String findYear(String filename) {
        Matcher matcher = Pattern.compile("(?:19|20)\\d{2}")
                .matcher(filename);

        return matcher.find()
                ? matcher.group()
                : "Year not in filename";
    }

    // ==================================================
    // SYLLABUS SUPPORT (ADDITIVE FEATURE)
    // ==================================================

    private static void ensureSyllabusTable() throws SQLException {
        try (Connection c = db(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS syllabus_topics ("
                    + "syllabus_topic_id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "subject_id INT NOT NULL, unit_name VARCHAR(150) NOT NULL DEFAULT '', "
                    + "topic_name VARCHAR(200) NOT NULL, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (subject_id) REFERENCES subjects(subject_id) ON DELETE CASCADE, "
                    + "UNIQUE KEY uq_subject_topic (subject_id, topic_name))");
        }
    }

    // POST /api/syllabus body: {"subjectId":1,"syllabusText":"Unit 1: Basics\nArrays\nSorting\nUnit 2: Graphs\nGraph traversal"}
    // Each non-empty line becomes a syllabus topic; lines beginning with Unit are used as unit labels.
    private static String saveSyllabus(String body) throws Exception {
        JsonNode root = JSON.readTree(body);
        int subjectId = root.path("subjectId").asInt(0);
        String syllabusText = root.path("syllabusText").asText("").trim();
        if (subjectId <= 0 || syllabusText.isBlank()) {
            throw new IllegalArgumentException("Choose a subject and provide syllabus text.");
        }
        int saved = 0;
        String unit = "";
        try (Connection c = db()) {
            try (PreparedStatement check = c.prepareStatement("SELECT subject_id FROM subjects WHERE subject_id=?")) {
                check.setInt(1, subjectId);
                try (ResultSet r = check.executeQuery()) {
                    if (!r.next()) throw new IllegalArgumentException("Subject not found.");
                }
            }
            c.setAutoCommit(false);
            try {
                try (PreparedStatement insert = c.prepareStatement(
                        "INSERT INTO syllabus_topics(subject_id,unit_name,topic_name) VALUES(?,?,?) "
                                + "ON DUPLICATE KEY UPDATE unit_name=VALUES(unit_name)")) {
                    for (String rawLine : syllabusText.split("\\R")) {
                        String line = rawLine.replaceAll("^[\\s•*\\-]+", "")
                                .replaceAll("\\s+", " ").trim();
                        if (line.isBlank()) continue;
                        if (line.matches("(?i)^unit\\s+[0-9ivx]+\\s*[:.\\-]?.*")) {
                            unit = line;
                            String remainder = line.replaceFirst("(?i)^unit\\s+[0-9ivx]+\\s*[:.\\-]?\\s*", "").trim();
                            if (remainder.isBlank()) continue;
                            line = remainder;
                        }
                        if (line.length() > 200) line = line.substring(0, 200).trim();
                        insert.setInt(1, subjectId);
                        insert.setString(2, unit);
                        insert.setString(3, line);
                        insert.addBatch();
                        saved++;
                    }
                    insert.executeBatch();
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
        return "{\"saved\":" + saved + ",\"message\":" + q("Syllabus topics saved. New uploads will use these topics.") + "}";
    }

    // Reclassify already-saved questions for one subject without deleting records.
    // POST /api/reclassify body: {"subjectId":1}
    private static String reclassifyQuestions(String body) throws Exception {
        JsonNode root = JSON.readTree(body);
        int subjectId = root.path("subjectId").asInt(0);
        if (subjectId <= 0) {
            throw new IllegalArgumentException("Choose a subject to reclassify.");
        }

        int updated = 0;
        try (Connection c = db()) {
            try (PreparedStatement check = c.prepareStatement(
                    "SELECT subject_id FROM subjects WHERE subject_id=?")) {
                check.setInt(1, subjectId);
                try (ResultSet r = check.executeQuery()) {
                    if (!r.next()) throw new IllegalArgumentException("Subject not found.");
                }
            }

            List<String> topics = loadSyllabusTopicNames(c, subjectId);
            String selectSql = "SELECT q.question_id, q.question_text FROM questions q "
                    + "JOIN question_papers p ON p.paper_id=q.paper_id WHERE p.subject_id=?";
            String updateSql = "UPDATE questions SET topic=? WHERE question_id=?";
            c.setAutoCommit(false);
            try (PreparedStatement select = c.prepareStatement(selectSql);
                 PreparedStatement update = c.prepareStatement(updateSql)) {
                select.setInt(1, subjectId);
                try (ResultSet r = select.executeQuery()) {
                    while (r.next()) {
                        int questionId = r.getInt("question_id");
                        String questionText = r.getString("question_text");
                        String topic = inferTopic(questionText, topics);
                        update.setString(1, topic);
                        update.setInt(2, questionId);
                        update.addBatch();
                        updated++;
                    }
                }
                update.executeBatch();
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
        return "{\"updatedQuestions\":" + updated
                + ",\"message\":" + q("Saved questions reclassified. Review any 'Other / Review Needed' topics.") + "}";
    }

    private static String syllabusTopics(String rawQuery) throws SQLException {
        int subjectId = getIdFromQuery(rawQuery, "subjectId");
        List<String> out = new ArrayList<>();
        try (Connection c = db(); PreparedStatement p = c.prepareStatement(
                "SELECT unit_name,topic_name FROM syllabus_topics WHERE subject_id=? ORDER BY syllabus_topic_id")) {
            p.setInt(1, subjectId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) out.add("{\"unit\":" + q(r.getString(1)) + ",\"topic\":" + q(r.getString(2)) + "}");
            }
        }
        return "[" + String.join(",", out) + "]";
    }

    private static List<String> loadSyllabusTopicNames(Connection c, int subjectId) throws SQLException {
        List<String> topics = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement(
                "SELECT topic_name FROM syllabus_topics WHERE subject_id=? ORDER BY syllabus_topic_id")) {
            p.setInt(1, subjectId);
            try (ResultSet r = p.executeQuery()) { while (r.next()) topics.add(r.getString(1)); }
        }
        return topics;
    }

    private static String normalizeForMatching(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static Set<String> meaningfulWords(String text) {
        Set<String> words = new HashSet<>();
        for (String word : text.split("\\s+")) {
            if (word.length() > 2 && !Set.of("the", "and", "for", "with", "from", "into", "using", "what", "how", "explain", "describe", "write", "define", "discuss").contains(word)) words.add(word);
        }
        return words;
    }

    // ==================================================
    // QUESTION PARSING AND TOPIC DETECTION
    // ==================================================

    private static class QuestionRecord {
        final String text;
        final String topic;
        final int marks;

        QuestionRecord(String text, String topic, int marks) {
            this.text = text;
            this.topic = topic;
            this.marks = marks;
        }
    }

    private static List<QuestionRecord> parseQuestions(String text, List<String> syllabusTopics) {
        List<QuestionRecord> out = new ArrayList<>();

        String normalized = text
                .replace('\u0000', ' ')
                .replace("\r", "\n");

        Pattern start = Pattern.compile(
                "(?im)^\\s*(?:(?:question|ques|q)\\s*)?"
                        + "\\d{1,2}\\s*[.)、:]\\s+");

        Matcher matcher = start.matcher(normalized);
        List<Integer> starts = new ArrayList<>();

        while (matcher.find()) {
            starts.add(matcher.start());
        }

        for (int i = 0; i < starts.size(); i++) {
            int a = starts.get(i);
            int b = i + 1 < starts.size()
                    ? starts.get(i + 1)
                    : normalized.length();

            String block = normalized.substring(a, b)
                    .replaceAll("\\s+", " ")
                    .trim();

            if (block.length() < 12) {
                continue;
            }

            int marks = 0;

            Matcher markMatcher = Pattern.compile(
                            "(?i)(?:\\[\\s*)?(\\d{1,2})\\s*(?:marks?|M)\\b")
                    .matcher(block);

            if (markMatcher.find()) {
                try {
                    marks = Integer.parseInt(markMatcher.group(1));
                } catch (Exception ignored) {
                    // Keep the default marks value.
                }
            }

            out.add(new QuestionRecord(
                    block.substring(0, Math.min(2000, block.length())),
                    inferTopic(block, syllabusTopics),
                    marks
            ));
        }

        return out;
    }


    private static String inferTopic(String text, List<String> syllabusTopics) {
        String t = text.toLowerCase(Locale.ROOT);

        // Specific canonical topic rules run first to combine differently worded DAA questions.
        if (containsAny(t, "master theorem", "recurrence relation", "recurrence equation",
                "substitution method", "recursion tree", "tower of hanoi", "solve t(n)")) {
            return findSyllabusMatch(syllabusTopics, t,
                    "Recurrence Relations", "Master Theorem", "Recurrence Relations and Master Theorem");
        }
        if (containsAny(t, "huffman", "kruskal", "prim's algorithm", "prim algorithm",
                "minimum spanning tree", "fractional knapsack", "activity selection",
                "job sequencing", "greedy method", "greedy algorithm", "optimal merge")) {
            return findSyllabusMatch(syllabusTopics, t, "Greedy Algorithms", "Greedy Method",
                    "Minimum Spanning Trees", "Huffman Coding");
        }
        if (containsAny(t, "longest common subsequence", "longest common substring", "dynamic programming",
                "matrix chain multiplication", "edit distance", "rod cutting", "0/1 knapsack", "0-1 knapsack")) {
            return findSyllabusMatch(syllabusTopics, t, "Dynamic Programming");
        }
        if (containsAny(t, "backtracking", "n-queen", "n queen", "graph coloring", "graph colouring",
                "branch and bound", "state space tree", "subset sum")) {
            return findSyllabusMatch(syllabusTopics, t, "Backtracking", "Branch and Bound");
        }
        if (containsAny(t, "divide and conquer", "merge sort", "mergesort", "quick sort", "quicksort",
                "maximum subarray", "binary search", "minimum and maximum elements")) {
            return findSyllabusMatch(syllabusTopics, t, "Divide and Conquer");
        }
        if (containsAny(t, "asymptotic notation", "big-o", "big o", "big omega", "big theta",
                "time complexity", "space complexity", "order of growth", "efficiency class",
                "non-recursive algorithm", "recursive algorithm", "np-complete", "np complete", "np-hard")) {
            return findSyllabusMatch(syllabusTopics, t, "Algorithm Analysis and Complexity",
                    "Algorithm Complexity", "Asymptotic Analysis");
        }
        if (containsAny(t, "travelling salesperson", "traveling salesperson", "shortest path", "dijkstra",
                "breadth first search", "depth first search", "graph traversal", "topological sorting")) {
            return findSyllabusMatch(syllabusTopics, t, "Graph Algorithms", "Graph Theory");
        }

        // Prefer specific topics defined in this subject's syllabus when a meaningful phrase or
        // phrase or multiple topic words match the question text.
        if (syllabusTopics != null) {
            String normalizedQuestion = normalizeForMatching(t);
            String bestTopic = null;
            int bestScore = 0;
            for (String topic : syllabusTopics) {
                String normalizedTopic = normalizeForMatching(topic);
                if (normalizedTopic.isBlank()) continue;
                if (normalizedQuestion.contains(normalizedTopic)) {
                    int score = normalizedTopic.split(" ").length + 2;
                    if (score > bestScore) { bestScore = score; bestTopic = topic; }
                    continue;
                }
                Set<String> topicWords = meaningfulWords(normalizedTopic);
                Set<String> questionWords = meaningfulWords(normalizedQuestion);
                int score = 0;
                for (String word : topicWords) if (questionWords.contains(word)) score++;
                if (score >= 2 && score > bestScore) { bestScore = score; bestTopic = topic; }
            }
            if (bestTopic != null) return bestTopic;
        }

        // General fallback rules for subjects without a syllabus match.
        // Syllabus topics work across any subject; these rules help when no
        // syllabus has been entered yet.

        // DAA: Divide and Conquer
        if (containsAny(t, "divide and conquer", "binary search",
                "merge sort", "mergesort", "quick sort", "quicksort",
                "maximum subarray", "kth element")) {
            return "Divide and Conquer";
        }

        // DAA: Greedy Algorithms
        if (containsAny(t, "greedy", "kruskal", "prim's algorithm",
                "prim algorithm", "minimum spanning tree",
                "huffman coding", "huffman code", "activity selection")) {
            return "Greedy Algorithms";
        }

        // DAA: Dynamic Programming
        if (containsAny(t, "dynamic programming", "rod cutting",
                "longest common subsequence", "longest common substring",
                "edit distance", "matrix chain multiplication")) {
            return "Dynamic Programming";
        }

        // DAA: Backtracking and Branch and Bound
        if (containsAny(t, "backtracking", "n-queen", "n queen",
                "graph coloring", "graph colouring",
                "branch and bound", "state space tree")) {
            return "Backtracking and Branch and Bound";
        }

        // DAA: Complexity and NP problems
        if (containsAny(t, "asymptotic notation", "big-o",
                "time complexity", "space complexity",
                "np-complete", "np complete", "np-hard",
                "np hard", "approximation algorithm",
                "brute force", "exhaustive search")) {
            return "Algorithm Analysis and Complexity";
        }

        // Java and Object-Oriented Programming
        if (containsAny(t, "inheritance", "extends keyword",
                "method overriding", "method overloading",
                "polymorphism", "constructor", "abstract class",
                "interface", "exception handling", "try-catch",
                "multithreading", "runnable", "arraylist", "hashmap")) {
            return "Java / Object-Oriented Programming";
        }

        // Database Management Systems
        if (containsAny(t, "normalization", "normalisation",
                "primary key", "foreign key", "candidate key",
                "sql query", "select statement", "inner join",
                "left join", "acid properties", "transaction",
                "entity relationship", "er diagram", "functional dependency")) {
            return "Database Management Systems";
        }

        // Operating Systems
        if (containsAny(t, "deadlock", "process scheduling",
                "cpu scheduling", "virtual memory", "paging",
                "page replacement", "semaphore", "process synchronization",
                "thrashing")) {
            return "Operating Systems";
        }

        // Computer Networks
        if (containsAny(t, "osi model", "tcp/ip", "tcp protocol",
                "udp protocol", "network topology", "routing algorithm",
                "subnetting", "congestion control", "data link layer",
                "network layer")) {
            return "Computer Networks";
        }

        // General classification for unfamiliar questions
        if (containsAny(t, "algorithm", "complexity")) {
            return "Algorithm Analysis";
        }

        return "Other / Review Needed";
    }

    private static String findSyllabusMatch(List<String> syllabusTopics, String question,
                                            String... preferredNames) {
        if (syllabusTopics != null && !syllabusTopics.isEmpty()) {
            for (String preferred : preferredNames) {
                for (String topic : syllabusTopics) {
                    if (normalizeForMatching(topic).equals(normalizeForMatching(preferred))) {
                        return topic;
                    }
                }
            }
        }
        return preferredNames[0];
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }


    // ==================================================
    // DELETE SUBJECTS AND PAPERS
    // ==================================================

    private static int getIdFromQuery(String query, String key) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Missing ID.");
        }

        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);

            if (pair.length == 2 && pair[0].equals(key)) {
                try {
                    return Integer.parseInt(
                            java.net.URLDecoder.decode(
                                    pair[1], StandardCharsets.UTF_8));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid ID.");
                }
            }
        }

        throw new IllegalArgumentException("Missing ID: " + key);
    }

    // Delete all linked questions and papers, then the subject.
    private static void deleteSubject(String query) throws SQLException {
        int subjectId = getIdFromQuery(query, "subjectId");

        try (Connection c = db()) {
            c.setAutoCommit(false);

            try {
                try (PreparedStatement check = c.prepareStatement(
                        "SELECT subject_id FROM subjects WHERE subject_id=?")) {
                    check.setInt(1, subjectId);

                    try (ResultSet r = check.executeQuery()) {
                        if (!r.next()) {
                            throw new IllegalArgumentException(
                                    "Subject not found.");
                        }
                    }
                }

                try (PreparedStatement deleteQuestions = c.prepareStatement(
                        "DELETE q FROM questions q "
                                + "INNER JOIN question_papers p "
                                + "ON q.paper_id=p.paper_id "
                                + "WHERE p.subject_id=?")) {
                    deleteQuestions.setInt(1, subjectId);
                    deleteQuestions.executeUpdate();
                }

                try (PreparedStatement deletePapers = c.prepareStatement(
                        "DELETE FROM question_papers WHERE subject_id=?")) {
                    deletePapers.setInt(1, subjectId);
                    deletePapers.executeUpdate();
                }

                try (PreparedStatement deleteSubject = c.prepareStatement(
                        "DELETE FROM subjects WHERE subject_id=?")) {
                    deleteSubject.setInt(1, subjectId);

                    if (deleteSubject.executeUpdate() == 0) {
                        throw new IllegalArgumentException(
                                "Subject not found.");
                    }
                }

                c.commit();

            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // Delete questions first, then their question paper.
    private static void deletePaper(String query) throws SQLException {
        int paperId = getIdFromQuery(query, "paperId");

        try (Connection c = db()) {
            c.setAutoCommit(false);

            try {
                try (PreparedStatement deleteQuestions = c.prepareStatement(
                        "DELETE FROM questions WHERE paper_id=?")) {
                    deleteQuestions.setInt(1, paperId);
                    deleteQuestions.executeUpdate();
                }

                try (PreparedStatement deletePaper = c.prepareStatement(
                        "DELETE FROM question_papers WHERE paper_id=?")) {
                    deletePaper.setInt(1, paperId);

                    if (deletePaper.executeUpdate() == 0) {
                        throw new IllegalArgumentException(
                                "Question paper not found.");
                    }
                }

                c.commit();

            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // ==================================================
    // REQUEST AND JSON HELPERS
    // ==================================================

    private static String value(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);

        if (i < 0) {
            return "";
        }

        i = json.indexOf(':', i + needle.length());

        if (i < 0) {
            return "";
        }

        i++;

        while (i < json.length()
                && Character.isWhitespace(json.charAt(i))) {
            i++;
        }

        if (i < json.length() && json.charAt(i) == '"') {
            StringBuilder b = new StringBuilder();

            for (i++; i < json.length(); i++) {
                char ch = json.charAt(i);

                if (ch == '\\' && i + 1 < json.length()) {
                    char next = json.charAt(++i);
                    b.append(next == 'n' ? '\n' : next);
                } else if (ch == '"') {
                    break;
                } else {
                    b.append(ch);
                }
            }

            return b.toString();
        }

        int j = i;

        while (j < json.length()
                && ",}".indexOf(json.charAt(j)) < 0) {
            j++;
        }

        return json.substring(i, j).trim();
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(
                ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static String q(String s) {
        if (s == null) {
            return "null";
        }

        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private static String jsonError(String s) {
        return "{\"error\":" + q(
                s == null ? "Unknown error" : s) + "}";
    }

    private static void send(
            HttpExchange ex, int status, String body
    ) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set(
                "Content-Type", "application/json; charset=utf-8");

        ex.getResponseHeaders().set(
                "Access-Control-Allow-Origin", "http://localhost:8080");

        ex.getResponseHeaders().set(
                "Access-Control-Allow-Headers", "Content-Type");

        ex.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET,POST,DELETE,OPTIONS");

        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
