package com.smartfeed.ui.recommendations;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.smartfeed.R;
import com.smartfeed.data.model.ArticleDto;
import com.smartfeed.data.model.RecommendationDto;
import com.smartfeed.util.DateFormatter;

import java.util.Locale;
import java.util.Objects;

public final class RecommendationAdapter extends ListAdapter<
        RecommendationDto,
        RecommendationAdapter.RecommendationViewHolder
        > {

    private static final DiffUtil.ItemCallback<RecommendationDto> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<RecommendationDto>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull RecommendationDto oldItem,
                        @NonNull RecommendationDto newItem
                ) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull RecommendationDto oldItem,
                        @NonNull RecommendationDto newItem
                ) {
                    ArticleDto oldArticle = oldItem.getArticle();
                    ArticleDto newArticle = newItem.getArticle();
                    return oldItem.getScore() == newItem.getScore()
                            && Objects.equals(oldItem.getReason(), newItem.getReason())
                            && articleContentEquals(oldArticle, newArticle);
                }
            };

    private final OnRecommendationClickListener clickListener;

    public RecommendationAdapter(OnRecommendationClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = Objects.requireNonNull(clickListener, "clickListener");
    }

    @NonNull
    @Override
    public RecommendationViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recommendation, parent, false);
        return new RecommendationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecommendationViewHolder holder, int position) {
        holder.bind(getItem(position), clickListener);
    }

    private static boolean articleContentEquals(ArticleDto oldArticle, ArticleDto newArticle) {
        if (oldArticle == newArticle) {
            return true;
        }
        if (oldArticle == null || newArticle == null) {
            return false;
        }
        return oldArticle.getId() == newArticle.getId()
                && Objects.equals(oldArticle.getTitle(), newArticle.getTitle())
                && Objects.equals(oldArticle.getSummary(), newArticle.getSummary())
                && Objects.equals(oldArticle.getCategoryName(), newArticle.getCategoryName())
                && Objects.equals(oldArticle.getPublishedAt(), newArticle.getPublishedAt());
    }

    public interface OnRecommendationClickListener {

        void onRecommendationClicked(RecommendationDto recommendation);
    }

    static final class RecommendationViewHolder extends RecyclerView.ViewHolder {

        private final TextView categoryView;
        private final TextView titleView;
        private final TextView summaryView;
        private final TextView dateView;
        private final TextView reasonView;
        private final TextView scoreView;

        private RecommendationViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryView = itemView.findViewById(R.id.recommendationCategory);
            titleView = itemView.findViewById(R.id.recommendationTitle);
            summaryView = itemView.findViewById(R.id.recommendationSummary);
            dateView = itemView.findViewById(R.id.recommendationDate);
            reasonView = itemView.findViewById(R.id.recommendationReason);
            scoreView = itemView.findViewById(R.id.recommendationScore);
        }

        private void bind(
                RecommendationDto recommendation,
                OnRecommendationClickListener listener
        ) {
            Context context = itemView.getContext();
            ArticleDto article = recommendation.getArticle();
            if (article == null) {
                categoryView.setText(R.string.category_unknown);
                titleView.setText(R.string.recommendation_article_unavailable);
                summaryView.setText("");
                dateView.setText("");
                itemView.setEnabled(false);
                itemView.setOnClickListener(null);
            } else {
                String category = article.getCategoryName();
                categoryView.setText(
                        category == null || category.trim().isEmpty()
                                ? context.getString(R.string.category_unknown)
                                : category
                );
                titleView.setText(article.getTitle());
                summaryView.setText(article.getSummary());
                dateView.setText(DateFormatter.formatArticleDate(article.getPublishedAt()));
                itemView.setEnabled(true);
                itemView.setOnClickListener(view -> listener.onRecommendationClicked(
                        recommendation
                ));
            }
            reasonView.setText(formatReason(context, recommendation.getReason()));
            scoreView.setText(context.getString(
                    R.string.recommendation_score,
                    RecommendationTextFormatter.formatScoreValue(
                            recommendation.getScore(),
                            Locale.getDefault()
                    )
            ));
        }

        private String formatReason(Context context, String reason) {
            if (reason == null || reason.trim().isEmpty()) {
                return context.getString(R.string.recommendation_reason_default);
            }
            switch (reason.trim()) {
                case "als_implicit_feedback":
                    return context.getString(R.string.recommendation_reason_als);
                case "fallback_subscribed_category":
                    return context.getString(R.string.recommendation_reason_subscription);
                case "fallback_global_popular":
                    return context.getString(R.string.recommendation_reason_popular);
                case "personalized_recent_activity":
                    return context.getString(R.string.recommendation_reason_recent_activity);
                default:
                    return RecommendationTextFormatter.humanizeUnknownReason(reason);
            }
        }
    }
}
