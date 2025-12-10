package se.kth.controller;

import java.sql.Connection;
import java.sql.SQLException;

import se.kth.integration.ActivityDAO;
import se.kth.integration.AllocationDAO;
import se.kth.integration.CourseInstanceDAO;
import se.kth.integration.DBHandler;
import se.kth.model.CostData;

public class TeachingManager {

    
    private final DBHandler dbHandler = new DBHandler();
    private final AllocationDAO allocationDAO = new AllocationDAO();
    private final CourseInstanceDAO courseDAO = new CourseInstanceDAO();
    private final ActivityDAO activityDAO = new ActivityDAO();



public String computeTeachingCost(int instanceId) throws Exception {
        Connection conn = null;
        try {
            conn = dbHandler.getConnection(); 
            
            
            CostData data = courseDAO.getCourseCostRawData(instanceId, conn);
            
            
            
            double plannedCost = data.plannedLoadHours * data.avgSalaryPerHour;
            double actualCost = data.actualAllocatedHours * data.avgSalaryPerHour;
            
            
            return String.format("ID: %d | Kurs: %s (Period %s)\nPlanerad belastning: %.1f timmar (Kostnad: %.1f KSEK)\nFaktisk allokering: %.1f timmar (Kostnad: %.1f KSEK)",
                instanceId, data.courseCode, data.period, data.plannedLoadHours, plannedCost, data.actualAllocatedHours, actualCost);

        } catch (SQLException e) {
            throw new Exception("Kunde inte hämta kostnadsdata: " + e.getMessage(), e);
        } finally {
            dbHandler.closeConnection(conn);
        }
    }


public String increaseStudentsAndShowCost(int instanceId, int increaseBy) throws Exception {
        Connection conn = null;
        StringBuilder output = new StringBuilder();
        
        try {
            conn = dbHandler.getConnection();

            CostData dataBefore = courseDAO.getCourseCostRawData(instanceId, conn);
            
            output.append(String.format("--- Rapport för %s (%s) ---\n", dataBefore.courseCode, dataBefore.period));
            output.append("--- Kostnad FÖRE Studentökning ---\n");
            output.append(formatCostData(dataBefore)).append("\n");


            courseDAO.increaseStudents(instanceId, increaseBy, conn);
            

            conn.commit(); 
            output.append("\n✅ Antal studenter ökat med ").append(increaseBy).append(". COMMIT.\n\n");


            Connection postCommitConn = null;
            try {
                postCommitConn = dbHandler.getConnection();
                CostData dataAfter = courseDAO.getCourseCostRawData(instanceId, postCommitConn);
                
                output.append("--- Kostnad EFTER Studentökning ---\n");
                output.append(formatCostData(dataAfter));
            } finally {
                dbHandler.closeConnection(postCommitConn);
            }
            
            return output.toString();

        } catch (Exception e) {
            if (conn != null) {
                conn.rollback();
            }
            throw new Exception("Studentökning misslyckades. ROLLBACK: " + e.getMessage(), e);
        } finally {
            dbHandler.closeConnection(conn);
        }
    }


    private String formatCostData(CostData data) {
        double plannedCost = data.plannedLoadHours * data.avgSalaryPerHour;
        double actualCost = data.actualAllocatedHours * data.avgSalaryPerHour;
        
        return String.format("Planerad belastning: %.1f timmar (Kostnad: %.1f KSEK)\nFaktisk allokering: %.1f timmar (Kostnad: %.1f KSEK)",
                data.plannedLoadHours, plannedCost, data.actualAllocatedHours, actualCost);
    }
    

    private String computeTeachingCostInternal(int instanceId, Connection conn) throws SQLException {
        CostData data = courseDAO.getCourseCostRawData(instanceId, conn);
        double plannedCost = data.plannedLoadHours * data.avgSalaryPerHour;
        double actualCost = data.actualAllocatedHours * data.avgSalaryPerHour;
        
        return String.format("Planerad belastning: %.1f timmar (Kostnad: %.1f KSEK)\nFaktisk allokering: %.1f timmar (Kostnad: %.1f KSEK)",
                data.plannedLoadHours, plannedCost, data.actualAllocatedHours, actualCost);
    }

    
public String addNewActivityAndAllocate(int instanceId, String teacherId) throws Exception {
        Connection conn = null;
        try {
            conn = dbHandler.getConnection(); 
            

            allocationDAO.lockTeacher(teacherId, conn);


            boolean alreadyOnCourse = allocationDAO.isTeacherAllocatedToInstance(teacherId, instanceId, conn);
            

            if (alreadyOnCourse) {

                throw new Exception("Läraren (" + teacherId + ") är redan allokerad till denna kursinstans.");
            }


            String period = courseDAO.getStudyPeriod(instanceId, conn);
            int year = courseDAO.getStudyYear(instanceId, conn);
            int currentCourses = allocationDAO.countTeacherAllocationsForPeriod(teacherId, period, year, conn);

            if (currentCourses >= 4) {
                throw new Exception("Regelbrott: Läraren (" + teacherId + ") har redan 4 kurser i P" + period + " " + year + ".");
            }


            int typeId = activityDAO.getOrCreateActivityType("Exercise", conn);
            int plannedActivityId = activityDAO.createPlannedActivity(instanceId, typeId, 10, conn); 
            allocationDAO.createAllocation(teacherId, plannedActivityId, conn);

            conn.commit(); 
            
            return activityDAO.displayNewAllocation(plannedActivityId, conn);

        } catch (Exception e) {
            if (conn != null) conn.rollback();

            throw new Exception("Rollback" + e.getMessage());
        } finally {
            dbHandler.closeConnection(conn);
        }
    }
    public void allocateTeacher(String teacherId, int instanceId) throws Exception {
        Connection conn = null;
        try {
            conn = dbHandler.getConnection(); 
            
            
            allocationDAO.lockTeacher(teacherId, conn); 

            
            String period = courseDAO.getStudyPeriod(instanceId, conn);
            int year = courseDAO.getStudyYear(instanceId, conn);

            
            int currentCourses = allocationDAO.countTeacherAllocationsForPeriod(teacherId, period, year, conn);

            if (currentCourses >= 4) {
                throw new Exception("Läraren (" + teacherId + ") har redan " + currentCourses + 
                                    " kurser i period " + period + " under år " + year + ". Gränsen är 4.");
            }

            
            int typeId = activityDAO.getOrCreateActivityType("Lecture", conn);
            int plannedActivityId = activityDAO.createPlannedActivity(instanceId, typeId, 20, conn);
            allocationDAO.createAllocation(teacherId, plannedActivityId, conn);

            conn.commit(); 
            System.out.println("✅ Allokering lyckades: " + teacherId + " -> Instance " + instanceId + 
                               " (Period " + period + ", År " + year + "). COMMIT.");

        } catch (Exception e) {
            if (conn != null) {
                conn.rollback();
                System.err.println("ROLLBACK");
            }
            throw e; 
        } finally {
            dbHandler.closeConnection(conn);
        }
    }
    

    public void deallocateTeacher(String teacherId, int instanceId) throws Exception {
        Connection conn = null;
        try {
            conn = dbHandler.getConnection();

            allocationDAO.deleteAllocation(teacherId, instanceId, conn); 

            conn.commit();
            System.out.println("Deallokering lyckades: " + teacherId + " borttagen från Instance " + instanceId);

        } catch (Exception e) {
            if (conn != null) {
                conn.rollback();
                System.err.println("ROLLBACK");
            }
            throw e;
        } finally {
            dbHandler.closeConnection(conn);
        }
    }

}