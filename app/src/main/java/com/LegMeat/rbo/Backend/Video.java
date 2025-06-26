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
import java.util.concurrent.TimeUnit;


public class Video extends File {
    private String fileName;
    private FileType fileType;
    private ArrayList<Double> keyFrameTimeStamps = new ArrayList<>();
    private ArrayList<KeyFrame> keyFrames = new ArrayList<>();
    private TreeMap<Video, Duration> overlappingVideos = new TreeMap<>();
    private int corruptedKeyFrames = 0;

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
    private void redirectToNullDevice(ProcessBuilder pb) {
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
    private void addKeyFrameTimestamps() throws ExternalCommandException, InvalidFileException {
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

    private void addAllKeyFrames() throws ExternalCommandException, InvalidFileException {
        // Adds all keyframes into the perpetual
        String[] mpegCommand = {"ffmpeg", "-loglevel", "fatal", "-nostats", "-hide_banner", "-probesize", "32",
                "-analyzeduration", "0", "-i", this.getAbsolutePath(), "-vf",
                "select='eq(pict_type,I)'", "-vsync", "0", "-pix_fmt", "rgb24", "-an", "-f",
                "image2pipe", "-c:v", "png", "-"};

        // May use this attribute later to determine corruption of a video
        this.corruptedKeyFrames = 0;
        try {
            ProcessBuilder pbMpeg = new ProcessBuilder(mpegCommand);
            redirectToNullDevice(pbMpeg);
            Process process = pbMpeg.start();

            // attempt to generate keyframe objects
            PushbackInputStream stdout = new PushbackInputStream(process.getInputStream(), 8);
            BufferedImage keyFrame;
            int keyFrameId = 1;
            byte[] pngMagicBytes = {(byte)0x89,(byte) 0x50,(byte) 0x4E,(byte)0x47,(byte) 0x0D,(byte)0x0A,
                    (byte)0x1A,(byte) 0x0A};
            byte[] buffer = new byte[8];

            // Piping with ffmpeg can cause a LOT of problems. This highly complex, but robust code searches for the
            // magic bytes representing the header of a complete png keyframe, and deletes all interim bytes, as these
            // must be erroneous (either unwanted feedback from ffmpeg [which may vary with version] into stdout,
            // corrupted data, or errors that have managed to find their way into the input stream). IOFile is very
            // strict and quite poor at skipping polluted data, so won't recognise the stdout as one image otherwise.
            System.out.println("Attempting to read " + keyFrameTimeStamps.size() + " key frames...");
            while (true) {
                try {
                    // check if the process has finished
                    if (!process.isAlive()) {
                        if (process.exitValue() == 0) { // the process has exited after reading final keyframe
                            break;
                        } else if (process.exitValue() != 0) { // the process has exited due to an error
                            throw new ExternalCommandException("Unable to extract key keyFrame. Ensure ffmpeg is " +
                                    "installed to system path and file is not corrupt.");
                        }
                    } else {
                        // repeatedly attempt to read keyFrame (keyFrame could be corrupt or polluted)
                        System.out.println("Reading key frame " + keyFrameId + "...");
                        while (true) {
                            // Search for magic bytes
                            int possibleMagicBytesRead = 0;
                            int possibleMagicByte = 0;
                            int skippedBytes = 0;
                            try {
                                while (possibleMagicBytesRead!= 8 && !Thread.currentThread().isInterrupted()) {
                                    possibleMagicByte = stdout.read();
                                    if ((byte) possibleMagicByte == pngMagicBytes[possibleMagicBytesRead]) {
                                        buffer[possibleMagicBytesRead] = (byte) possibleMagicByte;
                                        possibleMagicBytesRead++;
                                    } else if (possibleMagicByte == -1) {
                                            System.out.println("Trailing bytes were non-image.");
                                            break;
                                    } else {
                                        possibleMagicBytesRead = 0;
                                        skippedBytes++;
                                    }
                                }
                                System.out.println("Skipped " + skippedBytes + " non-image byte(s).");
                            } catch (IOException e) {
                                System.out.println("Keyframe " + keyFrameId +  "corrupted.");
                                this.corruptedKeyFrames++;
                                keyFrameId++;
                                break;
                            }

                            // break again (out of external loop) if trailing bytes are non-image
                            if (possibleMagicByte == -1) {
                                break;
                            } else if (possibleMagicBytesRead == 8) {
                                try {
                                    // unread to let ImageIO see the magic bytes too
                                    stdout.unread(buffer, 0, possibleMagicBytesRead);
                                    keyFrame = ImageIO.read(stdout);
                                    if (keyFrame != null) {
                                        try {
                                            this.keyFrames.add(new KeyFrame(keyFrameTimeStamps.get(keyFrameId-1),
                                                    keyFrame, keyFrameId));
                                            System.out.println("Keyframe " + keyFrameId + " Successfully added!.");
                                            System.out.println("Keyframe duration: " +
                                                    keyFrameTimeStamps.get(keyFrameId-1));
                                            keyFrameId++;
                                        } catch (IndexOutOfBoundsException e) {
                                            throw new InvalidFileException("File does not contain a " +
                                                    "timestamp for each keyframe.");
                                        }
                                    } else {
                                        System.out.println("Keyframe " + keyFrameId + "corrupted.");
                                        this.corruptedKeyFrames++;
                                        keyFrameId++;
                                    }
                                } catch (IOException e) {
                                    System.out.println("Keyframe " + keyFrameId +  "corrupted.");
                                    this.corruptedKeyFrames++;
                                    keyFrameId++;
                                }
                            }
                        }
                    }
                } catch (IllegalThreadStateException e) {
                    throw new InvalidFileException("Catastrophic error occurred. File is corrupt.");
                }
            }
            // Check to ensure the process has finished in time. Large timeout time, as some large videos may take a
            // while to process.
            boolean finished = process.waitFor(3, TimeUnit.MINUTES);
            if (!finished) {
                throw new InvalidFileException("File is either too large or corrupt and timed out.");
            } else {
                if (process.exitValue() != 0) {
                    throw new ExternalCommandException("Ensure ffmpeg is installed to system path, and file is not" +
                            "so corrupt that it cannot be read.");
                } else {
                    System.out.println("Successfully read " + keyFrameTimeStamps.size() + " key frames.");
                }
            }
        } catch (IOException e) {
            throw new InvalidFileException("Catastrophic error occurred. File is corrupt.");
        } catch (InterruptedException e) {
            System.out.println("Process cancelled by user.");
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
