package questgaze.main;

import java.util.Scanner;
import questgaze.model.Subject;
import questgaze.service.SubjectService;

public class QuestGazeApp {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);
        SubjectService subjectService = new SubjectService();
        int choice = 0;

        do {
            System.out.println("\n===== QuestGaze =====");
            System.out.println("Intelligent Exam Question Prediction System");
            System.out.println("1. Add subject");
            System.out.println("2. View subjects");
            System.out.println("3. Add previous-year question paper");
            System.out.println("4. Add questions to a paper");
            System.out.println("5. View stored papers and questions");
            System.out.println("6. Analyse topic frequency");
            System.out.println("7. Generate predicted question paper");
            System.out.println("8. View prediction history");
            System.out.println("9. Exit");
            System.out.print("Enter your choice: ");

            if (!scanner.hasNextInt()) {
                System.out.println("Please enter a number from 1 to 9.");
                scanner.nextLine();
                continue;
            }

            choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1 -> {
                    System.out.print("Enter subject name: ");
                    String name = scanner.nextLine().trim();

                    System.out.print("Enter subject code: ");
                    String code = scanner.nextLine().trim();

                    if (name.isEmpty() || code.isEmpty()) {
                        System.out.println(
                                "Subject name and code cannot be empty.");
                    } else {
                        Subject subject = new Subject(0, name, code);
                        boolean added = subjectService.addSubject(subject);

                        if (added) {
                            System.out.println("Subject added successfully!");
                        } else {
                            System.out.println("Could not add subject.");
                        }
                    }
                }

                case 2 -> subjectService.viewSubjects();

                case 3 -> System.out.println(
                        "Add previous-year paper selected.");

                case 4 -> System.out.println("Add questions selected.");

                case 5 -> System.out.println(
                        "View papers and questions selected.");

                case 6 -> System.out.println(
                        "Analyse topic frequency selected.");

                case 7 -> System.out.println(
                        "Generate predicted paper selected.");

                case 8 -> System.out.println(
                        "View prediction history selected.");

                case 9 -> System.out.println(
                        "Thank you for using QuestGaze!");

                default -> System.out.println(
                        "Invalid choice. Enter 1 to 9.");
            }

        } while (choice != 9);

        scanner.close();
    }
}
