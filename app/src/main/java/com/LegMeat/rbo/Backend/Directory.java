package com.LegMeat.rbo.Backend;

import com.LegMeat.rbo.Exceptions.InvalidFileException;

import java.util.ArrayList;
import java.io.File;

/**
 * Directory aims to gather all applicable videos within a directory, executing the tools which find and delete
 * video overlaps
 */
public class Directory {
    private ArrayList<Video> videoList = new ArrayList<>();
    private String directoryPath;

    /**
     * When the application is ready, this method will execute all the video cuts, according to each individul videos
     * cut points.
     */
    public void executeCuts() {
        // TBC
    }

    /**
     * Adds all applicable videos to list and calculates cut points of each video
     */
    public Directory(String directoryPath) {
        this.directoryPath = directoryPath;
        File directoryFolder = new File(directoryPath);
        String[] fileList = directoryFolder.list();
        if (fileList != null) {
            System.out.println("Directory contains " + fileList.length + " files. Beginning read...");
            for (String filename : fileList) {
                try {
                    videoList.add(new Video(filename, directoryPath + "\\" + filename));
                } catch (InvalidFileException e) {
                    System.out.println("File not added (non-video or unsupported format): " + e.getMessage());
                }
            }
            System.out.println("Directory contains " + videoList.size() + " files. Ending read...");
            if (videoList.size() > 1) {
                int videoNum = 1;
                System.out.println("Finding cut points...");
                for (Video video : videoList) {
                    System.out.println("Video " + videoNum + " out of " + videoList.size() + ".");
                    videoNum++;
                    // sentinel used for finding earliest overlapping video
                    double earliestOverlap = 9999999999.0;
                    Video secondaryVideo = null;
                    for (Video video2 : videoList) {
                        if (video != video2) {
                            Double overlap = video.findOverlap(video2);
                            if (overlap != -1.0 && overlap < earliestOverlap ) {
                                earliestOverlap = overlap;
                                secondaryVideo = video2;
                            }
                        }
                    }
                    if (earliestOverlap != 9999999999.0) {
                        video.updateSecondaryVideo(secondaryVideo);
                        System.out.println("Cut point updated: " + secondaryVideo.getCutPoint());
                    } else {
                        System.out.println("No cut point found!");
                    }
                }
            } else {
                System.out.println("No cut points found.");
            }

        } else {
            throw new InvalidFileException("Directory empty.");
        }
    }
}
