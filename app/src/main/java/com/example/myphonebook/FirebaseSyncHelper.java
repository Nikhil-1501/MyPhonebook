package com.example.myphonebook;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Helper class to manage synchronization between local SQLite database and Firebase Services.
 * Handles Firestore data sync and Firebase Storage image management.
 */
public class FirebaseSyncHelper {
    private static final String TAG = "FirebaseSyncHelper";
    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_CONTACTS = "contacts";
    
    private final FirebaseFirestore db;
    private final FirebaseStorage storage;
    private final FirebaseAuth auth;
    private final DBHandler dbHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public FirebaseSyncHelper(DBHandler dbHandler) {
        this.db = FirebaseFirestore.getInstance();
        this.storage = FirebaseStorage.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.dbHandler = dbHandler;
    }

    /**
     * Gets the current user's UID or returns "anonymous" if not logged in.
     */
    private String getUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : "anonymous";
    }

    /**
     * Main entry point for syncing a contact. 
     * If an image is present, it uploads the image first, then updates Firestore.
     */
    public void syncContact(Contact contact) {
        if (contact.getImage() != null) {
            uploadImageAndSync(contact);
        } else {
            saveToFirestore(contact);
        }
    }

    /**
     * Uploads the contact profile picture to Firebase Storage and retrieves its public URL.
     */
    private void uploadImageAndSync(Contact contact) {
        String fileName = "images/" + getUid() + "/" + (contact.getRemoteId() != null ? contact.getRemoteId() : System.currentTimeMillis()) + ".jpg";
        StorageReference imageRef = storage.getReference().child(fileName);

        UploadTask uploadTask = imageRef.putBytes(contact.getImage());
        uploadTask.addOnSuccessListener(taskSnapshot -> {
            imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                contact.setImageUrl(uri.toString());
                saveToFirestore(contact);
            });
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Image upload failed", e);
            saveToFirestore(contact);
        });
    }

    /**
     * Saves or updates the contact record in Firestore.
     * Uses partial updates (update()) to prevent overwriting the imageUrl with null.
     */
    private void saveToFirestore(Contact contact) {
        String uid = getUid();
        
        // Map containing fields to update. Using a map prevents overwriting 
        // fields that might be missing in the current local object (like imageUrl).
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("name", contact.getName());
        updates.put("phoneNumber", contact.getPhoneNumber());
        updates.put("email", contact.getEmail());
        updates.put("address", contact.getAddress());
        updates.put("favorite", contact.isFavorite());
        updates.put("remoteId", contact.getRemoteId());
        
        if (contact.getImageUrl() != null) {
            updates.put("imageUrl", contact.getImageUrl());
        }

        if (contact.getRemoteId() == null || contact.getRemoteId().isEmpty()) {
            // New contact: add to collection
            db.collection(COLLECTION_USERS).document(uid).collection(COLLECTION_CONTACTS)
                .add(contact)
                .addOnSuccessListener(documentReference -> {
                    contact.setRemoteId(documentReference.getId());
                    dbHandler.updateContact(contact); // Save the remote ID locally
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error adding contact", e));
        } else {
            // Existing contact: perform partial update
            db.collection(COLLECTION_USERS).document(uid).collection(COLLECTION_CONTACTS)
                .document(contact.getRemoteId())
                .update(updates)
                .addOnFailureListener(e -> Log.e(TAG, "Error updating contact", e));
        }
    }

    /**
     * Deletes a contact from Firestore given its remote ID.
     */
    public void deleteContact(String remoteId) {
        if (remoteId != null && !remoteId.isEmpty()) {
            db.collection(COLLECTION_USERS).document(getUid()).collection(COLLECTION_CONTACTS)
                .document(remoteId)
                .delete()
                .addOnFailureListener(e -> Log.e(TAG, "Error deleting contact", e));
        }
    }

    /**
     * Pulls all contacts from the cloud for the current user and merges them locally.
     */
    public void pullContactsFromServer(SyncCallback callback) {
        String uid = getUid();
        if (uid.equals("anonymous")) return;

        db.collection(COLLECTION_USERS).document(uid).collection(COLLECTION_CONTACTS)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                executor.execute(() -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Contact remoteContact = doc.toObject(Contact.class);
                        if (remoteContact != null) {
                            remoteContact.setRemoteId(doc.getId());
                            processPulledContact(remoteContact);
                        }
                    }
                    if (callback != null) callback.onSyncComplete();
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error pulling contacts", e);
                if (callback != null) callback.onSyncComplete();
            });
    }

    /**
     * Merges a pulled contact into the local database. 
     * Handles image recovery if the local image is missing.
     */
    private void processPulledContact(Contact remoteContact) {
        List<Contact> localContacts = dbHandler.getAllContacts();
        boolean exists = false;
        for (Contact local : localContacts) {
            if (remoteContact.getRemoteId().equals(local.getRemoteId()) || 
                remoteContact.getPhoneNumber().equals(local.getPhoneNumber())) {
                
                // If remote has an image URL but local has no image byte array, download it
                if (remoteContact.getImageUrl() != null && (local.getImage() == null || local.getImage().length == 0)) {
                    downloadAndSetImage(remoteContact);
                } else {
                    remoteContact.setId(local.getId());
                    // Keep the local image if it exists to avoid unnecessary downloads
                    if (local.getImage() != null) {
                        remoteContact.setImage(local.getImage());
                    }
                    dbHandler.updateContact(remoteContact);
                }
                exists = true;
                break;
            }
        }

        if (!exists) {
            // New contact from cloud
            if (remoteContact.getImageUrl() != null) {
                downloadAndSetImage(remoteContact);
            } else {
                dbHandler.addContact(remoteContact);
            }
        }
    }

    /**
     * Downloads an image from a URL and saves it to the local database.
     */
    private void downloadAndSetImage(Contact contact) {
        executor.execute(() -> {
            try {
                java.net.URL url = new java.net.URL(contact.getImageUrl());
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                java.io.InputStream input = connection.getInputStream();
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(input);
                
                if (bitmap != null) {
                    java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
                    contact.setImage(stream.toByteArray());
                    
                    // Save to local database
                    if (contact.getId() > 0) {
                        dbHandler.updateContact(contact);
                    } else {
                        dbHandler.addContact(contact);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error downloading image", e);
                // Fallback: save contact text data even if image download fails
                if (contact.getId() > 0) {
                    dbHandler.updateContact(contact);
                } else {
                    dbHandler.addContact(contact);
                }
            }
        });
    }

    public interface SyncCallback {
        void onSyncComplete();
    }
}
