package com.fahim.mt;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fahim.mt.mtproto.TelegramClientManager;
import java.util.ArrayList;
import java.util.List;

public class GridViewActivity extends AppCompatActivity implements TelegramClientManager.BotInteractionListener {
    private RecyclerView recyclerView;
    private GridAdapter adapter;
    private List<BlockData> blockList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grid_view);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 10)); // 10 columns
        adapter = new GridAdapter(blockList);
        recyclerView.setAdapter(adapter);

        // Register as listener
        TelegramClientManager.getInstance(this).setInteractionListener(this);
    }

    @Override
    public void onStepCompleted() {
        runOnUiThread(() -> {
            // If current block is active but no matches yet, it might be the "start" or "entering listings"
            // Let's just add a new gray block for each step unless we want to reuse.
            // User says: "sending start command is one color like gray then opening listing makes next block to be gray"
            BlockData block = new BlockData(true);
            blockList.add(block);
            adapter.notifyItemInserted(blockList.size() - 1);
            recyclerView.scrollToPosition(blockList.size() - 1);
        });
    }

    @Override
    public void onMatchFound(int count, List<Integer> uniqueIndices, List<Integer> duplicateIndices) {
        runOnUiThread(() -> {
            if (blockList.isEmpty()) {
                onStepCompleted();
            }
            BlockData currentBlock = blockList.get(blockList.size() - 1);
            currentBlock.matchCount = count;
            currentBlock.matchIndices.addAll(uniqueIndices);
            currentBlock.duplicateMatchIndices.addAll(duplicateIndices);
            adapter.notifyItemChanged(blockList.size() - 1);
        });
    }

    @Override
    public void onNextPage() {
        runOnUiThread(() -> {
            // "while it goes to next page it creates another block in the grid and its gray if there is none match found"
            BlockData block = new BlockData(true);
            blockList.add(block);
            adapter.notifyItemInserted(blockList.size() - 1);
            recyclerView.scrollToPosition(blockList.size() - 1);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TelegramClientManager.getInstance(this).setInteractionListener(null);
    }
}
