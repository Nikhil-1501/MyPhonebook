package com.example.myphonebook;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * Local SQLite database handler for managing contacts.
 * Handles database creation, versioning, and standard CRUD operations.
 */
public class DBHandler extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "contactsDB";
    private static final int DATABASE_VERSION = 5;

    private static final String TABLE_CONTACTS = "contacts";

    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_PHONE = "phone_number";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_IMAGE = "image";
    private static final String KEY_FAVORITE = "is_favorite";
    private static final String KEY_REMOTE_ID = "remote_id";

    public DBHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_CONTACTS_TABLE = "CREATE TABLE " + TABLE_CONTACTS + "("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_NAME + " TEXT,"
                + KEY_PHONE + " TEXT UNIQUE,"
                + KEY_EMAIL + " TEXT,"
                + KEY_ADDRESS + " TEXT,"
                + KEY_IMAGE + " BLOB,"
                + KEY_FAVORITE + " INTEGER DEFAULT 0,"
                + KEY_REMOTE_ID + " TEXT" + ")";
        db.execSQL(CREATE_CONTACTS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Simple upgrade policy: drop and recreate
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONTACTS);
        onCreate(db);
    }

    /**
     * Adds a new contact to the local database.
     * @param contact The contact object to be added.
     * @return true if added successfully, false if the phone number already exists (unique constraint).
     */
    public boolean addContact(Contact contact) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_NAME, contact.getName());
        values.put(KEY_PHONE, contact.getPhoneNumber());
        values.put(KEY_EMAIL, contact.getEmail());
        values.put(KEY_ADDRESS, contact.getAddress());
        values.put(KEY_IMAGE, contact.getImage());
        values.put(KEY_FAVORITE, contact.isFavorite() ? 1 : 0);
        values.put(KEY_REMOTE_ID, contact.getRemoteId());

        long result = db.insert(TABLE_CONTACTS, null, values);
        db.close();
        return result != -1;
    }

    /**
     * Retrieves all contacts from the database.
     * @return A list of all Contact objects.
     */
    public List<Contact> getAllContacts() {
        List<Contact> contactList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_CONTACTS;
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Contact contact = new Contact();
                contact.setId(cursor.getInt(0));
                contact.setName(cursor.getString(1));
                contact.setPhoneNumber(cursor.getString(2));
                contact.setEmail(cursor.getString(3));
                contact.setAddress(cursor.getString(4));
                contact.setImage(cursor.getBlob(5));
                contact.setFavorite(cursor.getInt(6) == 1);
                contact.setRemoteId(cursor.getString(7));
                contactList.add(contact);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return contactList;
    }

    /**
     * Retrieves contacts marked as favorites.
     * @return A list of favorite Contact objects.
     */
    public List<Contact> getFavoriteContacts() {
        List<Contact> contactList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_CONTACTS, null, KEY_FAVORITE + "=?",
                new String[]{"1"}, null, null, null);

        if (cursor.moveToFirst()) {
            do {
                Contact contact = new Contact();
                contact.setId(cursor.getInt(0));
                contact.setName(cursor.getString(1));
                contact.setPhoneNumber(cursor.getString(2));
                contact.setEmail(cursor.getString(3));
                contact.setAddress(cursor.getString(4));
                contact.setImage(cursor.getBlob(5));
                contact.setFavorite(true);
                contact.setRemoteId(cursor.getString(7));
                contactList.add(contact);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return contactList;
    }

    /**
     * Updates an existing contact's details.
     * @param contact The contact with updated information.
     * @return true if updated successfully.
     */
    public boolean updateContact(Contact contact) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_NAME, contact.getName());
        values.put(KEY_PHONE, contact.getPhoneNumber());
        values.put(KEY_EMAIL, contact.getEmail());
        values.put(KEY_ADDRESS, contact.getAddress());
        values.put(KEY_IMAGE, contact.getImage());
        values.put(KEY_FAVORITE, contact.isFavorite() ? 1 : 0);
        values.put(KEY_REMOTE_ID, contact.getRemoteId());

        int result = db.update(TABLE_CONTACTS, values, KEY_ID + " = ?",
                new String[]{String.valueOf(contact.getId())});
        db.close();
        return result > 0;
    }

    /**
     * Deletes a contact from the local database.
     * @param contact The contact to be deleted.
     */
    public void deleteContact(Contact contact) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_CONTACTS, KEY_ID + " = ?",
                new String[]{String.valueOf(contact.getId())});
        db.close();
    }

    /**
     * Retrieves a single contact by its local ID.
     * @param id The local SQLite ID of the contact.
     * @return The Contact object, or null if not found.
     */
    public Contact getContact(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_CONTACTS, null, KEY_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            Contact contact = new Contact();
            contact.setId(cursor.getInt(0));
            contact.setName(cursor.getString(1));
            contact.setPhoneNumber(cursor.getString(2));
            contact.setEmail(cursor.getString(3));
            contact.setAddress(cursor.getString(4));
            contact.setImage(cursor.getBlob(5));
            contact.setFavorite(cursor.getInt(6) == 1);
            contact.setRemoteId(cursor.getString(7));
            cursor.close();
            db.close();
            return contact;
        }
        if (cursor != null) cursor.close();
        db.close();
        return null;
    }
}
