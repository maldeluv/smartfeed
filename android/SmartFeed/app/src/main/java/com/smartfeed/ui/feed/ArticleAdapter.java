package com.smartfeed.ui.feed;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.smartfeed.R;
import com.smartfeed.data.model.ArticleDto;
import com.smartfeed.util.DateFormatter;

import java.util.Objects;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class ArticleAdapter extends ListAdapter<ArticleDto, ArticleAdapter.ArticleViewHolder> {

    private static final DiffUtil.ItemCallback<ArticleDto> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ArticleDto>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull ArticleDto oldItem,
                        @NonNull ArticleDto newItem
                ) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull ArticleDto oldItem,
                        @NonNull ArticleDto newItem
                ) {
                    return Objects.equals(oldItem.getTitle(), newItem.getTitle())
                            && Objects.equals(oldItem.getSummary(), newItem.getSummary())
                            && Objects.equals(oldItem.getCategoryName(), newItem.getCategoryName())
                            && Objects.equals(oldItem.getPublishedAt(), newItem.getPublishedAt())
                            && oldItem.isLiked() == newItem.isLiked()
                            && oldItem.isSaved() == newItem.isSaved();
                }
            };

    private final OnArticleActionListener actionListener;
    private Set<Long> pendingLikes = Collections.emptySet();
    private Set<Long> pendingSaves = Collections.emptySet();

    public ArticleAdapter(OnArticleActionListener actionListener) {
        super(DIFF_CALLBACK);
        this.actionListener = Objects.requireNonNull(actionListener, "actionListener");
    }

    public void setPendingActions(Set<Long> likes, Set<Long> saves) {
        Set<Long> updatedLikes = Collections.unmodifiableSet(new HashSet<>(likes));
        Set<Long> updatedSaves = Collections.unmodifiableSet(new HashSet<>(saves));
        Set<Long> changedIds = new HashSet<>(pendingLikes);
        changedIds.addAll(pendingSaves);
        changedIds.addAll(updatedLikes);
        changedIds.addAll(updatedSaves);
        Iterator<Long> changedIterator = changedIds.iterator();
        while (changedIterator.hasNext()) {
            long articleId = changedIterator.next();
            boolean likeUnchanged = pendingLikes.contains(articleId)
                    == updatedLikes.contains(articleId);
            boolean saveUnchanged = pendingSaves.contains(articleId)
                    == updatedSaves.contains(articleId);
            if (likeUnchanged && saveUnchanged) {
                changedIterator.remove();
            }
        }
        pendingLikes = updatedLikes;
        pendingSaves = updatedSaves;
        for (int index = 0; index < getCurrentList().size(); index++) {
            if (changedIds.contains(getCurrentList().get(index).getId())) {
                notifyItemChanged(index);
            }
        }
    }

    @NonNull
    @Override
    public ArticleViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_article, parent, false);
        return new ArticleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ArticleViewHolder holder, int position) {
        ArticleDto article = getItem(position);
        holder.bind(
                article,
                actionListener,
                pendingLikes.contains(article.getId()),
                pendingSaves.contains(article.getId())
        );
    }

    public interface OnArticleActionListener {

        void onArticleClicked(ArticleDto article);

        void onLikeClicked(ArticleDto article);

        void onSaveClicked(ArticleDto article);
    }

    static final class ArticleViewHolder extends RecyclerView.ViewHolder {

        private final TextView categoryView;
        private final TextView titleView;
        private final TextView summaryView;
        private final TextView dateView;
        private final AppCompatImageButton likeButton;
        private final AppCompatImageButton saveButton;

        private ArticleViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryView = itemView.findViewById(R.id.articleCategory);
            titleView = itemView.findViewById(R.id.articleTitle);
            summaryView = itemView.findViewById(R.id.articleSummary);
            dateView = itemView.findViewById(R.id.articleDate);
            likeButton = itemView.findViewById(R.id.articleLikeButton);
            saveButton = itemView.findViewById(R.id.articleSaveButton);
        }

        private void bind(
                ArticleDto article,
                OnArticleActionListener listener,
                boolean likePending,
                boolean savePending
        ) {
            String category = article.getCategoryName();
            categoryView.setText(
                    category == null || category.trim().isEmpty()
                            ? itemView.getContext().getString(R.string.category_unknown)
                            : category
            );
            titleView.setText(article.getTitle());
            summaryView.setText(article.getSummary());
            dateView.setText(DateFormatter.formatArticleDate(article.getPublishedAt()));
            likeButton.setSelected(article.isLiked());
            saveButton.setSelected(article.isSaved());
            likeButton.setEnabled(!likePending);
            saveButton.setEnabled(!savePending);
            likeButton.setContentDescription(itemView.getContext().getString(
                    article.isLiked() ? R.string.action_unlike : R.string.action_like
            ));
            saveButton.setContentDescription(itemView.getContext().getString(
                    article.isSaved() ? R.string.action_unsave : R.string.action_save
            ));
            itemView.setOnClickListener(view -> listener.onArticleClicked(article));
            likeButton.setOnClickListener(view -> listener.onLikeClicked(article));
            saveButton.setOnClickListener(view -> listener.onSaveClicked(article));
        }
    }
}
