package com.example.myphonebook;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myphonebook.databinding.ActivityMainBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import android.util.Base64;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.myphonebook.databinding.DialogQrCodeBinding;
import com.example.myphonebook.databinding.DialogSigninBinding;
import androidx.appcompat.app.AlertDialog;

import com.journeyapps.barcodescanner.ScanOptions;
import com.journeyapps.barcodescanner.ScanContract;
import android.provider.ContactsContract;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private DBHandler dbHandler;
    private ContactsAdapter contactsAdapter;
    private SharedPreferences sharedPreferences;
    private boolean isSortedAscending = true;
    private FirebaseSyncHelper firebaseSyncHelper;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    private static final String PREFS_NAME = "ThemePrefs";
    private static final String KEY_THEME = "isDarkMode";

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        firebaseAuthWithGoogle(account.getIdToken());
                    } catch (ApiException e) {
                        Toast.makeText(this, "Google sign in failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private final ActivityResultLauncher<String> createDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/comma-separated-values"),
            uri -> {
                if (uri != null) {
                    exportContactsToCsv(uri);
                }
            }
    );

    private final ActivityResultLauncher<String[]> openDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    importContactsFromCsv(uri);
                }
            }
    );

    private final ActivityResultLauncher<ScanOptions> qrCodeLauncher = registerForActivityResult(new ScanContract(), result -> {
        if (result.getContents() != null) {
            String vCard = result.getContents().trim();
            if (vCard.toUpperCase().contains("BEGIN:VCARD")) {
                parseAndAddVCard(vCard);
            } else {
                Toast.makeText(this, "Not a valid contact QR code", Toast.LENGTH_SHORT).show();
            }
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (sharedPreferences.getBoolean(KEY_THEME, false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbHandler = new DBHandler(this);
        firebaseSyncHelper = new FirebaseSyncHelper(dbHandler);
        mAuth = FirebaseAuth.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        binding.btnScanQr.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setPrompt("Scan a contact QR code");
            options.setBeepEnabled(true);
            options.setOrientationLocked(true);
            options.setCaptureActivity(CaptureActivityPortrait.class);
            qrCodeLauncher.launch(options);
        });

        // Initialize adapter with an empty list.
        contactsAdapter = new ContactsAdapter(new ArrayList<>(), new ContactsAdapter.OnContactClickListener() {
            @Override
            public void onEditClick(Contact contact) {
                openEditActivity(contact);
            }

            @Override
            public void onDeleteClick(Contact contact) {
                showDeleteConfirmationDialog(contact);
            }

            @Override
            public void onQrShareClick(Contact contact) {
                showQrCodeDialog(contact);
            }
        }, new ContactsAdapter.SelectionListener() {
            @Override
            public void onSelectionModeChanged(boolean isSelectionMode) {
                binding.layoutSelection.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
                binding.floatingActionButton.setVisibility(isSelectionMode ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onSelectionCountChanged(int count) {
                binding.tvSelectionCount.setText(count + " selected");
            }
        });

        RecyclerView recyclerView = binding.recyclerViewContacts;
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(contactsAdapter);

        binding.btnDeleteSelected.setOnClickListener(v -> {
            List<Contact> selected = contactsAdapter.getSelectedContacts();
            if (!selected.isEmpty()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Delete Contacts")
                        .setMessage("Are you sure you want to delete " + selected.size() + " contacts?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            for (Contact contact : selected) {
                                dbHandler.deleteContact(contact);
                                firebaseSyncHelper.deleteContact(contact.getRemoteId());
                            }
                            contactsAdapter.clearSelection();
                            loadAndDisplayContacts();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        binding.btnCancelSelection.setOnClickListener(v -> contactsAdapter.clearSelection());

        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT) {
            private final ColorDrawable editBackground = new ColorDrawable(Color.parseColor("#4CAF50")); // Green
            private final ColorDrawable deleteBackground = new ColorDrawable(Color.parseColor("#F44336")); // Red
            private final Drawable editIcon = ContextCompat.getDrawable(MainActivity.this, android.R.drawable.ic_menu_edit);
            private final Drawable deleteIcon = ContextCompat.getDrawable(MainActivity.this, android.R.drawable.ic_menu_delete);

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                Contact contact = contactsAdapter.getContactAt(position);

                if (direction == ItemTouchHelper.RIGHT) {
                    openEditActivity(contact);
                    contactsAdapter.notifyItemChanged(position);
                } else if (direction == ItemTouchHelper.LEFT) {
                    showDeleteConfirmationDialog(contact, position);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                View itemView = viewHolder.itemView;
                ContactsAdapter.ViewHolder vh = (ContactsAdapter.ViewHolder) viewHolder;
                View contactContent = vh.binding.contactContent;

                int top = itemView.getTop() + vh.binding.tvHeader.getHeight();
                if (vh.binding.tvHeader.getVisibility() == View.GONE) {
                    top = itemView.getTop();
                }
                int bottom = itemView.getBottom();

                if (dX > 0) { // Right Swipe (Edit)
                    editBackground.setBounds(itemView.getLeft(), top, itemView.getLeft() + ((int) dX), bottom);
                    editBackground.draw(c);

                    if (editIcon != null) {
                        int contentHeight = bottom - top;
                        int iconMargin = (contentHeight - editIcon.getIntrinsicHeight()) / 2;
                        int iconTop = top + iconMargin;
                        int iconBottom = iconTop + editIcon.getIntrinsicHeight();
                        int iconLeft = itemView.getLeft() + iconMargin;
                        int iconRight = iconLeft + editIcon.getIntrinsicWidth();
                        editIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                        editIcon.draw(c);
                    }
                } else if (dX < 0) { // Left Swipe (Delete)
                    deleteBackground.setBounds(itemView.getRight() + ((int) dX), top, itemView.getRight(), bottom);
                    deleteBackground.draw(c);

                    if (deleteIcon != null) {
                        int contentHeight = bottom - top;
                        int iconMargin = (contentHeight - deleteIcon.getIntrinsicHeight()) / 2;
                        int iconTop = top + iconMargin;
                        int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();
                        int iconRight = itemView.getRight() - iconMargin;
                        int iconLeft = iconRight - deleteIcon.getIntrinsicWidth();
                        deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                        deleteIcon.draw(c);
                    }
                }
                contactContent.setTranslationX(dX);
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                View contactContent = ((ContactsAdapter.ViewHolder) viewHolder).binding.contactContent;
                contactContent.setTranslationX(0f);
            }
        };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView);

        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                contactsAdapter.getFilter().filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                contactsAdapter.getFilter().filter(newText);
                return false;
            }
        });

        binding.floatingActionButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, EditContactActivity.class);
            startActivity(intent);
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!binding.searchView.isIconified()) {
                    binding.searchView.setIconified(true);
                } else if (contactsAdapter.isSelectionMode()) {
                    contactsAdapter.clearSelection();
                } else if (binding.layoutSettings.getRoot().getVisibility() == View.VISIBLE) {
                    binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
                } else {
                    finish();
                }
            }
        });

        binding.buttonSort.setOnClickListener(v -> {
            isSortedAscending = !isSortedAscending;
            loadAndDisplayContacts();
        });

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            updateUIForTab(item.getItemId());
            return true;
        });

        int selectedTab = R.id.nav_home;
        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt("selected_tab", R.id.nav_home);
        }
        binding.bottomNavigation.setSelectedItemId(selectedTab);
        updateUIForTab(selectedTab);

        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            pullContactsFromCloud();
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        pullContactsFromCloud();
                        updateAuthUI();
                        Toast.makeText(this, "Signed in with Google", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateUIForTab(int itemId) {
        if (itemId == R.id.nav_home) {
            binding.recyclerViewContacts.setVisibility(View.VISIBLE);
            binding.layoutSettings.getRoot().setVisibility(View.GONE);
            binding.floatingActionButton.setVisibility(View.VISIBLE);
            binding.linearLayout.setVisibility(View.VISIBLE);
            contactsAdapter.setFavoriteView(false);
            loadAndDisplayContacts();
        } else if (itemId == R.id.nav_favorites) {
            binding.recyclerViewContacts.setVisibility(View.VISIBLE);
            binding.layoutSettings.getRoot().setVisibility(View.GONE);
            binding.floatingActionButton.setVisibility(View.VISIBLE);
            binding.linearLayout.setVisibility(View.VISIBLE);
            contactsAdapter.setFavoriteView(true);
            loadAndDisplayFavorites();
        } else if (itemId == R.id.nav_settings) {
            binding.recyclerViewContacts.setVisibility(View.GONE);
            binding.layoutSettings.getRoot().setVisibility(View.VISIBLE);
            binding.floatingActionButton.setVisibility(View.GONE);
            binding.linearLayout.setVisibility(View.GONE);
            setupSettingsLogic();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("selected_tab", binding.bottomNavigation.getSelectedItemId());
    }

    private void setupSettingsLogic() {
        updateAuthUI();

        binding.layoutSettings.btnAuth.setOnClickListener(v -> {
            if (mAuth.getCurrentUser() != null) {
                mAuth.signOut();
                mGoogleSignInClient.signOut();
                updateAuthUI();
                Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();
            } else {
                if (!RateLimiter.canAttemptAuth(this)) {
                    Toast.makeText(this, "Too many login attempts. Try again in 15 minutes.", Toast.LENGTH_LONG).show();
                    return;
                }
                showSignInOptions();
            }
        });

        binding.layoutSettings.switchDarkMode.setOnCheckedChangeListener(null);
        binding.layoutSettings.switchDarkMode.setChecked(sharedPreferences.getBoolean(KEY_THEME, false));
        binding.layoutSettings.switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(KEY_THEME, isChecked).apply();
            recreate();
        });

        binding.layoutSettings.btnReset.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Reset App")
                    .setMessage("Are you sure you want to delete ALL contacts? This cannot be undone.")
                    .setPositiveButton("Reset", (dialog, which) -> {
                        new Thread(() -> {
                            List<Contact> all = dbHandler.getAllContacts();
                            for (Contact c : all) {
                                dbHandler.deleteContact(c);
                                firebaseSyncHelper.deleteContact(c.getRemoteId());
                            }
                            runOnUiThread(() -> {
                                Toast.makeText(this, "All contacts deleted", Toast.LENGTH_SHORT).show();
                                loadAndDisplayContacts();
                            });
                        }).start();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        binding.layoutSettings.btnBackup.setOnClickListener(v -> createDocumentLauncher.launch("contacts_backup.csv"));
        binding.layoutSettings.btnRestore.setOnClickListener(v -> openDocumentLauncher.launch(new String[]{"text/comma-separated-values", "text/plain"}));

        binding.layoutSettings.btnGithub.setOnClickListener(v -> openUrl("https://github.com/Nikhil-1501"));
        binding.layoutSettings.btnInstagram.setOnClickListener(v -> openUrl("https://www.instagram.com/nikhil_yadav1501?igsh=MWhwaG92d3NoemY0Ng=="));
        binding.layoutSettings.btnPrivacyPolicy.setOnClickListener(v -> {
            Intent intent = new Intent(this, PrivacyPolicyActivity.class);
            startActivity(intent);
        });
    }

    private void showSignInOptions() {
        String[] options = {"Google Sign In", "Email Sign In"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose Sign In Method")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                        googleSignInLauncher.launch(signInIntent);
                    } else {
                        showSignInDialog();
                    }
                })
                .show();
    }

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
                .setNeutralButton("Share File (Original Quality)", (dialog, which) -> shareContactAsVcf(contact))
                .show();
    }

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

    private void updateAuthUI() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            binding.layoutSettings.tvUserEmail.setText(user.getEmail());
            binding.layoutSettings.btnAuth.setText("Sign Out");
        } else {
            binding.layoutSettings.tvUserEmail.setText("Not signed in");
            binding.layoutSettings.btnAuth.setText("Sign In");
        }
    }

    private void showSignInDialog() {
        DialogSigninBinding signinBinding = DialogSigninBinding.inflate(getLayoutInflater());

        new MaterialAlertDialogBuilder(this)
                .setTitle("Sign In / Register")
                .setView(signinBinding.getRoot())
                .setPositiveButton("Sign In", (dialog, which) -> {
                    String email = signinBinding.etEmail.getText().toString();
                    String pass = signinBinding.etPassword.getText().toString();
                    if (!email.isEmpty() && !pass.isEmpty()) {
                        mAuth.signInWithEmailAndPassword(email, pass)
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        pullContactsFromCloud();
                                        updateAuthUI();
                                        Toast.makeText(this, "Welcome!", Toast.LENGTH_SHORT).show();
                                    } else {
                                        registerUser(email, pass);
                                    }
                                });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pullContactsFromCloud() {
        if (RateLimiter.shouldThrottle()) {
            binding.swipeRefreshLayout.setRefreshing(false);
            Toast.makeText(this, "Please wait a moment before refreshing again.", Toast.LENGTH_SHORT).show();
            return;
        }
        binding.swipeRefreshLayout.setRefreshing(true);
        firebaseSyncHelper.pullContactsFromServer(() -> {
            runOnUiThread(() -> {
                loadAndDisplayContacts();
                binding.swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(MainActivity.this, "Cloud contacts synced", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void registerUser(String email, String pass) {
        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        updateAuthUI();
                        Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Auth failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void exportContactsToCsv(Uri uri) {
        try {
            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            if (outputStream == null) return;

            StringBuilder csvData = new StringBuilder();
            csvData.append("Name,Phone,Email,Address,Image,IsFavorite\n");

            List<Contact> contacts = dbHandler.getAllContacts();
            for (Contact contact : contacts) {
                String imageBase64 = "";
                if (contact.getImage() != null) {
                    imageBase64 = Base64.encodeToString(contact.getImage(), Base64.NO_WRAP);
                }

                csvData.append(escapeCsv(contact.getName())).append(",")
                        .append(escapeCsv(contact.getPhoneNumber())).append(",")
                        .append(escapeCsv(contact.getEmail())).append(",")
                        .append(escapeCsv(contact.getAddress())).append(",")
                        .append(escapeCsv(imageBase64)).append(",")
                        .append(contact.isFavorite()).append("\n");
            }

            outputStream.write(csvData.toString().getBytes());
            outputStream.close();
            Toast.makeText(this, "Contacts exported successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void parseAndAddVCard(String vCard) {
        String name = "";
        String phone = "";
        String email = "";
        String address = "";
        byte[] image = null;

        String[] lines = vCard.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("FN:")) name = line.substring(3).trim();
            else if (line.startsWith("TEL")) {
                int colonIndex = line.indexOf(":");
                if (colonIndex != -1) phone = line.substring(colonIndex + 1).trim();
            } else if (line.startsWith("EMAIL:")) email = line.substring(6).trim();
            else if (line.startsWith("ADR")) {
                int colonIndex = line.indexOf(":");
                if (colonIndex != -1) {
                    String addrPart = line.substring(colonIndex + 1).trim();
                    address = addrPart.replace(";", " ").trim();
                }
            } else if (line.startsWith("PHOTO;")) {
                int colonIndex = line.indexOf(":");
                if (colonIndex != -1) {
                    String photoData = line.substring(colonIndex + 1).trim();
                    try {
                        image = Base64.decode(photoData, Base64.DEFAULT);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (!name.isEmpty() && !phone.isEmpty()) {
            Contact contact = new Contact(name, phone, email, address, image, false);
            if (dbHandler.addContact(contact)) {
                firebaseSyncHelper.syncContact(contact);
                loadAndDisplayContacts();
                Toast.makeText(this, "Contact added: " + name, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Contact already exists", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void importContactsFromCsv(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return;

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            int count = 0;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header
                }

                String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                if (parts.length >= 2) {
                    String name = unescapeCsv(parts[0]);
                    String phone = unescapeCsv(parts[1]);
                    String email = parts.length > 2 ? unescapeCsv(parts[2]) : "";
                    String address = parts.length > 3 ? unescapeCsv(parts[3]) : "";

                    byte[] image = null;
                    if (parts.length > 4) {
                        String imageBase64 = unescapeCsv(parts[4]);
                        if (!imageBase64.isEmpty()) {
                            image = Base64.decode(imageBase64, Base64.DEFAULT);
                        }
                    }

                    boolean isFavorite = false;
                    if (parts.length > 5) {
                        isFavorite = Boolean.parseBoolean(parts[5]);
                    }

                    Contact contact = new Contact(name, phone, email, address, image, isFavorite);
                    dbHandler.addContact(contact);
                    count++;
                }
            }

            inputStream.close();
            loadAndDisplayContacts();
            Toast.makeText(this, "Imported " + count + " contacts", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String unescapeCsv(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
            return value.replace("\"\"", "\"");
        }
        return value;
    }

    private void loadAndDisplayContacts() {
        List<Contact> currentContacts = dbHandler.getAllContacts();
        if (isSortedAscending) {
            Collections.sort(currentContacts, Comparator.comparing(Contact::getName, String.CASE_INSENSITIVE_ORDER));
        } else {
            Collections.sort(currentContacts, Comparator.comparing(Contact::getName, String.CASE_INSENSITIVE_ORDER).reversed());
        }
        contactsAdapter.updateList(currentContacts);
        updateEmptyState(currentContacts.isEmpty());
    }

    private void loadAndDisplayFavorites() {
        List<Contact> all = dbHandler.getAllContacts();
        List<Contact> favorites = new ArrayList<>();
        for (Contact c : all) {
            if (c.isFavorite()) favorites.add(c);
        }

        if (isSortedAscending) {
            Collections.sort(favorites, Comparator.comparing(Contact::getName, String.CASE_INSENSITIVE_ORDER));
        } else {
            Collections.sort(favorites, Comparator.comparing(Contact::getName, String.CASE_INSENSITIVE_ORDER).reversed());
        }
        contactsAdapter.updateList(favorites);
        updateEmptyState(favorites.isEmpty());
    }

    private void updateEmptyState(boolean isEmpty) {
        if (isEmpty) {
            binding.recyclerViewContacts.setVisibility(View.GONE);
            binding.layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            binding.recyclerViewContacts.setVisibility(View.VISIBLE);
            binding.layoutEmpty.setVisibility(View.GONE);
        }
    }

    private void openEditActivity(Contact contact) {
        Intent intent = new Intent(this, EditContactActivity.class);
        intent.putExtra("contact_id", contact.getId());
        startActivity(intent);
    }

    private void showDeleteConfirmationDialog(Contact contact) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Contact")
                .setMessage("Are you sure you want to delete this contact?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbHandler.deleteContact(contact);
                    firebaseSyncHelper.deleteContact(contact.getRemoteId());
                    loadAndDisplayContacts();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirmationDialog(Contact contact, int position) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Contact")
                .setMessage("Are you sure you want to delete " + contact.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbHandler.deleteContact(contact);
                    firebaseSyncHelper.deleteContact(contact.getRemoteId());
                    loadAndDisplayContacts();
                })
                .setNegativeButton("Cancel", (dialog, which) -> contactsAdapter.notifyItemChanged(position))
                .setOnCancelListener(dialog -> contactsAdapter.notifyItemChanged(position))
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding.bottomNavigation.getSelectedItemId() == R.id.nav_favorites) {
            loadAndDisplayFavorites();
        } else if (binding.bottomNavigation.getSelectedItemId() == R.id.nav_home) {
            loadAndDisplayContacts();
        }
    }
}
