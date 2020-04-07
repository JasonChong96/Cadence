package com.cs4347.cadence.musicPlayer;

import androidx.annotation.RawRes;

import com.cs4347.cadence.R;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;

public class SongSelector {
    private TreeMap<Integer, ArrayList<TimeShiftedSong>> songLibrary;

    private int currentBpm;
    private int currentTrackNo;

    private int bpmChangeThreshold = 10;
    private int bpmChangeRange = 5;
    // [-2 * bpmChangeThreshold, -bpmChangeThreshold, 0, bpmChangeThreshold, 2 * bpmChangeThreshold]
    private ArrayList<Integer> bpmList;

    public SongSelector() {
        songLibrary = new TreeMap<>();
        currentBpm = 0;
        currentTrackNo = 0;
        populateLibrary();
        for(int i = 0; i < bpmChangeRange; i++) {
            bpmList.add((i - bpmChangeRange/2)*bpmChangeThreshold);
        }
    }

    public TimeShiftedSong getNextSong(int bpm) {
        if (!isChangingBpm(bpm)) { //No change in BPM, play next song
            currentTrackNo++;
            if (currentTrackNo >= songLibrary.get(currentBpm).size()) {
                currentTrackNo = 0;
            }
            return songLibrary.get(currentBpm).get(currentTrackNo);
        }
        if(!isSwitchingSong(bpm)) { // Some change in the bpm, using the same song, but change the bpm
            // V2: Change in BPM, change the BPM for the song
            currentTrackNo = (currentTrackNo + 1) % songLibrary.get(currentBpm).size();
            return songLibrary.get(currentBpm).get(currentTrackNo);
        }

        if (songLibrary.containsKey(bpm)) { //Change in BPM, BPM is found, play first song
            currentBpm = bpm;
            currentTrackNo = 0;
            return songLibrary.get(currentBpm).get(currentTrackNo);
        }

        // Change in BPM, exact BPM not found in library. Find closest and play first song
        currentBpm = getBestFitBpm(bpm);
        currentTrackNo = 0;
        return songLibrary.get(currentBpm).get(currentTrackNo);


    }

    private int getBestFitBpm(int bpm) {
        int doubleTimeBpm = 2 * bpm;

        int[] differences = new int[4];

        try {
            differences[0] = songLibrary.lowerKey(bpm); // Closest Lowest BPM
        } catch (NullPointerException npe) {
            differences[0] = Integer.MAX_VALUE;
        }
        try {
            differences[1] = songLibrary.higherKey(bpm); // Closest highest BPM
        } catch (NullPointerException npe) {
            differences[1] = Integer.MAX_VALUE;
        }
        try {
            differences[2] = songLibrary.lowerKey(doubleTimeBpm); // Closest Lowest double time BPM
        } catch (NullPointerException npe) {
            differences[2] = Integer.MAX_VALUE;
        }
        try {
            differences[3] = songLibrary.higherKey(doubleTimeBpm); // Closest highest double time BPM
        } catch (NullPointerException npe) {
            differences[3] = Integer.MAX_VALUE;
        }

        int min_difference = Math.abs(bpm - differences[0]);
        int min_index = 0;
        for (int i = 1; i < differences.length; i++) {
            int current_difference = Math.abs(bpm - differences[i]);
            if (current_difference < min_difference) {
                min_difference = current_difference;
                min_index = i;
            }
        }
        System.out.println(Arrays.toString(differences));
        System.out.println(min_index);

        return differences[min_index];
    }

    private void populateLibrary() {
        Field[] fields = R.raw.class.getFields();
        int i = 0;
        while (i < fields.length) {
            @RawRes int slowId = getIdFromField(fields[i]);
            int slowBpm = getBpmFromField(fields[i]);
            i++;

            @RawRes int originalId = getIdFromField(fields[i]);
            int originalBpm = getBpmFromField(fields[i]);
            i++;

            @RawRes int fastId = getIdFromField(fields[i]);
            int fastBpm = getBpmFromField(fields[i]);
            i++;

            TimeShiftedSong currentSong = new TimeShiftedSong(slowId, slowBpm, originalId, originalBpm, fastId, fastBpm);
            if (songLibrary.containsKey(originalBpm)) {
                songLibrary.get(originalBpm).add(currentSong);
            } else {
                ArrayList<TimeShiftedSong> songList = new ArrayList<TimeShiftedSong>();
                songList.add(currentSong);
                songLibrary.put(originalBpm, songList);
            }
        }
    }

    private int getBpmFromField(Field field) {
        String name = field.getName();
        String[] details = name.split("_");
        return Integer.parseInt(details[details.length - 1]);
    }

    private int getIdFromField(Field field) {
        @RawRes int resourceId = 0;
        try {
            resourceId = (Integer) field.get(null);
        } catch (Exception e) {

        }
        return resourceId;
    }

    public int getCurrentTrackNo() {
        return currentTrackNo;
    }

    public void setCurrentTrackNo(int currentTrackNo) {
        this.currentTrackNo = currentTrackNo;
    }

    public boolean isSwitchingSong(int newBpm) {
        return newBpm - currentBpm < bpmChangeThreshold*bpmChangeRange/2 &&
                currentBpm - newBpm < bpmChangeThreshold*bpmChangeRange/2;
    }

    public boolean isChangingBpm(int newBpm) {
        //return getBestFitBpm(newBpm) != currentBpm;
        // The absolute difference for the current and new bpm is within the threshold.
        return newBpm - currentBpm < bpmChangeThreshold/2 &&
                currentBpm - newBpm < bpmChangeThreshold/2;
    }
}
