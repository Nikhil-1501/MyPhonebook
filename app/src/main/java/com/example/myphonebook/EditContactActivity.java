package com.example.myphonebook;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.example.myphonebook.databinding.DialogQrCodeBinding;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import android.util.Base64;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.core.content.FileProvider;

import com.example.myphonebook.databinding.ActivityEditContactBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Activity for creating and editing contacts.
 * Handles image selection (camera/gallery), input validation, QR code generation, 
 * and VCF sharing.
 */
public class EditContactActivity extends AppCompatActivity {

    private ActivityEditContactBinding binding;
    private DBHandler dbHandler;
    private Contact contact;
    private boolean isEditMode = false;
    private static final int REQUEST_PERMISSION = 100;
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private boolean isFavorite = false;
    private byte[] contactImage = null;
    private FirebaseSyncHelper firebaseSyncHelper;
    private Uri photoUri;

    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityEditContactBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbHandler = new DBHandler(this);
        firebaseSyncHelper = new FirebaseSyncHelper(dbHandler);

        setupActivityResultLaunchers();

        // Check if we are editing an existing contact
        Intent intent = getIntent();
        if (intent.hasExtra("contact_id")) {
            isEditMode = true;
            binding.tvTitle.setText("Edit Contact");
            binding.btnSave.setText("Update Contact");
            int contactId = intent.getIntExtra("contact_id", -1);
            contact = dbHandler.getContact(contactId);
            if (contact != null) {
                populateContactDetails();
            }
        } else {
            binding.tvTitle.setText("Add Contact");
            binding.btnSave.setText("Save Contact");
        }

        binding.btnSave.setOnClickListener(v -> {
            if (validateInput()) {
                if (isEditMode) {
                    updateContact();
                } else {
                    saveNewContact();
                }
            }
        });

        binding.ivContactImage.setOnClickListener(v -> showImageSelectionDialog());
        binding.btnCaptureImage.setVisibility(View.GONE);

        binding.btnFavorite.setOnClickListener(v -> {
            isFavorite = !isFavorite;
            updateFavoriteIcon();
        });

