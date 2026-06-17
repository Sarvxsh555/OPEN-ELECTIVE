package com.svce.oejava;

public class courseSelection {

    private int id;              // can just be index in list (not DB id)
    private String title;        // from courseTitle
    private String code;         // from courseCode
    private String instructor;   // from faculty
    private String department;   // from branch
    private int capacity;
    private int enrolled;
    private String restricted;
    private String session;   // raw text from DB (e.g. "IT,CS,AD")
    private boolean full;
    private boolean disabled;    // true for owning department (IT student & IT course)
    private int changeCount;     // keep for compatibility if used

    public courseSelection() {}

    public courseSelection(int id,
                           String title,
                           String code,
                           String instructor,
                           String department,
                           int capacity,
                           int enrolled,
                           String restricted,
                           String session,
                           boolean full,
                           boolean disabled) {
        this.id = id;
        this.title = title;
        this.code = code;
        this.instructor = instructor;
        this.department = department;
        this.capacity = capacity;
        this.enrolled = enrolled;
        this.restricted = restricted;
        this.session = session;
        this.full = full;
        this.disabled = disabled;
        this.changeCount = 0;
    }

    // getters/setters (same as you already had, just ensure these exist)

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getInstructor() { return instructor; }
    public void setInstructor(String instructor) { this.instructor = instructor; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public int getEnrolled() { return enrolled; }
    public void setEnrolled(int enrolled) { this.enrolled = enrolled; }

    public String getRestricted() { return restricted; }
    public void setRestricted(String restricted) { this.restricted = restricted; }

    public String getSession() { return session; }
    public void setSession(String session) { this.session = session; }  

    public boolean isFull() { return full; }
    public void setFull(boolean full) { this.full = full; }

    public boolean isDisabled() { return disabled; }
    public void setDisabled(boolean disabled) { this.disabled = disabled; }

    public int getChangeCount() { return changeCount; }
    public void setChangeCount(int changeCount) { this.changeCount = changeCount; }

    // extra getters (frontend compatibility)
    public String getName() { return title; }
    public String getDescription() { return "Instructor: " + (instructor == null ? "" : instructor); }
}
