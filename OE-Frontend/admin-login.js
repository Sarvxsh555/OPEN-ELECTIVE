document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("adminLoginForm");
    const emailInput = document.getElementById("email");
    const passwordInput = document.getElementById("password");
    const loginError = document.getElementById("loginError");

    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        loginError.style.display = "none";

        const email = emailInput.value.trim();
        const password = passwordInput.value;

        try {
            const response = await fetch("http://localhost:9090/api/admin/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email, password }),
            });

            const data = await response.json();

            if (!data.success) {
                loginError.textContent = data.message || "Login failed";
                loginError.style.display = "block";
                return;
            }

            // Store a simple flag to indicate admin is logged in
            localStorage.setItem("adminLoggedIn", "true");
            localStorage.setItem("adminEmail", data.adminDetails.email);

            // Redirect to the admin dashboard (we will create this page next)
            window.location.href = "admin-dashboard.html";

        } catch (err) {
            console.error(err);
            loginError.textContent = "Network error or server is down.";
            loginError.style.display = "block";
        }
    });
});