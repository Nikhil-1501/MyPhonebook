# MyPhonebook 📱

A modern, feature-rich Android contact management application built with **Material Design 3**, **SQLite**, and **Firebase**. This app provides a seamless experience for managing personal contacts with real-time cloud synchronization and advanced sharing capabilities.

---

## 📺 Application Walkthrough

https://github.com/user-attachments/assets/ab8753fc-cceb-4155-a30d-41f951cc0dfb

---

## 🚀 Features

### **Contact Management**
*   **Full CRUD**: Create, Read, Update, and Delete contacts.
*   **Favorites**: Mark contacts as favorites for quick access.
*   **Smart Search**: Filter contacts by name or phone number in real-time.
*   **Alphabetical Indexing**: Automatic grouping of contacts with sticky headers.

### **Media & UI**
*   **Material 3 UI**: Clean, modern interface following the latest Android design standards.
*   **Circular Profile Imagery**: Uses `ShapeableImageView` for consistent, professional profile pictures.
*   **Interactive Photos**: Tap the profile picture to capture from the camera or pick from the gallery.

### **Sync & Storage**
*   **Offline First**: Uses **SQLite** for instant access to data even without internet.
*   **Cloud Sync**: Real-time synchronization with **Firebase Firestore**.
*   **Secure Image Hosting**: Contact photos are stored in **Firebase Storage**.
*   **Multi-Device Access**: Sign in with **Google** or **Email** to access your contacts from any device.

### **Sharing & Utilities**
*   **QR Code Integration**:
    *   **Share**: Generate vCard QR codes for any contact.
    *   **Scan**: Quickly add new contacts by scanning their QR code.
*   **VCF Support**: Share contacts as standard `.vcf` files via email or messaging apps.
*   **CSV Export/Import**: Backup and restore your entire contact list.

---

## 🛠 Tech Stack

*   **Language**: Java (Android SDK)
*   **UI Framework**: Material Design 3 (Material Components)
*   **Architecture**: ViewBinding for type-safe UI interaction.
*   **Local Database**: SQLite (via `SQLiteOpenHelper`)
*   **Backend**: 
    *   Firebase Authentication (Google & Email)
    *   Firebase Firestore (NoSQL database)
    *   Firebase Storage (Image hosting)
*   **Libraries**:
    *   ZXing (QR Code generation/scanning)
    *   SwipeRefreshLayout (Pull-to-refresh sync)
    *   ConstraintLayout (Responsive UI)

---

## 📁 Project Structure

```
app/src/main/java/com/example/myphonebook/
├── MainActivity.java           # Main contact list and navigation hub
├── EditContactActivity.java     # Add/Edit screen with image and sharing logic
├── Contact.java                # Data model (POJO)
├── DBHandler.java              # SQLite database management
├── FirebaseSyncHelper.java     # Cloud synchronization logic
├── RateLimiter.java            # Security: Auth & Action rate limiting
├── ContactsAdapter.java        # RecyclerView adapter for the contact list
├── PrivacyPolicyActivity.java  # In-app privacy documentation
└── CaptureActivityPortrait.java # Custom QR scanner activity
```

---

## 🛡 Security & Optimization

*   **Input Sanitization**: All user inputs are trimmed and validated against size limits and Regex patterns.
*   **Rate Limiting**: 
    *   Authentication is limited to 5 attempts per 15 minutes.
    *   General actions (like sync) are throttled to prevent server abuse.
*   **Payload Protection**: Image uploads are capped at 5MB to prevent OOM crashes while supporting high-quality photos.
*   **Data Integrity**: Uses Firestore partial updates (`.update()`) instead of `.set()` to merge cloud data with local changes, preventing accidental loss of profile image URLs during sync.
*   **Build Optimization**: Gradle parallel execution and daemon enabled for faster build times.

---

## ⚙️ Setup Instructions

### **Prerequisites**
*   Android Studio Ladybug (or newer).
*   A Firebase project (Google-services.json required).

### **Installation**
1.  **Clone the project**:
    ```bash
    git clone https://github.com/Nikhil-1501/MyPhonebook.git
    ```
2.  **Add Firebase**:
    *   Place your `google-services.json` in the `app/` directory.
    *   Enable **Firestore**, **Authentication**, and **Storage** in the Firebase Console.
3.  **Build**:
    *   Open the project in Android Studio.
    *   Sync Gradle and run the app on an emulator or physical device.

---

## 📖 Usage Guide

1.  **Adding a Contact**: Click the FAB (+) on the main screen. Fill in the details and tap the circular image to add a photo.
2.  **Sharing via QR**: Open a contact, tap the triple-dot menu (⋮), and select **Share QR**.
3.  **Syncing**: Pull down on the main list to trigger a cloud sync. Ensure you are signed in via the Settings tab.
4.  **Bulk Export**: Go to Settings and use the **Export to CSV** feature to create a backup.

---

## 📸 App Gallery

<p align="center">
  <img src="https://github.com/user-attachments/assets/553101f2-24c9-419f-b27d-207c322714e6" width="240" title="Main Contact List" alt="Main Contact List" />
  <img src="https://github.com/user-attachments/assets/1eae59fd-5280-4278-b77c-5663c28abc12" width="240" title="Add Contact" alt="Add Contact" />
  <img src="https://github.com/user-attachments/assets/7011ac63-7a5e-4150-a449-37ab5df79d36" width="240" title="Dropdown Options" alt="Dropdown Options" />
</p>

<p align="center">
  <img src="https://github.com/user-attachments/assets/71232b5b-b6f9-46e0-80d0-e918e6ccc3ca" width="240" title="Delete Contact" alt="Delete Contact" />
  <img src="https://github.com/user-attachments/assets/88828930-7800-4bed-b720-0d4c1ddad281" width="240" title="Settings" alt="Settings" />
  <img src="https://github.com/user-attachments/assets/8ced85b1-08c7-4ec2-8c1b-6db18d2c5374" width="240" title="Cloud Sync" alt="Cloud Sync" />
</p>

<p align="center">
  <img src="https://github.com/user-attachments/assets/4238f863-488d-4343-b9c3-82b952b34439" width="240" title="Favorites" alt="Favorites" />
  <img src="https://github.com/user-attachments/assets/7863ddff-662e-45f7-9757-ae1328e2406a" width="240" title="Dark Mode Settings" alt="Dark Mode Settings" />
  <img src="https://github.com/user-attachments/assets/274740fa-cc01-4cd0-8adf-82759cd55759" width="240" title="Dark Mode Home" alt="Dark Mode Home" />
</p>

<p align="center">
  <img src="https://github.com/user-attachments/assets/82c3c992-a69d-4669-a0bc-1214afbb3ed6" width="240" title="Share via QR" alt="Share via QR" />
  <img src="https://github.com/user-attachments/assets/4851607c-9a76-47d2-bec2-00a38543512c" width="240" title="Dark Mode Favorites" alt="Dark Mode Favorites" />
</p>

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