        binding.btnMoreOptions.setOnClickListener(this::showMoreOptions);
    }

    /**
     * Displays a popup menu with Delete and Share options.
     */
    private void showMoreOptions(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add("Delete");
        if (isEditMode) {
            popup.getMenu().add("Share QR");
            popup.getMenu().add("Share VCF");
        }
        
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("Delete")) {
                confirmDelete();
                return true;
            } else if (title.equals("Share QR")) {
                showQrCodeDialog(contact);
                return true;
            } else if (title.equals("Share VCF")) {
                shareContactAsVcf(contact);
                return true;
            }
            return false;
        });
        popup.show();
    }

    /**
     * Generates a vCard QR code for the contact and displays it in a dialog.
     */
    private void showQrCodeDialog(Contact contact) {
        DialogQrCodeBinding qrBinding = DialogQrCodeBinding.inflate(LayoutInflater.from(this));
        qrBinding.tvQrContactName.setText(contact.getName());

        StringBuilder vCard = new StringBuilder();
        vCard.append("BEGIN:VCARD\r\n");
        vCard.append("VERSION:3.0\r\n");
        vCard.append("FN:").append(contact.getName()).append("\r\n");
        vCard.append("TEL;TYPE=CELL:").append(contact.getPhoneNumber()).append("\r\n");
        vCard.append("EMAIL:").append(contact.getEmail()).append("\r\n");
        vCard.append("ADR;TYPE=HOME:;;").append(contact.getAddress().replace(";", "\\;")).append(";;;;\r\n");

        if (contact.getImage() != null) {
            Bitmap originalBitmap = BitmapFactory.decodeByteArray(contact.getImage(), 0, contact.getImage().length);
            if (originalBitmap != null) {
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 50, 50, true);
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream);
                byte[] compressedImage = stream.toByteArray();
                String imageBase64 = Base64.encodeToString(compressedImage, Base64.NO_WRAP);
                vCard.append("PHOTO;TYPE=JPEG;ENCODING=b:").append(imageBase64).append("\r\n");
            }
        }
        vCard.append("END:VCARD\r\n");

        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(vCard.toString(), BarcodeFormat.QR_CODE, 512, 512);
            qrBinding.ivQrCode.setImageBitmap(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error generating QR code", Toast.LENGTH_SHORT).show();
        }

        new MaterialAlertDialogBuilder(this)
                .setView(qrBinding.getRoot())
                .setPositiveButton("Close", null)
                .setNeutralButton("Share File", (dialog, which) -> shareContactAsVcf(contact))
                .show();
    }

    /**
     * Exports the contact as a .vcf file and triggers a system share intent.
     */
    private void shareContactAsVcf(Contact contact) {
        StringBuilder vCard = new StringBuilder();
        vCard.append("BEGIN:VCARD\nVERSION:3.0\n");
        vCard.append("FN:").append(contact.getName()).append("\n");
        vCard.append("TEL;TYPE=CELL:").append(contact.getPhoneNumber()).append("\n");
        vCard.append("EMAIL:").append(contact.getEmail()).append("\n");
        vCard.append("ADR;TYPE=HOME:;;").append(contact.getAddress()).append(";;;;\n");

        if (contact.getImage() != null) {
            String imageBase64 = Base64.encodeToString(contact.getImage(), Base64.NO_WRAP);
            vCard.append("PHOTO;TYPE=JPEG;ENCODING=b:").append(imageBase64).append("\n");
        }
        vCard.append("END:VCARD");

        try {
            java.io.File file = new java.io.File(getExternalCacheDir(), contact.getName() + ".vcf");
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(vCard.toString());
            writer.close();

            Uri contentUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/x-vcard");
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Contact via"));
        } catch (Exception e) {
            Toast.makeText(this, "Error sharing contact", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete() {
        if (!isEditMode) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Contact")
                .setMessage("Are you sure you want to delete this contact?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbHandler.deleteContact(contact);
                    firebaseSyncHelper.deleteContact(contact.getRemoteId());
                    Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupActivityResultLaunchers() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            Bitmap selectedImage = decodeSampledBitmapFromUri(selectedImageUri, 200, 200);
                            updateImage(selectedImage);
                        }
                    }
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Bitmap photo = null;
                        if (photoUri != null) {
                            photo = decodeSampledBitmapFromUri(photoUri, 500, 500);
                        } else if (result.getData() != null && result.getData().getExtras() != null) {
                            photo = (Bitmap) result.getData().getExtras().get("data");
                        }
                        updateImage(photo);
                    }
                }
        );
    }

    private void updateFavoriteIcon() {
        if (isFavorite) {
            binding.btnFavorite.setImageResource(R.drawable.ic_star_filled);
        } else {
            binding.btnFavorite.setImageResource(R.drawable.ic_star_outline);
        }
    }

    private void showImageSelectionDialog() {
        String[] options = {"Gallery", "Camera"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Image Source")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        checkGalleryPermission();
                    } else {
                        checkCameraPermission();
                    }
                })
                .show();
    }

    private void checkGalleryPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_PERMISSION);
            } else {
                chooseImageFromGallery();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
            } else {
                chooseImageFromGallery();
            }
        }
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            takePhotoWithCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                chooseImageFromGallery();
            } else {
                Toast.makeText(this, "Permission denied to read your storage", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takePhotoWithCamera();
            } else {
                Toast.makeText(this, "Permission denied to use camera", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void populateContactDetails() {
        binding.etName.setText(contact.getName());
        binding.etPhone.setText(contact.getPhoneNumber());
        binding.etEmail.setText(contact.getEmail());
        binding.etAddress.setText(contact.getAddress());
        isFavorite = contact.isFavorite();
        updateFavoriteIcon();

        if (contact.getImage() != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(contact.getImage(), 0, contact.getImage().length);
            binding.ivContactImage.setImageBitmap(bitmap);
            contactImage = contact.getImage();
        }
    }

    /**
     * Performs sanitization and validation on user inputs.
     * Checks for length constraints, regex patterns, and image size limits.
     */
    private boolean validateInput() {
        String name = binding.etName.getText().toString().trim();
        String phone = binding.etPhone.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String address = binding.etAddress.getText().toString().trim();

        if (name.isEmpty()) {
            binding.tilName.setError("Name is required");
            return false;
        } else if (name.length() > 100) {
            binding.tilName.setError("Name is too long (max 100 chars)");
            return false;
        } else {
            binding.tilName.setError(null);
        }
        
        if (phone.isEmpty()) {
            binding.tilPhone.setError("Phone number is required");
            return false;
        } else if (!phone.matches("^[+]?[0-9]{8,15}$")) {
            binding.tilPhone.setError("Invalid phone number format");
            return false;
        } else {
            binding.tilPhone.setError(null);
        }

        if (!email.isEmpty()) {
            if (email.length() > 100) {
                binding.tilEmail.setError("Email is too long");
                return false;
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.setError("Invalid email address");
                return false;
            } else {
                binding.tilEmail.setError(null);
            }
        }

        if (address.length() > 500) {
            binding.tilAddress.setError("Address is too long (max 500 chars)");
            return false;
        } else {
            binding.tilAddress.setError(null);
        }

        // Limit image size to 5MB to prevent memory issues and cloud storage bloat
        if (contactImage != null && contactImage.length > 5 * 1024 * 1024) {
            Toast.makeText(this, "Image size too large (max 5MB)", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private void saveNewContact() {
        String name = binding.etName.getText().toString().trim();
        String phone = binding.etPhone.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String address = binding.etAddress.getText().toString().trim();

        Contact newContact = new Contact(name, phone, email, address, contactImage, isFavorite);
        if (dbHandler.addContact(newContact)) {
            firebaseSyncHelper.syncContact(newContact);
            Toast.makeText(this, "Contact saved successfully", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Phone number already exists", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateContact() {
        contact.setName(binding.etName.getText().toString().trim());
        contact.setPhoneNumber(binding.etPhone.getText().toString().trim());
        contact.setEmail(binding.etEmail.getText().toString().trim());
        contact.setAddress(binding.etAddress.getText().toString().trim());
        contact.setImage(contactImage);
        contact.setFavorite(isFavorite);

        if (dbHandler.updateContact(contact)) {
            firebaseSyncHelper.syncContact(contact);
            Toast.makeText(this, "Contact updated successfully", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Phone number already exists", Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        galleryLauncher.launch(intent);
    }

    private void takePhotoWithCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "Error creating file", Toast.LENGTH_SHORT).show();
            }

            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                cameraLauncher.launch(intent);
            }
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalCacheDir();
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void updateImage(Bitmap bitmap) {
        if (bitmap != null) {
            binding.ivContactImage.setImageBitmap(bitmap);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            contactImage = stream.toByteArray();
        } else {
            Toast.makeText(this, "Failed to get image", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Decodes a bitmap from a URI with downsampling to avoid OutOfMemory errors.
     */
    public Bitmap decodeSampledBitmapFromUri(Uri uri, int reqWidth, int reqHeight) {
        try {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, options);
        } catch (Exception e) {
            android.util.Log.e("EditContactActivity", "Error decoding bitmap from URI", e);
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
