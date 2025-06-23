package com.LegMeat.rbo.Backend;

import com.LegMeat.rbo.Exceptions.ExternalCommandError;
import com.LegMeat.rbo.Exceptions.InvalidFileException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.TreeMap;


public class Video extends File {
    private String fileName;
    private FileType fileType;
    private int fps;
    private ArrayList<Double> keyFrameTimeStamps = new ArrayList();
    private ArrayList<KeyFrame> keyFrames = new ArrayList<>();
    private TreeMap<Video, Duration> overlappingVideos = new TreeMap<>();

    public void addOverlappedMap(Video overlappedVideo, Duration localOverlappedTime) {
        overlappingVideos.put(overlappedVideo, localOverlappedTime);
    }

    public TreeMap<Video, Duration> getOverlappedMap() {
        return overlappingVideos;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    public FileType getFileType() {
        return fileType;
    }

    public void setFps(int fps) {
        this.fps = fps;
    }

    public int getFps() {
        return fps;
    }

    public void cut(LocalDateTime newStart) throws ExternalCommandError, InvalidFileException {
    }

    /**
     * Used during "image2pipe" commands to redirect all errors messages to wherever the null device is depending
     * on a user's operating system.
     */
    public void redirectToNullDevice(ProcessBuilder pb) throws ExternalCommandError, InvalidFileException {
        // Redirects all errors to null device (prevents pollution)
        String osName = System.getProperty("os.name");
        if (osName.toLowerCase().contains("windows")) {
            pb.redirectError(new File("NUL:"));
        } else {
            // device location of UNIX systems (macOS, Linux, etc.)
            pb.redirectError(new File("/dev/null"));
        }
    }

    /**
     * Finds the timestamp of each keyframe using ffprobe, later used by addAllKeyFrames to give each keyFrame object
     * a timestamp.
     */
    public void addKeyFrameTimestamps() {
        String[] probeCommand = {"ffprobe", "-loglevel", "fatal", "-skip_frame", "nokey", "-select_streams",
                "v:0", "-show_entries", "frame=pts_time", "-of", "csv", this.getAbsolutePath()};
        try {
            ProcessBuilder pbProbe = new ProcessBuilder(probeCommand);
            redirectToNullDevice(pbProbe);
            Process process = pbProbe.start();
            Scanner reader = new Scanner(process.getInputStream()).useDelimiter("[frame|,]");
            while (reader.hasNext()) {
                String sTimestamp;
                if (!(sTimestamp = reader.next()).isEmpty()) {
                    double timestamp = Double.parseDouble(sTimestamp);
                    this.keyFrameTimeStamps.add(timestamp);
                }
            }
        } catch (IOException e) {
            e.getMessage();
        }
    }

    public void addAllKeyFrames() {
        // first get load all timestamps
        addKeyFrameTimestamps();
        // Adds all keyframes into the perpetual
        String[] mpegCommand = {"ffmpeg", "-loglevel", "fatal", "-i", this.getAbsolutePath(), "-vf",
                "select='eq(pict_type,PICT_TYPE_I);", "-vsync", "vfr", "-an", "-f", "image2pipe",
                "-c:v", "png", "-"};
        int keyFrameId = 1;

        try {
            ProcessBuilder pbMpeg = new ProcessBuilder(mpegCommand);
            redirectToNullDevice(pbMpeg);
            Process process = pbMpeg.start();

            // attempt to generate keyframe objects

            BufferedInputStream reader = new BufferedInputStream(process.getInputStream());
            BufferedImage frame;
            while ((frame = ImageIO.read(reader)) != null) {
                this.keyFrames.add(new KeyFrame(keyFrameTimeStamps.get(keyFrameId), frame, keyFrameId));
                keyFrameId++;
            }
        } catch (IOException e) {
            e.getMessage();
        }
    }

    public Video(String fileName, String filePath, int fps) {
        super(filePath);
        this.fileName = fileName;
        // later find fps from metadata
        this.fps = fps;
        addAllKeyFrames();

    }
}
