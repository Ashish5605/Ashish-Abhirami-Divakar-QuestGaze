
package questgaze.model;

public class QuestionPaper {
    private int paperId;
    private int subjectId;
    private String examYear;
    private String semester;
    public QuestionPaper(int paperId, int subjectId,
                         String examYear, String semester) {
        this.paperId = paperId;
        this.subjectId = subjectId;
        this.examYear = examYear;
        this.semester = semester;
    }
    public int getPaperId() {
        return paperId;
    }
    public int getSubjectId() {
        return subjectId;
    }
    public String getExamYear() {
        return examYear;
    }
    public String getSemester() {
        return semester;
    }
}
