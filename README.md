# Open Elective Application Setup Guide

This repository contains a Spring Boot backend and a static HTML/CSS/JavaScript frontend for open elective course selection.

## Project Structure

```text
Open-Elective/
+-- OE-Backend/    Spring Boot REST API, JPA, MySQL
`-- OE-Frontend/   Static HTML, CSS, JavaScript frontend
```

## 1. Install Required Software

Install these versions before running the project:

| Tool | Version | Why it is needed |
| --- | --- | --- |
| Java JDK | 17 LTS | Required by Spring Boot 3.x |
| MySQL Server | 8.0.x or 8.4 LTS | Stores students, courses, admins, and enrollments |
| Maven | 3.9.11 | Included through the Maven Wrapper in `OE-Backend`; install separately only if the wrapper fails on your machine |
| Browser | Latest Chrome/Edge/Firefox | Runs the frontend |
| Python | 3.11+ | Optional, used only to serve the static frontend on port `5500` |
| VS Code Live Server | 5.7.9 | Optional alternative to Python static server |

Check installed versions:

```powershell
java -version
mysql --version
python --version
```

The backend already includes Maven Wrapper, so you usually do not need to install Maven manually. If `mvnw.cmd` fails on Windows because your user folder path contains spaces, install Apache Maven `3.9.11` and use `mvn` instead of `.\mvnw.cmd`.

## 2. Configure MySQL

Start MySQL Server, then log in:

```powershell
mysql -u root -p
```

Create the database:

```sql
CREATE DATABASE IF NOT EXISTS open_elective
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE open_elective;
```

The backend currently reads this database configuration from `OE-Backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/open_elective
spring.datasource.username=root
spring.datasource.password=712724
server.port=9090
spring.jpa.hibernate.ddl-auto=update
```

If your MySQL root password is different, update `spring.datasource.password`.

Recommended safer option:

```sql
CREATE USER IF NOT EXISTS 'oe_user'@'localhost' IDENTIFIED BY 'oe_password';
GRANT ALL PRIVILEGES ON open_elective.* TO 'oe_user'@'localhost';
FLUSH PRIVILEGES;
```

Then update `application.properties`:

```properties
spring.datasource.username=oe_user
spring.datasource.password=oe_password
```

## 3. Create SQL Tables

Hibernate can create/update tables automatically because `spring.jpa.hibernate.ddl-auto=update` is enabled. Still, you can create the tables manually with this SQL:

```sql
USE open_elective;

