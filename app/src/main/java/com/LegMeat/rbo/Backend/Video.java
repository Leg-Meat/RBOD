package com.LegMeat.rbo.Backend;

import com.LegMeat.rbo.Exceptions.ExternalCommandException;
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
    private ArrayList<Double> keyFrameTimeStamps = new ArrayList<>();
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


    public void cut(LocalDateTime newStart) throws ExternalCommandException, InvalidFileException {
    }

    /**
     * Used during "image2pipe" commands to redirect all errors messages to wherever the null device is depending
     * on a user's operating system.
     */
    public void redirectToNullDevice(ProcessBuilder pb) {
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
    public void addKeyFrameTimestamps() throws ExternalCommandException, InvalidFileException {
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
            if (process.exitValue() != 0) {
                throw new ExternalCommandException("Unable to extract key frame timestamps. Ensure ffprobe is installed" +
                        "to system path.");
            }
        } catch (SecurityException e) {
            throw new ExternalCommandException("Unable to extract key frame timestamps. Program denied security" +
                    " permissions.");
        } catch (UnsupportedOperationException e) {
            throw new ExternalCommandException("Unable to extract key frame timestamps. Operating system unsupported.");
        } catch (IOException e) {
            throw new InvalidFileException("Unable to extract key frame timestamps. File corrupt.");
        }
    }

    public void addAllKeyFrames() throws ExternalCommandException, InvalidFileException {
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
            throw new ExternalCommandException("Unable to extract key frame timestamps.");
        }
    }

    public Video(String fileName, String filePath) {
        super(filePath);
        this.fileName = fileName;
        try{
            this.fileType = FileType.valueOf(fileName.substring(fileName.length() - 3).toUpperCase());
        } catch (IllegalArgumentException e){
            throw new InvalidFileException("Invalid file type.");
        }
        try {
            addKeyFrameTimestamps();
        } catch (InvalidFileException | ExternalCommandException e) {
            e.getMessage();
        }
        try {
           addAllKeyFrames();
        } catch(InvalidFileException| ExternalCommandException e) {
            e.getMessage();
        }

    }
}
