package com.fahim.mt;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.fahim.mt.mtproto.TelegramClientManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import java.util.ArrayList;
import java.util.List;

public class GridViewActivity extends AppCompatActivity implements TelegramClientManager.BotInteractionListener, GridAdapter.OnItemClickListener {
    private RecyclerView recyclerView;
    private GridAdapter adapter;
    private List<BlockData> blockList;
    
    private ViewPager2 dashboardPager;
    private TabLayout dashboardTabs;
    
    // Page 1 views
    private TextView redCountText, blueCountText, yellowCountText, goldCountText;
    private MaterialButton startBtn, stopBtn;
    
    // Page 2 views
    private LinearLayout matchesContainer;
    private View matchesScrollView;
    private TextView noMatchesText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grid_view);

        dashboardPager = findViewById(R.id.dashboardPager);
        dashboardTabs = findViewById(R.id.dashboardTabs);
        
        DashboardPagerAdapter pagerAdapter = new DashboardPagerAdapter();
        dashboardPager.setAdapter(pagerAdapter);
        dashboardPager.setOffscreenPageLimit(2); // Keep both pages in memory

        new TabLayoutMediator(dashboardTabs, dashboardPager, (tab, position) -> {
            tab.setText(position == 0 ? "Status" : "Matches");
        }).attach();

        TelegramClientManager manager = TelegramClientManager.getInstance(this);
        blockList = manager.getBlockList();
        
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 10)); // 10 columns
        adapter = new GridAdapter(blockList, this);
        recyclerView.setAdapter(adapter);

        // We need to wait for ViewPager2 to layout its pages before accessing views
        dashboardPager.post(() -> {
            View page1 = dashboardPager.getChildAt(0);
            if (page1 instanceof RecyclerView) {
                // ViewPager2 uses a RecyclerView internally
                RecyclerView rv = (RecyclerView) page1;
                
                // This is a bit hacky because ViewPager2 lazily creates views.
                // Instead of finding by ID directly, let's use a PageChangeCallback or similar.
                // But for a simple dashboard with 2 pages, we can just grab them if they exist.
            }
        });
        
        // Better way: Listen for page selection and initialize views if not already done.
        dashboardPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                initPageViews(position);
            }
        });

        // Register as listener
        manager.setInteractionListener(this);
        
        if (!blockList.isEmpty()) {
            recyclerView.scrollToPosition(blockList.size() - 1);
        }
    }

    private void initPageViews(int position) {
        View page = dashboardPager.findViewWithTag("page_" + position);
        // We'll use a slightly different approach: DashboardPagerAdapter will tag the views.
        // Actually, let's just use the ViewPager2's internal RecyclerView to find views by ID.
        
        if (redCountText == null) {
            redCountText = findViewById(R.id.redCountText);
            blueCountText = findViewById(R.id.blueCountText);
            yellowCountText = findViewById(R.id.yellowCountText);
            goldCountText = findViewById(R.id.goldCountText);
            startBtn = findViewById(R.id.startInteractionButton);
            stopBtn = findViewById(R.id.stopInteractionButton);
            
            if (startBtn != null) {
                startBtn.setOnClickListener(v -> TelegramClientManager.getInstance(this).interactWithBot("@CentscardBot"));
                stopBtn.setOnClickListener(v -> TelegramClientManager.getInstance(this).stopBot());
                updateDashboard();
            }
        }
        
        if (matchesContainer == null) {
            matchesContainer = findViewById(R.id.matchesContainer);
            matchesScrollView = findViewById(R.id.matchesScrollView);
            noMatchesText = findViewById(R.id.noMatchesText);
        }
    }

    private void updateDashboard() {
        if (redCountText == null) return;
        TelegramClientManager manager = TelegramClientManager.getInstance(this);
        redCountText.setText("Red: " + manager.getTotalRed());
        blueCountText.setText("Blue: " + manager.getTotalBlue());
        yellowCountText.setText("Yellow: " + manager.getTotalYellow());
        goldCountText.setText("Gold: " + manager.getTotalGold());
    }

    @Override
    public void onItemClick(int position) {
        BlockData data = blockList.get(position);
        dashboardPager.setCurrentItem(1, true); // Swipe to Matches page
        dashboardPager.post(() -> {
            initPageViews(1);
            showBlockMatches(data);
        });
    }

    private void showBlockMatches(BlockData data) {
        if (matchesContainer == null) return;
        matchesContainer.removeAllViews();
        if (data.matchDetails == null || data.matchDetails.isEmpty()) {
            if (matchesScrollView != null) matchesScrollView.setVisibility(View.GONE);
            if (noMatchesText != null) noMatchesText.setVisibility(View.VISIBLE);
            return;
        }

        if (matchesScrollView != null) matchesScrollView.setVisibility(View.VISIBLE);
        if (noMatchesText != null) noMatchesText.setVisibility(View.GONE);
        
        for (BlockData.MatchDetail match : data.matchDetails) {
            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 8, 8, 8);
            card.setLayoutParams(params);
            card.setCardElevation(4f);
            card.setRadius(12f);
            card.setStrokeWidth(2);
            card.setStrokeColor(getMatchColor(match.type));
            card.setClickable(true);
            card.setFocusable(true);
            card.setCheckable(true);

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(16, 8, 16, 8);

            TextView binText = new TextView(this);
            binText.setText(match.bin);
            binText.setTypeface(null, android.graphics.Typeface.BOLD);
            binText.setTextSize(14f);

            TextView priceText = new TextView(this);
            priceText.setText("$" + match.price);
            priceText.setTextSize(12f);

            layout.addView(binText);
            layout.addView(priceText);
            card.addView(layout);

            card.setOnClickListener(v -> {
                for (int i = 0; i < matchesContainer.getChildCount(); i++) {
                    View child = matchesContainer.getChildAt(i);
                    if (child instanceof MaterialCardView) {
                        ((MaterialCardView)child).setChecked(false);
                    }
                }
                card.setChecked(true);
            });

            matchesContainer.addView(card);
        }
    }

    private int getMatchColor(String type) {
        switch (type) {
            case "RED": return android.graphics.Color.RED;
            case "YELLOW": return android.graphics.Color.YELLOW;
            case "BLUE": return android.graphics.Color.BLUE;
            case "GOLD": return android.graphics.Color.parseColor("#FFD700");
            default: return android.graphics.Color.GRAY;
        }
    }

    @Override
    public void onGridReset() {
        runOnUiThread(() -> {
            adapter.notifyDataSetChanged();
            updateDashboard();
            if (matchesContainer != null) matchesContainer.removeAllViews();
            if (matchesScrollView != null) matchesScrollView.setVisibility(View.GONE);
            if (noMatchesText != null) noMatchesText.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onGridUpdated() {
        runOnUiThread(() -> {
            adapter.notifyDataSetChanged();
            updateDashboard();
            if (!blockList.isEmpty()) {
                recyclerView.scrollToPosition(blockList.size() - 1);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TelegramClientManager.getInstance(this).setInteractionListener(null);
    }
}
