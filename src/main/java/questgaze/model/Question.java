
package questgaze.model;

public class Question {

    private int questionId;
    private String questionText;
    private String topic;
    private int marks;

    public Question(int questionId, String questionText,
                    String topic, int marks) {
        this.questionId = questionId;
        this.questionText = questionText;
        this.topic = topic;
        this.marks = marks;
    }

    public int getQuestionId() {
        return questionId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getTopic() {
        return topic;
    }

    public int getMarks() {
        return marks;
    }
}
