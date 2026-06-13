package com.example.myphonebook;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myphonebook.databinding.DialogContactActionsBinding;
import com.example.myphonebook.databinding.ItemContactBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ViewHolder> implements Filterable {

    private final List<Contact> contactListDisplay;
    private final List<Contact> contactListFull;
    private final OnContactClickListener listener;
    private final SelectionListener selectionListener;

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    private boolean isSelectionMode = false;
    private final List<Contact> selectedContacts = new ArrayList<>();

    public interface OnContactClickListener {
        void onEditClick(Contact contact);
        void onDeleteClick(Contact contact);
        void onQrShareClick(Contact contact);
    }

    public interface SelectionListener {
        void onSelectionModeChanged(boolean isSelectionMode);
        void onSelectionCountChanged(int count);
    }

    public ContactsAdapter(List<Contact> initialContacts, OnContactClickListener listener, SelectionListener selectionListener) {
        this.listener = listener;
        this.selectionListener = selectionListener;
        this.contactListDisplay = new ArrayList<>(initialContacts);
        this.contactListFull = new ArrayList<>(initialContacts);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final ItemContactBinding binding;

        public ViewHolder(ItemContactBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemContactBinding binding = ItemContactBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    private boolean isFavoriteView = false;

    public void setFavoriteView(boolean favoriteView) {
        isFavoriteView = favoriteView;
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Contact contact = contactListDisplay.get(position);
        
        // --- Alphabetical Partitioning Logic ---
        if (isFavoriteView) {
            holder.binding.tvHeader.setVisibility(View.GONE);
        } else {
            String currentName = contact.getName();
            String currentHeader = (currentName != null && !currentName.isEmpty())
                    ? currentName.substring(0, 1).toUpperCase()
                    : "#";

            boolean showHeader = false;
            if (position == 0) {
                showHeader = true;
            } else {
                Contact previousContact = contactListDisplay.get(position - 1);
                String previousName = previousContact.getName();
                String previousHeader = (previousName != null && !previousName.isEmpty())
                        ? previousName.substring(0, 1).toUpperCase()
                        : "#";

                if (!currentHeader.equals(previousHeader)) {
                    showHeader = true;
                }
            }

            if (showHeader) {
                holder.binding.tvHeader.setVisibility(View.VISIBLE);
                holder.binding.tvHeader.setText(currentHeader);
            } else {
                holder.binding.tvHeader.setVisibility(View.GONE);
            }
        }
        // ----------------------------------------

        int backgroundColor = holder.itemView.getContext().getColor(android.R.color.transparent);
        int textColorPrimary = holder.itemView.getContext().getColor(android.R.color.tab_indicator_text); // Default
        
        // This is a bit of a hack to get the correct theme colors manually if they aren't applying
        // However, using the ?attr approach in XML is better. 
        // Let's ensure the XML is correct first.

        holder.binding.contactName.setText(contact.getName());
        holder.binding.contactPhone.setText(contact.getPhoneNumber());
        holder.binding.contactEmail.setText(contact.getEmail());

        byte[] imageBytes = contact.getImage();
        if (imageBytes != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            holder.binding.contactImage.setImageBitmap(bitmap);
        } else {
            holder.binding.contactImage.setImageResource(R.drawable.ic_launcher_foreground);
        }

        // --- Selection Mode Logic ---
        if (isSelectionMode) {
            holder.binding.checkboxSelection.setVisibility(View.VISIBLE);
            holder.binding.checkboxSelection.setChecked(selectedContacts.contains(contact));
        } else {
            holder.binding.checkboxSelection.setVisibility(View.GONE);
            holder.binding.checkboxSelection.setChecked(false);
        }

        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(contact);
            } else {
                showDropdownMenu(v, contact);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                isSelectionMode = true;
                toggleSelection(contact);
                selectionListener.onSelectionModeChanged(true);
                notifyDataSetChanged();
            }
            return true;
        });
        // ---------------------------
    }

    private void showDropdownMenu(View v, Contact contact) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(v.getContext());
        DialogContactActionsBinding dialogBinding = DialogContactActionsBinding.inflate(
                LayoutInflater.from(v.getContext()));
        bottomSheetDialog.setContentView(dialogBinding.getRoot());

        dialogBinding.tvActionName.setText(contact.getName());

        dialogBinding.btnCall.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + contact.getPhoneNumber()));
            v.getContext().startActivity(intent);
            bottomSheetDialog.dismiss();
        });

        dialogBinding.btnMessage.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("sms:" + contact.getPhoneNumber()));
            v.getContext().startActivity(intent);
            bottomSheetDialog.dismiss();
        });

        dialogBinding.btnEmail.setOnClickListener(view -> {
            if (contact.getEmail() != null && !contact.getEmail().isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:" + contact.getEmail()));
                v.getContext().startActivity(intent);
            } else {
                android.widget.Toast.makeText(v.getContext(), "No email address", android.widget.Toast.LENGTH_SHORT).show();
            }
            bottomSheetDialog.dismiss();
        });

        dialogBinding.btnShareQr.setOnClickListener(view -> {
            listener.onQrShareClick(contact);
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void toggleSelection(Contact contact) {
        if (selectedContacts.contains(contact)) {
            selectedContacts.remove(contact);
        } else {
            selectedContacts.add(contact);
        }
        notifyDataSetChanged();
        selectionListener.onSelectionCountChanged(selectedContacts.size());
    }

    public void clearSelection() {
        isSelectionMode = false;
        selectedContacts.clear();
        selectionListener.onSelectionModeChanged(false);
        notifyDataSetChanged();
    }

    public List<Contact> getSelectedContacts() {
        return new ArrayList<>(selectedContacts);
    }

    @Override
    public int getItemCount() {
        return contactListDisplay.size();
    }

    public Contact getContactAt(int position) {
        return contactListDisplay.get(position);
    }

    public void updateList(List<Contact> newList) {
        contactListDisplay.clear();
        contactListDisplay.addAll(newList);
        contactListFull.clear();
        contactListFull.addAll(newList);
        notifyDataSetChanged();
    }

    @Override
    public Filter getFilter() {
        return contactFilter;
    }

    private final Filter contactFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Contact> filteredList = new ArrayList<>();
            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(contactListFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                for (Contact item : contactListFull) {
                    if (item.getName().toLowerCase().contains(filterPattern) || item.getPhoneNumber().contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
            }
            FilterResults results = new FilterResults();
            results.values = filteredList;
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            contactListDisplay.clear();
            contactListDisplay.addAll((List<Contact>) results.values);
            notifyDataSetChanged();
        }
    };
}
