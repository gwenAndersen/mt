package com.fahim.mt;

import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class GridAdapter extends RecyclerView.Adapter<GridAdapter.ViewHolder> {
    private final List<BlockData> blockList;

    public GridAdapter(List<BlockData> blockList) {
        this.blockList = blockList;
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
        return new ViewHolder(view);
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
        public ViewHolder(GridBlockView view) {
            super(view);
            this.view = view;
        }
    }
}
