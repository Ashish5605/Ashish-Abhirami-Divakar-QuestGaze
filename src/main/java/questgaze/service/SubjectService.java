
        package questgaze.service;

import questgaze.database.DatabaseManager;
import questgaze.model.Subject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SubjectService {

    // Add a new subject to the database
    public boolean addSubject(Subject subject) {

        String sql = "INSERT INTO subjects (subject_name, subject_code) VALUES (?, ?)";

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, subject.getSubjectName());
            ps.setString(2, subject.getSubjectCode());

            int rowsInserted = ps.executeUpdate();

            return rowsInserted > 0;

        } catch (SQLException e) {
            System.out.println("Error adding subject: " + e.getMessage());
            return false;
        }
    }

    // Display all saved subjects
    public void viewSubjects() {

        String sql = "SELECT subject_id, subject_name, subject_code FROM subjects";

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("\n===== Saved Subjects =====");

            boolean found = false;

            while (rs.next()) {
                found = true;

                System.out.println("ID: " + rs.getInt("subject_id"));
                System.out.println("Name: " + rs.getString("subject_name"));
                System.out.println("Code: " + rs.getString("subject_code"));
                System.out.println("--------------------------");
            }

            if (!found) {
                System.out.println("No subjects found. Add a subject first.");
            }

        } catch (SQLException e) {
            System.out.println("Error viewing subjects: " + e.getMessage());
        }
    }
}
