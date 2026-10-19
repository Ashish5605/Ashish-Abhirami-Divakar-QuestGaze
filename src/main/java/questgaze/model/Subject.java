
package questgaze.model;

public class Subject {

    private int subjectId;
    private String subjectName;
    private String subjectCode;

    public Subject(int subjectId, String subjectName, String subjectCode) {
        this.subjectId = subjectId;
        this.subjectName = subjectName;
        this.subjectCode = subjectCode;
    }

    public int getSubjectId() {
        return subjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public String getSubjectCode() {
        return subjectCode;
    }
}
