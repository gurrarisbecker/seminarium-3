
package se.kth.model;

public class CostData {
    public final String courseCode;
    public final String period;    
    public final double plannedLoadHours;
    public final double actualAllocatedHours;
    public final double avgSalaryPerHour;

    public CostData(String code, String period, double planned, double actual, double salary) {
        this.courseCode = code;
        this.period = period;
        this.plannedLoadHours = planned;
        this.actualAllocatedHours = actual;
        this.avgSalaryPerHour = salary;
    }
}