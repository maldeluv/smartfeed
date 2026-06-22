package com.smartfeed.ui.categories;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.smartfeed.R;
import com.smartfeed.data.model.CategoryDto;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

public final class CategoryAdapter
        extends ListAdapter<CategoryDto, CategoryAdapter.CategoryViewHolder> {

    private static final DiffUtil.ItemCallback<CategoryDto> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<CategoryDto>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull CategoryDto oldItem,
                        @NonNull CategoryDto newItem
                ) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull CategoryDto oldItem,
                        @NonNull CategoryDto newItem
                ) {
                    return Objects.equals(oldItem.getName(), newItem.getName())
                            && Objects.equals(oldItem.getDescription(), newItem.getDescription())
                            && oldItem.isSubscribed() == newItem.isSubscribed();
                }
            };

    private final OnSubscriptionChanged listener;
    private Set<Long> pendingIds = Collections.emptySet();

    public CategoryAdapter(OnSubscriptionChanged listener) {
        super(DIFF_CALLBACK);
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public void setPendingIds(Set<Long> ids) {
        Set<Long> updated = Collections.unmodifiableSet(new HashSet<>(ids));
        if (!pendingIds.equals(updated)) {
            Set<Long> changedIds = new HashSet<>(pendingIds);
            changedIds.addAll(updated);
            Iterator<Long> changedIterator = changedIds.iterator();
            while (changedIterator.hasNext()) {
                long categoryId = changedIterator.next();
                if (pendingIds.contains(categoryId) == updated.contains(categoryId)) {
                    changedIterator.remove();
                }
            }
            pendingIds = updated;
            for (int index = 0; index < getCurrentList().size(); index++) {
                if (changedIds.contains(getCurrentList().get(index).getId())) {
                    notifyItemChanged(index);
                }
            }
        }
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CategoryDto category = getItem(position);
        holder.bind(category, pendingIds.contains(category.getId()), listener);
    }

    public interface OnSubscriptionChanged {

        void onChanged(CategoryDto category);
    }

    static final class CategoryViewHolder extends RecyclerView.ViewHolder {

        private final TextView nameView;
        private final TextView descriptionView;
        private final SwitchMaterial subscriptionSwitch;

        private CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.categoryName);
            descriptionView = itemView.findViewById(R.id.categoryDescription);
            subscriptionSwitch = itemView.findViewById(R.id.categorySubscriptionSwitch);
        }

        private void bind(
                CategoryDto category,
                boolean pending,
                OnSubscriptionChanged listener
        ) {
            nameView.setText(category.getName());
            String description = category.getDescription();
            boolean hasDescription = description != null && !description.trim().isEmpty();
            descriptionView.setVisibility(hasDescription ? View.VISIBLE : View.GONE);
            descriptionView.setText(hasDescription ? description : "");

            subscriptionSwitch.setOnCheckedChangeListener(null);
            subscriptionSwitch.setChecked(category.isSubscribed());
            subscriptionSwitch.setEnabled(!pending);
            subscriptionSwitch.setText(
                    category.isSubscribed()
                            ? R.string.category_subscribed
                            : R.string.category_subscribe
            );
            subscriptionSwitch.setOnCheckedChangeListener(
                    (button, checked) -> listener.onChanged(category)
            );
        }
    }
}
