package se.kth.integration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import se.kth.model.CostData;

public class CourseInstanceDAO {

    private static final double AVG_SALARY_PER_HOUR_KSEK = 0.5; 

    public int findInstanceId(String courseCode, String period, Connection conn) throws SQLException {
        String sql = "SELECT ci.instance_id FROM course_instance ci " +
                     "JOIN course_layout cl ON ci.course_layout_id = cl.course_layout_id " +
                     "WHERE cl.course_code = ? AND ci.study_period = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, courseCode);
            stmt.setString(2, period);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Hittade inte kursinstans ID för " + courseCode + " i period " + period);
    }

public CostData getCourseCostRawData(int instanceId, Connection conn) throws SQLException {
        
        String sql = 
            "SELECT " +
            "   cl.course_code, " +       
            "   ci.study_period, " +         
            "   COALESCE(SUM(pa.planned_hours), 0) as total_planned_hours, " +
            "   COALESCE(SUM(CASE WHEN pe.employment_id IS NOT NULL THEN pa.planned_hours ELSE 0 END), 0) as actual_allocated_hours " +
            "FROM course_instance ci " +
            "JOIN course_layout cl ON ci.course_layout_id = cl.course_layout_id " +
            "LEFT JOIN planned_activity pa ON ci.instance_id = pa.instance_id " +
            "LEFT JOIN planned_employee pe ON pa.planned_activity_id = pe.planned_activity_id " +
            "WHERE ci.instance_id = ? " +
            "GROUP BY ci.instance_id, cl.course_code, ci.study_period";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, instanceId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String code = rs.getString("course_code");
                    String period = rs.getString("study_period");
                    double planned = rs.getDouble("total_planned_hours");
                    double actual = rs.getDouble("actual_allocated_hours");

                    return new CostData(code, period, planned, actual, AVG_SALARY_PER_HOUR_KSEK);
                }
                throw new SQLException("Kunde inte hitta kursinstans med ID: " + instanceId);
            }
        }
    }

public void increaseStudents(int instanceId, int increaseBy, Connection conn) throws SQLException {

        String sql = "UPDATE course_instance SET num_students = num_students + ? WHERE instance_id = ?";
            
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, increaseBy);
            stmt.setInt(2, instanceId); // <-- setInt
            
            int rows = stmt.executeUpdate();
            if (rows == 0) {

                throw new SQLException("Ingen kurs uppdaterades. Kontrollera att ID: " + instanceId + " finns.");
            }
        }
    }
public String getStudyPeriod(int instanceId, Connection conn) throws SQLException {
        String sql = "SELECT study_period FROM course_instance WHERE instance_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        throw new SQLException("Kunde inte hitta study_period för instance_id: " + instanceId);
    }
public int getStudyYear(int instanceId, Connection conn) throws SQLException {
        String sql = "SELECT study_year FROM course_instance WHERE instance_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Kunde inte hitta study_year för instance_id: " + instanceId);
    }
}