package com.fahim.mt;

import java.util.ArrayList;
import java.util.List;

public class BlockData {
    public boolean isActive = false;
    public int matchCount = 0;
    public List<Integer> matchIndices = new ArrayList<>();
    public List<Integer> duplicateMatchIndices = new ArrayList<>();

    public BlockData() {}

    public BlockData(boolean isActive) {
        this.isActive = isActive;
    }
}
