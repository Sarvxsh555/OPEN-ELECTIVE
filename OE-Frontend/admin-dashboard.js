// admin-dashboard.js
document.addEventListener("DOMContentLoaded", () => {
  // --- AUTH CHECK ---
  if (localStorage.getItem("adminLoggedIn") !== "true") {
    alert("Access denied. Please log in as admin.");
    window.location.href = "admin-login.html";
    return;
  }

  // --- ELEMENTS ---
  const studentFileInput = document.getElementById("studentFile");
  const courseFileInput = document.getElementById("courseFile");
  const uploadStudentsBtn = document.getElementById("uploadStudentsBtn");
  const uploadCoursesBtn = document.getElementById("uploadCoursesBtn");
  const truncateConfirmModal = document.getElementById("truncateConfirmModal");
  const truncateConfirmForm = document.getElementById("truncateConfirmForm");
  const truncateTargetLabel = document.getElementById("truncateTargetLabel");
  const truncateCancelBtn = document.getElementById("truncateCancelBtn");
  const truncateStudentsBtn = document.getElementById("truncateStudents");
  const truncateCoursesBtn = document.getElementById("truncateCourses");
  const truncateEnrollmentsBtn = document.getElementById("truncateEnrollments");
  const courseUploadLabel = document.getElementById("courseUploadLabel");

  const enrollmentsTableBody = document.getElementById("enrollmentsTableBody");
  const coursesTableBody = document.getElementById("coursesTableBody");
  const addCourseForm = document.getElementById("addCourseForm");
  const logoutBtn = document.getElementById("logoutBtn");
  const enrollmentSearch = document.getElementById("enrollmentSearch");

  const editCourseModal = document.getElementById("editCourseModal");
  const editCourseForm = document.getElementById("editCourseForm");
  const cancelEditBtn = document.getElementById("cancelEditBtn");
  const deleteCourseBtn = document.getElementById("deleteCourseBtn");

  const adminSessionSelect = document.getElementById("adminSessionSelect");

  const downloadAllGroupBy = document.getElementById("downloadAllGroupBy");
  const downloadAllPdfBtn = document.getElementById("downloadAllPdfBtn");
  const downloadAllExcelBtn = document.getElementById("downloadAllExcelBtn");

  const enrolledDeptSelect = document.getElementById("enrolledDeptSelect");
  const notEnrolledDeptSelect = document.getElementById("notEnrolledDeptSelect");
  const downloadEnrolledDeptPdfBtn = document.getElementById("downloadEnrolledDeptPdfBtn");
  const downloadEnrolledDeptExcelBtn = document.getElementById("downloadEnrolledDeptExcelBtn");
  const downloadNotEnrolledDeptPdfBtn = document.getElementById("downloadNotEnrolledDeptPdfBtn");
  const downloadNotEnrolledDeptExcelBtn = document.getElementById("downloadNotEnrolledDeptExcelBtn");

  // NEW: academic year inputs (no set button)
  const startYearInput = document.getElementById("startYear");
  const endYearInput = document.getElementById("endYear");

  // session UI wrappers
  const sessionWarning = document.getElementById("sessionWarning");
  const sessionContent = document.getElementById("sessionContent");

  const API_BASE_URL = "http://localhost:9090/api";
  let courses = [];
  let truncateTarget = null; // "students" or "courses" or "enrollments"
  let allEnrollmentsCache = [];

  // --- SESSION (currentSession like "25-26OS") ---
  let currentSession = null;

  // helper: format two-digit short year
  function shortYr(y) {
    return (y % 100).toString().padStart(2, "0");
  }

  // Build session string from inputs, returns session or null if invalid
  function buildSessionFromInputs() {
    const syVal = startYearInput ? startYearInput.value.trim() : "";
    const eyVal = endYearInput ? endYearInput.value.trim() : "";
    const sem = adminSessionSelect ? adminSessionSelect.value : "OS";

    const sy = parseInt(syVal, 10);
    const ey = parseInt(eyVal, 10);

    if (!sy || !ey || ey < sy) return null;
    return `${shortYr(sy)}-${shortYr(ey)}${sem}`; // e.g. 25-26OS
  }

  // Save session pieces to localStorage
  function persistSession(s) {
    try {
      localStorage.setItem("savedSession", s);
      // also save explicit pieces for easier UI restore/edit
      if (startYearInput && endYearInput && adminSessionSelect) {
        localStorage.setItem("savedStartYear", startYearInput.value);
        localStorage.setItem("savedEndYear", endYearInput.value);
        localStorage.setItem("savedSem", adminSessionSelect.value);
      }
    } catch (e) {
      console.warn("Could not persist session to localStorage", e);
    }
  }

  // Remove saved session (not used automatically; kept for debug)
  function clearPersistedSession() {
    localStorage.removeItem("savedSession");
    localStorage.removeItem("savedStartYear");
    localStorage.removeItem("savedEndYear");
    localStorage.removeItem("savedSem");
  }

  // Called when currentSession becomes available (either restored or built)
  function onSessionReady(sessionStr) {
    currentSession = sessionStr;
    // show/hide UI sections
    if (sessionWarning) sessionWarning.style.display = "none";
    if (sessionContent) sessionContent.style.display = ""; // show
    // update OS/ES option text to show selected years (if selects exist)
    updateSessionOptionTexts();
    // enable session dependent controls
    setSessionDependentEnabled(true);
    // persist
    persistSession(sessionStr);
    // fetch data
    fetchAndRenderAll();
    // update courseUploadLabel text accordingly
    updateCourseUploadLabel();
  }

  // update option innerText for clarity to include years
  function updateSessionOptionTexts() {
    if (!adminSessionSelect) return;
    const sy = startYearInput ? parseInt(startYearInput.value || "", 10) : NaN;
    const ey = endYearInput ? parseInt(endYearInput.value || "", 10) : NaN;
    const sy2 = !isNaN(sy) ? shortYr(sy) : "";
    const ey2 = !isNaN(ey) ? shortYr(ey) : "";
    const os = Array.from(adminSessionSelect.options).find((o) => o.value === "OS");
    const es = Array.from(adminSessionSelect.options).find((o) => o.value === "ES");
    if (os && sy2 && ey2) os.text = `${sy2}-${ey2} OS (Odd Semester)`;
    if (es && sy2 && ey2) es.text = `${sy2}-${ey2} ES (Even Semester)`;
  }

  // update course upload label based on selected semester
  function updateCourseUploadLabel() {
    if (!courseUploadLabel || !adminSessionSelect) return;
    const sem = adminSessionSelect.value;
    courseUploadLabel.textContent =
      sem === "OS"
        ? "Upload course details for Odd semester"
        : "Upload course details for Even semester";
  }

  // Get session (may be null)
  function getSession() {
    return currentSession;
  }

  // enable/disable controls that depend on session
  function setSessionDependentEnabled(enabled) {
    const controls = [
      uploadStudentsBtn,
      uploadCoursesBtn,
      truncateStudentsBtn,
      truncateCoursesBtn,
      truncateEnrollmentsBtn,
      downloadAllPdfBtn,
      downloadAllExcelBtn,
      downloadEnrolledDeptPdfBtn,
      downloadEnrolledDeptExcelBtn,
      downloadNotEnrolledDeptPdfBtn,
      downloadNotEnrolledDeptExcelBtn,
      addCourseForm,
      document.getElementById("adminEnrollForm"),
      truncateConfirmForm,
    ].filter(Boolean);

    controls.forEach((el) => {
      // forms -> disable their fields
      try {
        if (el.tagName === "FORM") {
          el.querySelectorAll("input, select, button, textarea").forEach((child) => {
            child.disabled = !enabled;
          });
        } else {
          el.disabled = !enabled;
        }
      } catch (err) {
        // defensive - some elements might not exist or be weird
        try {
          el.disabled = !enabled;
        } catch (e) {}
      }
    });
  }

  // Initially disable session-dependent UI
  setSessionDependentEnabled(false);

  // --- RESTORE SESSION IF PRESENT IN localStorage (Option A) ---
  (function tryRestoreSession() {
    const saved = localStorage.getItem("savedSession");
    const savedStart = localStorage.getItem("savedStartYear");
    const savedEnd = localStorage.getItem("savedEndYear");
    const savedSem = localStorage.getItem("savedSem");
    if (saved && savedStart && savedEnd && savedSem) {
      // populate inputs (if present)
      if (startYearInput) startYearInput.value = savedStart;
      if (endYearInput) endYearInput.value = savedEnd;
      if (adminSessionSelect) adminSessionSelect.value = savedSem;
      // finalize
      onSessionReady(saved);
    } else {
      // show warning and hide sessionContent
      if (sessionWarning) sessionWarning.style.display = "";
      if (sessionContent) sessionContent.style.display = "none";
      setSessionDependentEnabled(false);
    }
  })();

  // --- AUTO-SET SESSION WHEN ADMIN FILLS START/END + SEM (no button) ---
  function tryAutoSetSessionFromInputs() {
    const s = buildSessionFromInputs();
    if (!s) {
      // invalid or incomplete -> keep disabled
      if (sessionWarning) sessionWarning.style.display = "";
      if (sessionContent) sessionContent.style.display = "none";
      setSessionDependentEnabled(false);
      currentSession = null;
      return;
    }
    // if session changed -> apply
    if (s !== currentSession) {
      onSessionReady(s);
    }
  }

  // attach listeners to inputs/select so when admin edits, the session is auto-built
  if (startYearInput) {
    startYearInput.addEventListener("input", tryAutoSetSessionFromInputs);
    startYearInput.addEventListener("blur", tryAutoSetSessionFromInputs);
  }
  if (endYearInput) {
    endYearInput.addEventListener("input", tryAutoSetSessionFromInputs);
    endYearInput.addEventListener("blur", tryAutoSetSessionFromInputs);
  }
  if (adminSessionSelect) {
    adminSessionSelect.addEventListener("change", () => {
      tryAutoSetSessionFromInputs();
      updateCourseUploadLabel();
    });
  }

  // --- FETCH / RENDER LOGIC (unchanged functionalities) ---
  async function fetchAndRenderAll() {
    await fetchAndRenderCourses();
    await fetchAndRenderEnrollments();
  }

  async function fetchAndRenderCourses() {
    try {
      const session = getSession();
      if (!session) return;
      const response = await fetch(
        `${API_BASE_URL}/admin/courses?session=${encodeURIComponent(session)}`
      );
      courses = await response.json();
      if (!coursesTableBody) return;
      coursesTableBody.innerHTML = "";

      courses.forEach((course) => {
        const row = document.createElement("tr");
        row.innerHTML = `
          <td class="py-2 px-4 border-b">${course.id ?? ""}</td>
          <td class="py-2 px-4 border-b">${course.code ?? ""}</td>
          <td class="py-2 px-4 border-b">${course.title ?? ""}</td>
          <td class="py-2 px-4 border-b">${course.instructor ?? ""}</td>
          <td class="py-2 px-4 border-b">${course.department ?? ""}</td>
          <td class="py-2 px-4 border-b">${course.enrolled ?? 0}/${course.capacity ?? 0}</td>
          <td class="py-2 px-4 border-b">${course.restricted ?? ""}</td>
          <td class="py-2 px-4 border-b">
            <button class="edit-btn bg-yellow-500 text-white px-3 py-1 rounded"
                    data-code="${course.code}">Edit</button>
          </td>
          <td class="py-2 px-4 border-b">
            <button class="download-course-pdf bg-blue-600 text-white px-3 py-1 rounded"
                    data-code="${course.code}">PDF</button>
            <button class="download-course-excel bg-emerald-600 text-white px-3 py-1 rounded"
                    data-code="${course.code}">Excel</button>
          </td>
        `;
        coursesTableBody.appendChild(row);
      });

      // attach download handlers
      document.querySelectorAll(".download-course-pdf").forEach((btn) => {
        btn.addEventListener("click", () => {
          const courseCode = btn.getAttribute("data-code");
          const session = getSession();
          if (!session) {
            alert("Set academic year & session first.");
            return;
          }
          const url = `${API_BASE_URL}/admin/courses/${encodeURIComponent(courseCode)}/pdf?session=${encodeURIComponent(session)}`;
          window.location.href = url;
        });
      });

      document.querySelectorAll(".download-course-excel").forEach((btn) => {
        btn.addEventListener("click", () => {
          const courseCode = btn.getAttribute("data-code");
          const session = getSession();
          if (!session) {
            alert("Set academic year & session first.");
            return;
          }
          const url = `${API_BASE_URL}/admin/courses/${encodeURIComponent(courseCode)}/excel?session=${encodeURIComponent(session)}`;
          window.location.href = url;
        });
      });
    } catch (error) {
      console.error("Failed to fetch courses:", error);
    }
  }

  async function fetchAndRenderEnrollments() {
    try {
      const session = getSession();
      if (!session) return;
      const response = await fetch(`${API_BASE_URL}/admin/enrollments?session=${encodeURIComponent(session)}`);
      const enrollments = await response.json();
      allEnrollmentsCache = enrollments || [];
      renderEnrollmentsTable(allEnrollmentsCache);
    } catch (error) {
      console.error("Failed to fetch enrollments:", error);
    }
  }

  function renderEnrollmentsTable(data) {
    if (!enrollmentsTableBody) return;
    enrollmentsTableBody.innerHTML = "";
    (data || []).forEach((enrollment) => {
      const row = document.createElement("tr");
      row.innerHTML = `
        <td class="py-2 px-4 border-b">${enrollment["Register No"] ?? ""}</td>
        <td class="py-2 px-4 border-b">${enrollment.Name ?? ""}</td>
        <td class="py-2 px-4 border-b">${enrollment.Email ?? ""}</td>
        <td class="py-2 px-4 border-b">${enrollment.Department ?? ""}</td>
        <td class="py-2 px-4 border-b">${enrollment["Course Code"] ?? ""}</td>
        <td class="py-2 px-4 border-b">${enrollment["Course Title"] ?? ""}</td>
        <td class="py-2 px-4 border-b">
          <button class="delete-btn bg-red-500 text-white px-3 py-1 rounded"
                  data-email="${enrollment.Email ?? ""}"
                  data-code="${enrollment["Course Code"] ?? ""}">
            Delete
          </button>
        </td>
      `;
      enrollmentsTableBody.appendChild(row);
    });
  }

  // --- TRUNCATE MODAL helpers ---
  function openTruncateModal(target) {
    truncateTarget = target;
    if (!truncateTargetLabel) return;
    if (target === "students") {
      truncateTargetLabel.textContent = "ALL students";
    } else if (target === "courses") {
      truncateTargetLabel.textContent = "ALL courses";
    } else if (target === "enrollments") {
      truncateTargetLabel.textContent = "ALL enrollments";
    }
    if (truncateConfirmModal) truncateConfirmModal.classList.remove("hidden");
  }

  function closeTruncateModal() {
    if (truncateConfirmModal) truncateConfirmModal.classList.add("hidden");
    if (truncateConfirmForm) truncateConfirmForm.reset();
  }

  // --- EVENTS (existing functionality ported) ---
  // enrollment search
  if (enrollmentSearch) {
    enrollmentSearch.addEventListener("input", () => {
      const q = enrollmentSearch.value.trim();
      if (!q) {
        renderEnrollmentsTable(allEnrollmentsCache);
        return;
      }
      const filtered = allEnrollmentsCache.filter((e) => {
        const reg = String(e["Register No"] || "").trim();
        return reg.toLowerCase().startsWith(q.toLowerCase());
      });
      renderEnrollmentsTable(filtered);
    });
  }

  // add course
  if (addCourseForm) {
    addCourseForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const session = getSession();
      if (!session) {
        alert("Set academic year & session first.");
        return;
      }
      const newCourse = {
        title: document.getElementById("title").value,
        code: document.getElementById("code").value,
        instructor: document.getElementById("instructor").value,
        department: document.getElementById("department").value,
        capacity: parseInt(document.getElementById("capacity").value, 10),
        restricted: document.getElementById("restricted").value,
        session: session,
      };
      try {
        const response = await fetch(`${API_BASE_URL}/admin/courses?session=${encodeURIComponent(session)}`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(newCourse),
        });
        const result = await response.json();
        alert(result.message || "Course added.");
        addCourseForm.reset();
        fetchAndRenderAll();
      } catch (error) {
        alert("Failed to add course.");
        console.error("Error adding course:", error);
      }
    });
  }

  // enrollments delete (table delegation)
  if (enrollmentsTableBody) {
    enrollmentsTableBody.addEventListener("click", async (e) => {
      if (e.target.classList.contains("delete-btn")) {
        const studentEmail = e.target.dataset.email;
        const courseCode = e.target.dataset.code;
        const session = getSession();
        if (!session) {
          alert("Set academic year & session first.");
          return;
        }
        if (confirm(`Are you sure you want to delete the enrollment for ${studentEmail} in ${courseCode}?`)) {
          try {
            const response = await fetch(`${API_BASE_URL}/admin/enrollments?email=${encodeURIComponent(studentEmail)}&courseCode=${encodeURIComponent(courseCode)}&session=${encodeURIComponent(session)}`, { method: "DELETE" });
            if (response.ok) {
              alert("Enrollment deleted successfully.");
              fetchAndRenderAll();
            } else {
              alert("Failed to delete enrollment.");
            }
          } catch (error) {
            console.error("Error deleting enrollment:", error);
          }
        }
      }
    });
  }

  // courses table edit button delegation
  if (coursesTableBody) {
    coursesTableBody.addEventListener("click", (e) => {
      if (e.target.classList.contains("edit-btn")) {
        const courseCode = e.target.dataset.code;
        const courseToEdit = courses.find((c) => c.code === courseCode);
        if (courseToEdit) {
          const editId = document.getElementById("editCourseId");
          const editTitle = document.getElementById("editTitle");
          const editInstructor = document.getElementById("editInstructor");
          const editCapacity = document.getElementById("editCapacity");
          if (editId) editId.value = courseToEdit.code;
          if (editTitle) editTitle.value = courseToEdit.title;
          if (editInstructor) editInstructor.value = courseToEdit.instructor;
          if (editCapacity) editCapacity.value = courseToEdit.capacity;
          if (editCourseModal) editCourseModal.classList.remove("hidden");
        }
      }
    });
  }

  // edit course submit
  if (editCourseForm) {
    editCourseForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const session = getSession();
      if (!session) {
        alert("Set academic year & session first.");
        return;
      }
      const courseCode = document.getElementById("editCourseId").value;
      const updatedCourse = {
        title: document.getElementById("editTitle").value,
        instructor: document.getElementById("editInstructor").value,
        capacity: parseInt(document.getElementById("editCapacity").value, 10),
        session: session,
      };
      try {
        const response = await fetch(`${API_BASE_URL}/admin/courses/${encodeURIComponent(courseCode)}?session=${encodeURIComponent(session)}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(updatedCourse),
        });
        const result = await response.json();
        alert(result.message || "Course updated.");
        if (editCourseModal) editCourseModal.classList.add("hidden");
        fetchAndRenderAll();
      } catch (error) {
        alert("Failed to update course.");
      }
    });
  }

  // delete course button in edit modal
  if (deleteCourseBtn) {
    deleteCourseBtn.addEventListener("click", async () => {
      const session = getSession();
      if (!session) {
        alert("Set academic year & session first.");
        return;
      }
      const courseCode = document.getElementById("editCourseId").value;
      if (confirm("Are you sure you want to permanently delete this course?")) {
        try {
          await fetch(`${API_BASE_URL}/admin/courses/${encodeURIComponent(courseCode)}?session=${encodeURIComponent(session)}`, { method: "DELETE" });
          alert("Course deleted successfully.");
          if (editCourseModal) editCourseModal.classList.add("hidden");
          fetchAndRenderAll();
        } catch (error) {
          alert("Failed to delete course.");
        }
      }
    });
  }

  if (cancelEditBtn) {
    cancelEditBtn.addEventListener("click", () => {
      if (editCourseModal) editCourseModal.classList.add("hidden");
    });
  }

  // manual enroll form
  const adminEnrollForm = document.getElementById("adminEnrollForm");
  if (adminEnrollForm) {
    adminEnrollForm.addEventListener("submit", async function (e) {
      e.preventDefault();
      const session = getSession();
      if (!session) {
        alert("Set academic year & session first.");
        return;
      }
      const email = document.getElementById("adminEnrollEmail").value.trim();
      const courseCode = document.getElementById("adminEnrollCourseCode").value.trim();

      const response = await fetch(`${API_BASE_URL}/admin/enroll?session=${encodeURIComponent(session)}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, courseCode }),
      });
      const result = await response.json();
      alert(result.message);
      fetchAndRenderEnrollments();
    });
  }

  // truncate buttons
  if (truncateStudentsBtn) truncateStudentsBtn.addEventListener("click", () => openTruncateModal("students"));
  if (truncateCoursesBtn) truncateCoursesBtn.addEventListener("click", () => openTruncateModal("courses"));
  if (truncateEnrollmentsBtn) truncateEnrollmentsBtn.addEventListener("click", () => openTruncateModal("enrollments"));
  if (truncateCancelBtn) truncateCancelBtn.addEventListener("click", closeTruncateModal);

  // truncate confirm submit
  if (truncateConfirmForm) {
    truncateConfirmForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      if (!truncateTarget) return;
      const session = getSession();
      if (!session) {
        alert("Set academic year & session first.");
        return;
      }
      const email = document.getElementById("truncateEmail").value.trim();
      const password = document.getElementById("truncatePassword").value.trim();

      try {
        const resp = await fetch(`${API_BASE_URL}/admin/verify-and-truncate?session=${encodeURIComponent(session)}`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email, password, target: truncateTarget }),
        });
        const result = await resp.json();
        alert(result.message || "Truncate operation completed.");
        if (resp.ok) {
          closeTruncateModal();
          fetchAndRenderAll();
        }
      } catch (err) {
        console.error("Truncate failed:", err);
        alert("Failed to truncate. Please try again.");
      }
    });
  }

  // downloads (all/pdf & excel)
  if (downloadAllPdfBtn) {
    downloadAllPdfBtn.addEventListener("click", () => {
      const session = getSession();
      if (!session) {
        alert("Set academic year & session first.");
        return;
      }
      const groupBy = downloadAllGroupBy ? downloadAllGroupBy.value : "dept";
      window.location.href = `${API_BASE_URL}/admin/downloads/all/pdf?groupBy=${encodeURIComponent(groupBy)}&session=${encodeURIComponent(session)}`;
    });
  }

  if (downloadAllExcelBtn) {
    downloadAllExcelBtn.addEventListener("click", () => {
      const session = getSession();
      if (!session) {
        alert("Set academic year & session first.");
        return;
      }
      const groupBy = downloadAllGroupBy ? downloadAllGroupBy.value : "dept";
      window.location.href = `${API_BASE_URL}/admin/downloads/all/excel?groupBy=${encodeURIComponent(groupBy)}&session=${encodeURIComponent(session)}`;
    });
  }

  // upload handlers
  if (uploadStudentsBtn) {
    uploadStudentsBtn.addEventListener("click", async () => {
      const file = studentFileInput?.files[0];
      if (!file) {
        alert("Please choose a student Excel file.");
        return;
      }
      const session = getSession();
      if (!session) {
        alert("Set academic year & session first.");
        return;
      }
      const formData = new FormData();
      formData.append("file", file);
      const resp = await fetch(`${API_BASE_URL}/admin/upload/students?session=${encodeURIComponent(session)}`, { method: "POST", body: formData });
      const result = await resp.json();
      alert(result.message || "Students uploaded.");
      fetchAndRenderAll();
    });
  }

  if (uploadCoursesBtn) {
    uploadCoursesBtn.addEventListener("click", async () => {
      const file = courseFileInput?.files[0];
      if (!file) {
        alert("Please choose a courses Excel file.");
        return;
      }
      const session = getSession();
      if (!session) {
        alert("Set academic year & session first.");
        return;
      }
      const formData = new FormData();
      formData.append("file", file);
      const resp = await fetch(`${API_BASE_URL}/admin/upload/courses?session=${encodeURIComponent(session)}`, { method: "POST", body: formData });
      const result = await resp.json();
      alert(result.message || "Courses uploaded.");
      fetchAndRenderAll();
    });
  }

  // enrolled / not-enrolled downloads
  if (downloadEnrolledDeptPdfBtn) {
    downloadEnrolledDeptPdfBtn.addEventListener("click", () => {
      const session = getSession();
      if (!session) {
        alert("Set academic year & session first.");
        return;
      }
      const dept = enrolledDeptSelect ? enrolledDeptSelect.value : "";
      window.location.href = `${API_BASE_URL}/admin/downloads/enrolled/pdf?session=${encodeURIComponent(session)}&dept=${encodeURIComponent(dept)}`;
    });
  }
  if (downloadEnrolledDeptExcelBtn) {
    downloadEnrolledDeptExcelBtn.addEventListener("click", () => {
      const session = getSession();
      if (!session) {
        alert("Set academic year & session first.");
        return;
      }
      const dept = enrolledDeptSelect ? enrolledDeptSelect.value : "";
      window.location.href = `${API_BASE_URL}/admin/downloads/enrolled/excel?session=${encodeURIComponent(session)}&dept=${encodeURIComponent(dept)}`;
    });
  }
  if (downloadNotEnrolledDeptPdfBtn) {
    downloadNotEnrolledDeptPdfBtn.addEventListener("click", () => {
      const session = getSession();
      if (!session) {
        alert("Set academic year & session first.");
        return;
      }
      const dept = notEnrolledDeptSelect ? notEnrolledDeptSelect.value : "";
      window.location.href = `${API_BASE_URL}/admin/downloads/not-enrolled/pdf?session=${encodeURIComponent(session)}&dept=${encodeURIComponent(dept)}`;
    });
  }
  if (downloadNotEnrolledDeptExcelBtn) {
    downloadNotEnrolledDeptExcelBtn.addEventListener("click", () => {
      const session = getSession();
      if (!session) {
        alert("Set academic year & session first.");
        return;
      }
      const dept = notEnrolledDeptSelect ? notEnrolledDeptSelect.value : "";
      window.location.href = `${API_BASE_URL}/admin/downloads/not-enrolled/excel?session=${encodeURIComponent(session)}&dept=${encodeURIComponent(dept)}`;
    });
  }

  // logout (do not clear saved session - Option A retains it)
  if (logoutBtn) {
    logoutBtn.addEventListener("click", (e) => {
      e.preventDefault();
      localStorage.removeItem("adminLoggedIn");
      localStorage.removeItem("adminEmail");
      // Keep saved session so admin doesn't re-enter next login (Option A).
      window.location.href = "admin-login.html";
    });
  }

  // Finally, ensure course upload label reflects current semester
  updateCourseUploadLabel();
}); // DOMContentLoaded end