CREATE TABLE IF NOT EXISTS admins (
  email VARCHAR(100) PRIMARY KEY,
  password VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS students (
  registration_no BIGINT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  branch VARCHAR(255) NOT NULL,
  dob DATE NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS courses (
  course_code VARCHAR(255) PRIMARY KEY,
  course_title VARCHAR(255) NOT NULL,
  faculty VARCHAR(255),
  branch VARCHAR(255),
  capacity INT NOT NULL,
  enrolled INT NOT NULL DEFAULT 0,
  restricted VARCHAR(255),
  session VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS enrollments (
  registration_no BIGINT NOT NULL,
  session VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  branch VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  course_code VARCHAR(255) NOT NULL,
  course_title VARCHAR(255) NOT NULL,
  ts DATETIME NOT NULL,
  changed_course VARCHAR(255) NOT NULL,
  PRIMARY KEY (registration_no, session)
);
```

Important: the current `Course` entity uses `course_code` as the primary key, so each course code must be unique.

## 4. Add Sample Data

Run this SQL so admin login, student login, sessions, and courses work immediately:

```sql
USE open_elective;

INSERT INTO admins (email, password)
VALUES ('admin@svce.ac.in', 'admin123')
ON DUPLICATE KEY UPDATE password = VALUES(password);

INSERT INTO students (registration_no, name, branch, dob, email)
VALUES
  (2127240801082, 'Demo Student', 'IT', '2004-01-15', 'demo.student@gmail.com'),
  (2127240801083, 'CS Student', 'CS', '2004-02-20', 'cs.student@gmail.com')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  branch = VALUES(branch),
  dob = VALUES(dob),
  email = VALUES(email);

INSERT INTO courses
  (course_code, course_title, faculty, branch, capacity, enrolled, restricted, session)
VALUES
  ('OE101', 'Cloud Computing', 'Dr. Kumar', 'CS', 60, 0, 'CS', '25-26OS'),
  ('OE102', 'Internet of Things', 'Dr. Meena', 'ECE', 60, 0, '', '25-26OS'),
  ('OE201', 'Data Analytics', 'Dr. Raj', 'IT', 50, 0, 'IT', '25-26ES')
ON DUPLICATE KEY UPDATE
  course_title = VALUES(course_title),
  faculty = VALUES(faculty),
  branch = VALUES(branch),
  capacity = VALUES(capacity),
  restricted = VALUES(restricted),
  session = VALUES(session);
```

For student Google login, the Google account email must match the student email stored in the `students` table.

## 5. Run the Backend

Open a terminal from the repository root:

```powershell
cd OE-Backend
.\mvnw.cmd spring-boot:run
```

On macOS/Linux:

```bash
cd OE-Backend
./mvnw spring-boot:run
```

Windows fallback if the Maven Wrapper fails:

```powershell
cd OE-Backend
mvn spring-boot:run
```

The first run downloads dependencies. When startup finishes, the backend should be available at:

```text
http://localhost:9090
```

Quick backend checks:

```powershell
curl http://localhost:9090/api/sessions
curl http://localhost:9090/api/courses?session=25-26OS
```

## 6. Run the Frontend

The frontend is static HTML/JavaScript. It must be served from `localhost:5500` or `127.0.0.1:5500` because the backend CORS config allows those origins.

Option A, using Python:

```powershell
cd OE-Frontend
python -m http.server 5500
```

Open:

```text
http://localhost:5500/index.html
```

Option B, using VS Code Live Server:

1. Open the `OE-Frontend` folder in VS Code.
2. Install the Live Server extension version `5.7.9`.
3. Right-click `index.html`.
4. Select `Open with Live Server`.
5. Make sure the URL uses port `5500`.

## 7. Student Flow

1. Start MySQL.
2. Start the backend on `http://localhost:9090`.
3. Start the frontend on `http://localhost:5500`.
4. Open `http://localhost:5500/index.html`.
5. Enter the student register number.
6. Sign in with Google.
7. Select a session.
8. Choose an available course.
9. Submit enrollment.
10. Use `Change` if the student needs to change the selected course once.

Student login only succeeds when:

- `registration_no` exists in the `students` table.
- The Google email exactly matches the student's `email` value.
- Courses exist for the selected `session`.

## 8. Admin Flow

Open:

```text
http://localhost:5500/admin-login.html
```

Use the sample admin account:

```text
Email: admin@svce.ac.in
Password: admin123
```

From the admin dashboard you can:

- View courses by session.
- Add, edit, and delete courses.
- Manually enroll students.
- Delete enrollments.
- Upload student Excel files.
- Upload course Excel files.
- Download reports as PDF or Excel.
- Clear selected data after admin verification.

## 9. Excel Upload Format

Student Excel required headers:

```text
Registration No, Name, Branch, DOB, Email ID
```

Rules:

- Header row must be the first row.
- `DOB` should be an Excel date or ISO date like `2004-01-15`.
- `Email ID` must be the Google login email.

Course Excel required headers:

```text
SL No., Dept Offering, CourseCode, CourseTitle, Faculty, Capacity, Restricted to
```

Rules:

- `SL No.` is optional in code, but keeping it is recommended.
- `Dept Offering` maps to the course department.
- `Restricted to` can contain departments like `IT`, `CS`, or `IT,CS`.
- Uploaded courses are saved under the selected dashboard session.

## 10. API Summary

Backend base URL:

```text
http://localhost:9090/api
```

Common endpoints:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/login/google` | Student Google login validation |
| GET | `/sessions` | List available sessions from courses |
| GET | `/courses?email={email}&session={session}` | List courses for a student |
| POST | `/enroll?session={session}` | Enroll student |
| POST | `/enroll/change?session={session}` | Change student enrollment |
| GET | `/enrollments?email={email}&session={session}` | Student enrollment list |
| POST | `/admin/login` | Admin login |
| GET | `/admin/courses?session={session}` | Admin course list |
| POST | `/admin/courses?session={session}` | Add course |
| PUT | `/admin/courses/{code}?session={session}` | Update course |
| DELETE | `/admin/courses/{code}?session={session}` | Delete course |
| GET | `/admin/enrollments?session={session}` | Admin enrollment list |
| POST | `/admin/upload/students?session={session}` | Upload students Excel |
| POST | `/admin/upload/courses?session={session}` | Upload courses Excel |

## 11. Build and Test

Compile backend without running tests:

```powershell
cd OE-Backend
.\mvnw.cmd -DskipTests compile
```

Windows fallback:

```powershell
cd OE-Backend
mvn -DskipTests compile
```

Run backend tests:

```powershell
cd OE-Backend
.\mvnw.cmd test
```

Create a backend jar:

```powershell
cd OE-Backend
.\mvnw.cmd clean package
```

Run the jar:

```powershell
java -jar target\oejava-0.0.1-SNAPSHOT.jar
```

## 12. Troubleshooting

Backend cannot connect to MySQL:

- Confirm MySQL Server is running.
- Confirm database `open_elective` exists.
- Confirm username/password in `application.properties`.
- Confirm MySQL is listening on port `3306`.

Frontend shows network/server error:

- Confirm backend is running on `http://localhost:9090`.
- Confirm frontend is running on `http://localhost:5500`.
- Do not open the HTML file directly with `file://`.

No sessions appear:

- Add at least one row to the `courses` table.
- The `/api/sessions` endpoint reads distinct values from `courses.session`.

Student cannot log in:

- Confirm the register number exists in `students.registration_no`.
- Confirm the Google email matches `students.email`.
- Confirm the backend is running before login.

Admin cannot log in:

- Confirm the admin row exists in `admins`.
- Try the sample account `admin@svce.ac.in` / `admin123`.

Port already in use:

- Backend uses port `9090`.
- Frontend should use port `5500`.
- If you change ports, update CORS in `OE-Backend/src/main/java/com/svce/oejava/CorsConfig.java` and API URLs in the frontend JavaScript files.
