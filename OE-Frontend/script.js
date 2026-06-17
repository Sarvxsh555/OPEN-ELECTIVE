// Firebase imports
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.0/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
} from "https://www.gstatic.com/firebasejs/10.12.0/firebase-auth.js";

// Your Firebase config
const firebaseConfig = {
  apiKey: "AIzaSyDKIaVRv0JEKGzR5T5EzAZKCD6mkaQumzQ",
  authDomain: "svce-oe-login.firebaseapp.com",
  projectId: "svce-oe-login",
  storageBucket: "svce-oe-login.firebasestorage.app",
  messagingSenderId: "766393548954",
  appId: "1:766393548954:web:c0ce1976622c8e45fc5f23",
  measurementId: "G-98MDRMXP69"
};


// Initialize Firebase
const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const provider = new GoogleAuthProvider();
let loginInProgress = false;

// Google Login Function
window.googleLogin = async function () {
  const regno = document.getElementById("regno").value.trim();
  const errorMessage = document.getElementById("errorMessage");
  const loginBtn = document.getElementById("googleLoginBtn");

  if (loginInProgress) {
    return;
  }

  errorMessage.textContent = "";

  // Check regno
  if (regno === "") {
    errorMessage.textContent = "Please enter your Register Number.";
    return;
  }

  try {
    loginInProgress = true;
    if (loginBtn) {
      loginBtn.disabled = true;
      loginBtn.classList.add("opacity-60", "cursor-not-allowed");
    }

    // Show Google Popup
    const result = await signInWithPopup(auth, provider);
    const user = result.user;

    // Send data to backend
    const resp = await fetch("http://localhost:9090/api/login/google", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        regno: regno,
        email: user.email,
      }),
    });

    const data = await resp.json();

    if (resp.ok && data.success) {
      // Save student details
      localStorage.setItem(
        "studentDetails",
        JSON.stringify(data.studentDetails)
      );
      window.location.href = "courseSelection.html";
    } else {
      errorMessage.textContent = data.message || "Login failed!";
    }
  } catch (err) {
    console.error("Firebase Google Login Error:", err);
    if (err && err.code === "auth/cancelled-popup-request") {
      errorMessage.textContent = "Login popup was already open. Please try once.";
    } else if (err && err.code === "auth/popup-closed-by-user") {
      errorMessage.textContent = "Google login was closed before completion.";
    } else {
      errorMessage.textContent = "Login failed. Please try again.";
    }
  } finally {
    loginInProgress = false;
    if (loginBtn) {
      loginBtn.disabled = false;
      loginBtn.classList.remove("opacity-60", "cursor-not-allowed");
    }
  }
};
