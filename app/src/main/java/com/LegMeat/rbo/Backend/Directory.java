package com.LegMeat.rbo.Backend;

import com.LegMeat.rbo.Exceptions.InvalidFileException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.io.File;

/**
 * Directory aims to gather all applicable videos within a directory, executing the tools which find and delete
 * video overlaps
 */
public class Directory {
    private ArrayList<Video> videoList = new ArrayList<>();
    private File directoryFolder;


    public void originalExecuteCuts() {
        // find base directory of all videos
        String newDirectory = directoryFolder + " - Overlap Removed";
        // Create a new directory for cut videos
        try {
            Files.createDirectory(Paths.get(newDirectory));
            int videoNum = 1;
            for (Video video : this.videoList) {
                System.out.println("Attempting to cut video " + videoNum + " out of " + videoList.size() + " .");
                if (video.isCorrupted()) {
                    System.out.println("Video " + videoNum + " is corrupted.");
                } else {
                    try {
                        video.originalcopyCut(newDirectory);
                        System.out.println("Successfully cut video " + videoNum + "!");
                    } catch (InvalidFileException e) {
                        System.out.println("Video has no cutpoint yet.");
                    } catch (IOException e) {
                        System.out.println("IO Error cutting or overwriting video " + videoNum + "! Ensure ffmpeg is" +
                                "installed to system path and has write permissions. Ensure File.nio.file.Files has " +
                                "overwrite permissions. Origianl message: " + e.getMessage());
                    }
                }
                videoNum++;
            }
        } catch (IOException e) {
            System.out.println("Could not create new file directory. 'Cut' directory already exists!");
        }
    }
    /**
     * When the application is ready, this method will execute all the video cuts, according to each individul videos
     * cut points.
     */
    public void executeCuts(boolean overwrite) {
        // if we're not overwriting, create a new directory to copy over to
        String newDirectory;
        if (!overwrite) {
            newDirectory = directoryFolder + " - Overlap Removed";
        } else {
            newDirectory = directoryFolder.toString();
        }
        try {
            if (!overwrite) {
                // Create a new directory for cut videos if we're not overwriting
                Files.createDirectory(Paths.get(newDirectory));
            }
            int videoNum = 1;
            for (Video video : this.videoList) {
                System.out.println("Attempting to cut video " + videoNum + " out of " + videoList.size() + " .");
                if (video.isCorrupted()) {
                    System.out.println("Video " + videoNum + " is corrupted.");
                } else {
                    try {
                        video.cut(newDirectory, overwrite);
                        System.out.println("Successfully cut video " + videoNum + "!");
                    } catch (InvalidFileException e) {
                        System.out.println("Video has no cutpoint yet.");
                    } catch (IOException e) {
                        System.out.println("IO Error cutting or overwriting video " + videoNum + "! Ensure ffmpeg is" +
                                "installed to system path and has write permissions. Ensure File.nio.file.Files has " +
                                "overwrite permissions. Origianl message: " + e.getMessage());
                    }
                }
                videoNum++;
            }
        } catch (IOException e) {
            System.out.println("Could not create new file directory. 'Cut' directory already exists!");
        }
    }

    /**
     * Adds all applicable videos to list and calculates cut points of each video
     */
    public Directory(String directoryPath) {
        this.directoryFolder = new File(directoryPath);
        String[] fileList = directoryFolder.list();
        int videoNumber = 0;
        if (fileList != null) {
            System.out.println("Directory contains " + fileList.length + " files. Beginning read...");
            for (String filename : fileList) {
                videoNumber += 1;
                System.out.println("Attempting to add video: " + videoNumber);
                try {
                    videoList.add(new Video(filename, directoryPath + "\\" + filename));
                } catch (InvalidFileException e) {
                    System.out.println("File not added (non-video, unsupported format or corrupt video): " +
                            e.getMessage());
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
