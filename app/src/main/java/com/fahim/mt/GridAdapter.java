package com.fahim.mt;

import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class GridAdapter extends RecyclerView.Adapter<GridAdapter.ViewHolder> {
    private final List<BlockData> blockList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public GridAdapter(List<BlockData> blockList, OnItemClickListener listener) {
        this.blockList = blockList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        GridBlockView view = new GridBlockView(parent.getContext());
        // Set layout params for the custom view
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        view.setLayoutParams(params);
        return new ViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.view.setData(blockList.get(position));
    }

    @Override
    public int getItemCount() {
        return blockList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final GridBlockView view;
        public ViewHolder(GridBlockView view, OnItemClickListener listener) {
            super(view);
            this.view = view;
            view.setOnClickListener(v -> {
                if (listener != null) {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        listener.onItemClick(pos);
                    }
                }
            });
        }
    }
}
