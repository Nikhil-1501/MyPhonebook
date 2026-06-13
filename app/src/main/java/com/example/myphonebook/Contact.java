package com.example.myphonebook;

import com.google.firebase.firestore.Exclude;

/**
 * Data model representing a Contact entity.
 * This class is used for both local SQLite storage and Firebase Firestore synchronization.
 */
public class Contact {
    private int id;                 // Local database ID (Auto-incremented)
    private String name;           // Contact's full name
    private String phoneNumber;    // Unique phone number
    private String email;          // Optional email address
    private String address;        // Optional physical address
    private byte[] image;          // Profile picture stored as byte array (BLOB in SQLite)
    private boolean isFavorite;    // Flag for favorite contacts
    private String remoteId;       // Unique document ID from Firebase Firestore
    private String imageUrl;       // URL for the profile image in Firebase Storage

    /**
     * Default constructor required for Firebase Firestore deserialization.
     */
    public Contact() {}

    /**
     * Constructor for creating a new contact (before it has a local ID).
     */
    public Contact(String name, String phoneNumber, String email, String address, byte[] image, boolean isFavorite) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.address = address;
        this.image = image;
        this.isFavorite = isFavorite;
    }

    /**
     * Constructor for loading an existing contact from the database.
     */
    public Contact(int id, String name, String phoneNumber, String email, String address, byte[] image, boolean isFavorite) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.address = address;
        this.image = image;
        this.isFavorite = isFavorite;
    }

    /**
     * Excluded from Firebase because Firestore uses its own Document ID system.
     */
    @Exclude
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    /**
     * Profile image is excluded from the main Firestore document. 
     * Images are stored separately in Firebase Storage for performance.
     */
    @Exclude
    public byte[] getImage() { return image; }
    public void setImage(byte[] image) { this.image = image; }
    
    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public String getRemoteId() { return remoteId; }
    public void setRemoteId(String remoteId) { this.remoteId = remoteId; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
