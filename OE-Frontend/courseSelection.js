document.addEventListener("DOMContentLoaded", () => {
    let selectedCourse = null;
    let currentEnrolledCourseCode = null;
    let changeMode = false;
  
    const studentDetails = JSON.parse(localStorage.getItem("studentDetails"));
    if (!studentDetails) {
      alert("Please log in to view this page.");
      window.location.href = "../index.html";
      return;
    }
  
    // --- 1. Logic to extract Department from Email ---
    const getStudentDeptFromEmail = (email) => {
      if (!email) return "";
      const match = email.match(/^\d{4}([a-zA-Z]+)/);
      return match ? match[1].toLowerCase() : "";
    };
  
    const studentDept = getStudentDeptFromEmail(studentDetails.email);
    console.log("Derived Student Dept:", studentDept); 
  
    const studentInfoDiv = document.getElementById("studentInfo");
    if (studentInfoDiv && studentDetails) {
      studentInfoDiv.innerHTML = `Name: ${studentDetails.name}<br>Department: ${studentDetails.branch}<br>Register Number : ${studentDetails.registrationNo}`;
    }
  
    const courseList = document.getElementById("courseGrid");
    const enrolledCoursesList = document.getElementById("enrolledCoursesList");
    const logoutBtn = document.getElementById("logoutBtn");
    const submitBtn = document.getElementById("submitBtn");
    const changeButton = document.getElementById("changeBtn");
    const sessionSelect = document.getElementById("sessionSelect");
    const API_BASE_URL = "http://localhost:9090/api";
  fetchSessions();
  document.getElementById("sessionSelect").addEventListener("change", () => {
    fetchCourses();
    loadEnrolledCourses();
  });
    // --- Helper to Visualise Disabled Submit Button ---
    function updateSubmitButtonVisuals() {
        if (!submitBtn) return;
        if (submitBtn.disabled) {
            // Make it Grey
            submitBtn.classList.remove("bg-blue-600", "hover:bg-blue-700");
            submitBtn.classList.add("bg-gray-400", "cursor-not-allowed");
        } else {
            // Make it Blue (Active)
            submitBtn.classList.remove("bg-gray-400", "cursor-not-allowed");
            submitBtn.classList.add("bg-blue-600", "hover:bg-blue-700");
        }
    }

    // Initialize Submit Button as Disabled (Grey)
    if (submitBtn) {
        submitBtn.disabled = true;
        updateSubmitButtonVisuals();
    }
  
    document.addEventListener("click", async (e) => {
      const target = e.target;
  
      // SELECT button
      if (target && target.classList && target.classList.contains("select-btn")) {
        try {
          const raw = target.dataset.courseId || target.getAttribute("data-course-id");
          const json = decodeURIComponent(raw || "");
          const courseData = JSON.parse(json);
          selectedCourse = courseData;
  
          showSelectedCourse(selectedCourse);
  
          if (submitBtn) {
            // Enable Submit ONLY if:
            // 1. Not currently enrolled (First time)
            // 2. OR In Change Mode AND selected course is different from current
            if (
              !currentEnrolledCourseCode || 
              (changeMode && courseData.code !== currentEnrolledCourseCode) 
            ) {
              submitBtn.disabled = false;
            } else {
              submitBtn.disabled = true;
            }
            // Update the Grey/Blue color
            updateSubmitButtonVisuals();
          }
        } catch (err) {
          console.error("Failed to parse course data attribute:", err);
          alert("Unable to select course due to malformed data.");
          selectedCourse = null;
          if (submitBtn) {
             submitBtn.disabled = true;
             updateSubmitButtonVisuals();
          }
        }
        return;
      }
  
      // SUBMIT button
      if (target && target.id === "submitBtn") {
        if (!submitBtn || submitBtn.disabled) return;
        if (!selectedCourse) {
          alert("No course selected!");
          return;
        }
        if (changeMode) {
          await changeCourse(selectedCourse);
        } else {
          await enrollStudent(selectedCourse);
        }
        return;
      }
  
      // CHANGE button
      if (target && target.id === "changeBtn") {
        if (!currentEnrolledCourseCode) {
          alert("You're not enrolled in any course yet!");
          return;
        }
        enableSelectButtons();
        changeMode = true;
        selectedCourse = null;
        
        // Disable submit button until they pick a NEW course
        if (submitBtn) {
            submitBtn.disabled = true;
            updateSubmitButtonVisuals();
        }
  
        const selectedContainer = document.getElementById("selectedCourse");
        const detailsContainer = document.getElementById("selectionDetails");
        if (selectedContainer && detailsContainer) {
          detailsContainer.classList.add("hidden");
          selectedContainer.classList.remove("hidden");
        }
  
        alert("Select a new course to change to. You cannot pick your current course.");
        return;
      }
    });
  
    // LOGOUT button
    if (logoutBtn) {
      logoutBtn.addEventListener("click", () => {
        localStorage.removeItem("studentDetails");
        window.location.href = "index.html";
      });
    }
  async function fetchSessions() {
  try {
    const response = await fetch(`${API_BASE_URL}/sessions`);
    const sessions = await response.json();
    const sessionSelect = document.getElementById("sessionSelect");
    sessionSelect.innerHTML = '<option value="" disabled selected>Select Session</option>';
    sessions.forEach(session => {
      const option = document.createElement("option");
      option.value = session;
      option.textContent = session;
      sessionSelect.appendChild(option);
    });
    // Show message to select a session
    document.getElementById("courseGrid").innerHTML = "<p class='text-gray-500'>Please select a session to view courses.</p>";
  } catch (error) {
    console.error("fetchSessions error:", error);
  }
}

    async function fetchCourses() {
  const session = sessionSelect ? sessionSelect.value : "";
  if (!session) {
    document.getElementById("courseGrid").innerHTML = "<p class='text-gray-500'>Please select a session to view courses.</p>";
    return;
  }
  try {
    const response = await fetch(
      `${API_BASE_URL}/courses?email=${encodeURIComponent(studentDetails.email)}&session=${encodeURIComponent(session)}`
    );
    const courses = await response.json();
    displayCourses(courses);
  } catch (error) {
    console.error("fetchCourses error:", error);
    document.getElementById("courseGrid").innerHTML = "<p class='text-red-500'>Could not load courses.</p>";
  }
}

  
    function displayCourses(courses) {
      if (!courseList) return;
      courseList.innerHTML = "";
  
      if (!Array.isArray(courses) || courses.length === 0) {
        courseList.innerHTML =
          "<p>No courses are currently available for your department or all courses are full.</p>";
        return;
      }
  
      courses.forEach((course) => {
        const seatsAvailable =
          typeof course.capacity === "number" &&
          typeof course.enrolled === "number"
            ? course.capacity - course.enrolled
            : "N/A";
  
        // --- 2. Check Logic for Restriction ---
        let isRestricted = false;
        if (course.restricted) {
          const restrictedList = course.restricted
            .split(/[\/,]+/)  
            .map((s) => s.trim().toLowerCase());
          
          if (restrictedList.includes(studentDept)) {
            isRestricted = true;
          }
        }
  
        const isFull = seatsAvailable !== "N/A" && seatsAvailable <= 0;
        const isDisabled = !!course.disabled || isFull || isRestricted;
  
        // --- 3. UI Styling (Grey Card if Restricted) ---
        const cardBgClass = isRestricted ? "bg-gray-200 opacity-75" : "bg-white";
        const btnText = isRestricted ? "Disabled" : isFull ? "Full" : "Select";
        const btnColorClass = isRestricted || isFull 
            ? "bg-gray-400 cursor-not-allowed" 
            : "bg-blue-500 hover:bg-blue-600";
  
        const courseElement = document.createElement("div");
        courseElement.className = `course-card p-4 border rounded-lg flex justify-between items-center ${cardBgClass}`;
  
        const encoded = encodeURIComponent(JSON.stringify(course));
  
        courseElement.innerHTML = `
          <div>
            <h3 class="text-xl font-bold">
              ${escapeHtml(course.title || "")}
              <span class="text-xxl font-bold ">
                (${escapeHtml(course.code || "")})
              </span>
            </h3>
            <p class="text-gray-600">
              Instructor: ${escapeHtml(course.instructor || "")}
            </p>
            <p class="text-gray-600">
              Seats Available: ${escapeHtml(String(seatsAvailable))}
            </p>
            <span class="dept-pill inline-block mt-2 px-3 py-1 text-xs font-semibold rounded-full bg-indigo-100 text-indigo-800">
              ${escapeHtml(course.department || course.branch || "")}
            </span>
            <span class="ml-2 text-s text-gray-500">
              Session: ${escapeHtml(course.session || "")}
            </span>
          </div>
          <button
            class="select-btn text-white px-4 py-2 rounded disabled:opacity-50 disabled:cursor-not-allowed ${btnColorClass}"
            data-course-id="${encoded}"
            ${isDisabled ? "disabled" : ""}
          >
            ${btnText}
          </button>
        `;
  
        courseList.appendChild(courseElement);
      });
    }
  
    function showSelectedCourse(course) {
      const selectedContainer = document.getElementById("selectedCourse");
      const detailsContainer = document.getElementById("selectionDetails");
      const titleEl = document.getElementById("selectedCourseTitle");
      const instrEl = document.getElementById("selectedCourseInstructor");
      const detailsEl = document.getElementById("selectedCourseDetails");
  
      if (
        !selectedContainer ||
        !detailsContainer ||
        !titleEl ||
        !instrEl ||
        !detailsEl
      )
        return;
  
      selectedContainer.classList.add("hidden");
      detailsContainer.classList.remove("hidden");
  
      titleEl.textContent = `${course.title || ""} (${course.code || ""})`;
      instrEl.textContent = `Instructor: ${course.instructor || ""}`;
      detailsEl.textContent = `Department: ${
        course.department || course.branch || ""
      } | Session: ${course.session || ""}`;
    }
  
    async function enrollStudent(course) {
        const session = sessionSelect ? sessionSelect.value : "";
  if (!session) return; // Prevent enrollment without session

      try {
        const response = await fetch(
          `${API_BASE_URL}/enroll?session=${encodeURIComponent(session)}`,
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              email: studentDetails.email,
              name: studentDetails.name,
              dept: studentDetails.branch,
              selectedCourse: course,
            }),
          }
        );
  
        const data = await response.json();
        alert(data.message);
  
        if (!response.ok) {
          return;
        }
  
        currentEnrolledCourseCode = course.code;
        changeMode = false;
        if (submitBtn) {
            submitBtn.disabled = true;
            updateSubmitButtonVisuals();
        }
  
        await fetchCourses();
        await loadEnrolledCourses(); 
      } catch (err) {
        console.error("enrollStudent error:", err);
        alert("Enrollment failed due to a network error.");
      }
    }
  
    async function changeCourse(course) {
const session = sessionSelect ? sessionSelect.value : "";
  if (!session) return; // Prevent change without session
  
      try {
        const response = await fetch(
          `${API_BASE_URL}/enroll/change?session=${encodeURIComponent(session)}`,
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              email: studentDetails.email,
              oldCourseCode: currentEnrolledCourseCode,
              newCourseCode: course.code,
            }),
          }
        );
  
        const data = await response.json();
        alert(data.message);
  
        if (response.ok) {
          currentEnrolledCourseCode = course.code;
          changeMode = false;
          if (submitBtn) {
              submitBtn.disabled = true;
              updateSubmitButtonVisuals();
          }
  
          if (changeButton) {
            changeButton.disabled = true;
            changeButton.classList.add("opacity-50", "cursor-not-allowed");
          }
  
          await fetchCourses();
          await loadEnrolledCourses();
        }
      } catch (err) {
        console.error("changeCourse error:", err);
        alert("Change course failed due to a network error.");
      }
    }
  
    async function loadEnrolledCourses() {
      if (!enrolledCoursesList) return;
  
      const session = sessionSelect ? sessionSelect.value : "OS";
  
      try {
        const response = await fetch(
          `${API_BASE_URL}/enrollments?email=${encodeURIComponent(
            studentDetails.email
          )}&session=${encodeURIComponent(session)}`
        );
        const courses = await response.json();
  
        enrolledCoursesList.innerHTML = "";
  
        if (!Array.isArray(courses) || courses.length === 0) {
          enrolledCoursesList.innerHTML =
            "<li class='text-gray-500'>Not enrolled in any course for this session.</li>";
          currentEnrolledCourseCode = null;
  
          const detailsContainer = document.getElementById("selectionDetails");
          const selectedContainer = document.getElementById("selectedCourse");
          if (detailsContainer && selectedContainer) {
            detailsContainer.classList.add("hidden");
            selectedContainer.classList.remove("hidden");
          }
          return;
        }
  
        courses.forEach((course) => {
          const li = document.createElement("li");
          li.textContent = `${course.title || course.name || ""} (${
            course.code || ""
          })`;
          enrolledCoursesList.appendChild(li);
        });
  
        const enrolled = courses[0];
        currentEnrolledCourseCode = enrolled.code;
        showSelectedCourse(enrolled);
        
        // Ensure submit button is disabled (grey) if we found an enrollment
        if (submitBtn && !changeMode) {
            submitBtn.disabled = true;
            updateSubmitButtonVisuals();
        }

      } catch (err) {
        console.error("loadEnrolledCourses error:", err);
      }
    }
  
    function enableSelectButtons() {
      const buttons = document.querySelectorAll(".select-btn");
      buttons.forEach((btn) => {
        // Only enable if it is not restricted
        if (btn.textContent.trim() !== "Disabled" && btn.textContent.trim() !== "Full") {
            btn.classList.remove("opacity-50", "cursor-not-allowed");
            btn.disabled = false;
        }
      });
    }
  
    function escapeHtml(str) {
      return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
    }
  
    if (sessionSelect) {
      sessionSelect.addEventListener("change", () => {
        fetchCourses();
        loadEnrolledCourses();
      });
    }
  
    fetchCourses();
    loadEnrolledCourses();
  });