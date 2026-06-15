package com.fahim.mt;

import java.util.ArrayList;
import java.util.List;

public class BlockData {
    public static class MatchDetail {
        public String bin;
        public String price;
        public String type; // RED, YELLOW, BLUE, GOLD

        public MatchDetail(String bin, String price, String type) {
            this.bin = bin;
            this.price = price;
            this.type = type;
        }
    }

    public boolean isActive = false;
    public int matchCount = 0;
    public List<Integer> matchIndices = new ArrayList<>();
    public List<Integer> duplicateMatchIndices = new ArrayList<>();
    public List<Integer> historyMatchIndices = new ArrayList<>();
    public List<Integer> goldMatchIndices = new ArrayList<>();
    public List<MatchDetail> matchDetails = new ArrayList<>();
    
    public double maxPrice = 0.0;
    public double totalPrice = 0.0;
    public int blankPageCount = 1;
    public boolean isBlankPageGroup = false;

    public BlockData() {}

    public BlockData(boolean isActive) {
        this.isActive = isActive;
    }
}
