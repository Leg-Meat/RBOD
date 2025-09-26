package com.LegMeat.rbo.Backend;

import com.LegMeat.rbo.Exceptions.ExternalCommandException;
import com.LegMeat.rbo.Exceptions.InvalidFileException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;


public class Video extends File {
    private String fileName;
    private FileType fileType;
    private Double duration;
    private ArrayList<Double> keyFrameTimeStamps = new ArrayList<>();
    private ArrayList<KeyFrame> keyFrames = new ArrayList<>();
    private Boolean isLeadVideo = false; // dictates whether the clip is a lead video of another overlapping clip
    private Double cutPoint = -1.0; // where to cut secondary video from. -1 if not a secondary video.
    private Video secondaryVideo = null; // dictates the closest overlapping video
    private boolean corrupted = false; // dictates if a video is corrupt

    public ArrayList<KeyFrame> getKeyFrames() {
        return keyFrames;
    }

    public void displayKeyFrames() {
        for (KeyFrame keyFrame : keyFrames) {
            System.out.println(keyFrame.toString());
        }
    }

    public void setCorrupted(boolean corrupted) {
        this.corrupted = corrupted;
    }

    public boolean isCorrupted() {
        return corrupted;
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

    public void setDuration(Double duration) {
        this.duration = duration;
    }

    public Double getDuration() {
        return duration;
    }

    public Boolean isLeadVideo() {
        return isLeadVideo;
    }

    public void setIsLeadVideo(Boolean leadVideo) {
        isLeadVideo = leadVideo;
    }

    public double getCutPoint() {
        return cutPoint;
    }

    public void setCutPoint(double cutPoint) {
        this.cutPoint = cutPoint;
    }

    public Video getSecondaryVideo() {
        return secondaryVideo;
    }

    public void setSecondaryVideo(Video secondaryVideo) {
        this.secondaryVideo = secondaryVideo;
    }

    /**
     * Cuts the video by its cut point
     * @throws ExternalCommandException
     * @throws InvalidFileException
     */
    public void cut() throws ExternalCommandException, InvalidFileException, IOException {
        if (this.cutPoint == -1.0) {
            throw new InvalidFileException("No CutPoint yet.");
        } else {
            // Assuming the video HAS a cut point, a temporary copy of the cut video is made, so that the original file
            // won't be lost or corrupted if the process fails for any reason.
            String outputAbsolutePath = this.getAbsolutePath().substring(0,this.getAbsolutePath().length() - 3)
                    + "_temp.mkv";
            String[] mpegCommand = {"ffmpeg", "-y", "-ss", String.valueOf(this.cutPoint), "-i", this.getAbsolutePath(),
            "-c", "copy", outputAbsolutePath};
            try {
                ProcessBuilder pbMpeg = new ProcessBuilder(mpegCommand);
                // redirect both error and stdout to null device (stdout unnecessary here and will only hang)
                redirectToNullDevice(pbMpeg, true);
                redirectToNullDevice(pbMpeg, false);
                Process process = pbMpeg.start();
                boolean finished = process.waitFor(1, TimeUnit.MINUTES);
                if (!finished) {
                    throw new InvalidFileException("File is either too large or corrupt and timed out.");
                } else {
                    if (process.exitValue() != 0) {
                        throw new ExternalCommandException("Unable to cut video. Ensure ffmpeg is " +
                                "installed to system path and has write permissions.");
                    }
                    // now begin overwrite process...
                    try {
                        Path outPutPath = Paths.get(outputAbsolutePath);
                        Path originalPath = Paths.get(this.getAbsolutePath());
                        Files.move(outPutPath, originalPath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new IOException(e.getMessage());
                    }
                }
            } catch (SecurityException e) {
                throw new ExternalCommandException("Unable to cut video. Program denied security permissions.");
            } catch (UnsupportedOperationException e) {
                throw new ExternalCommandException("Unable to cut video. Operating system unsupported.");
            } catch (IOException e) {
                throw new IOException("File could not be cut due to corruption.");
            } catch (InterruptedException e) {
                System.out.println("Process cancelled by user.");
            }
        }
    }

    /**
     * Used during "image2pipe" commands to redirect all errors messages to wherever the null device is depending
     * on a user's operating system.
     */
    private void redirectToNullDevice(ProcessBuilder pb, boolean error) {
        String osName = System.getProperty("os.name");
        if (error) {
            // Redirects all errors to null device (prevents pollution)
            if (osName.toLowerCase().contains("windows")) {
                pb.redirectError(new File("NUL:"));
            } else {
                // device location of UNIX systems (macOS, Linux, etc.)
                pb.redirectError(new File("/dev/null"));
            }
        } else {
            // Redirects all stdout to null device (prevents hanging)
            if (osName.toLowerCase().contains("windows")) {
                pb.redirectInput(new File("NUL:"));
            } else {
                // device location of UNIX systems (macOS, Linux, etc.)
                pb.redirectInput(new File("/dev/null"));
            }
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
            redirectToNullDevice(pbProbe, true);
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
                throw new ExternalCommandException("Unable to extract key frame timestamps. Ensure ffprobe is " +
                        "installed to system path.");
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
        int corruptedKeyFrames = 0;
        try {
            ProcessBuilder pbMpeg = new ProcessBuilder(mpegCommand);
            redirectToNullDevice(pbMpeg, true);
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
                                corruptedKeyFrames++;
                                keyFrameId++;
                                break;
                            }
                            // break again (out of external loop) if trailing post-magic-bytes are non-image
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
                                            System.out.println("Keyframe timestamp: " +
                                                    keyFrameTimeStamps.get(keyFrameId-1));
                                            keyFrameId++;
                                        } catch (IndexOutOfBoundsException e) {
                                            throw new InvalidFileException("File does not contain a " +
                                                    "timestamp for each keyframe.");
                                        }
                                    } else {
                                        System.out.println("Keyframe " + keyFrameId + "corrupted.");
                                        corruptedKeyFrames++;
                                        keyFrameId++;
                                    }
                                } catch (IOException e) {
                                    System.out.println("Keyframe " + keyFrameId +  "corrupted.");
                                    corruptedKeyFrames++;
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

    private void findDuration() {
        // ffProbe command finds timestamp of last packet (finding duration in metadata isn't robust; not all
        // file type metadata contains it)
        String[] probeCommand = {"ffprobe", "-v", "error", "-show_entries", "format=duration", "-of",
                "default=noprint_wrappers=1:nokey=1", this.getAbsolutePath()};
        try {
            // we don't redirect to null device here, as for some reason the command output arrives through the
            // error stream
            ProcessBuilder pbProbe = new ProcessBuilder(probeCommand);
            Process process = pbProbe.start();
            // Output duration
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            try {
                this.duration = Double.parseDouble(reader.readLine());
            } catch (Exception e) {
                this.corrupted = true;
                throw new InvalidFileException("File is corrupt.");
            }
            // Check to ensure the process has finished in time. Short timeout time, as shouldn't be long process.
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
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

    /**
     * ---NEEDS REFACTORING--- (overlaps a LOT with findOverlap method)
     *
     * Finds the "cut point" of where the secondary video needs to be cut from
     * @param videoTwo the second video being compared
     * @return
     */
    public Double findCutPoint(Video videoTwo) {
        Double cutPoint = -1.0;
        for (KeyFrame frameVidTwo: videoTwo.getKeyFrames()) {
            boolean found = false;
            for (KeyFrame frameVidOne : this.keyFrames) {
                if (frameVidOne.equals(frameVidTwo)) {
                    // Cut point is lead video duration, subtract lead video overlap time
                    cutPoint = this.duration - frameVidOne.getTimestamp();
                    System.out.println("Cut point at: " + cutPoint);
                    found = true;
                    break;
                }
            }
            if (found) {
                break;
            }
        }
        return cutPoint;
    }

    /**
     * updates the secondary video, if a new, or closer, overlapping video is found
     * @param givenSecondaryVideo The new secondary video to update the new (or old one)
     */
    public void updateSecondaryVideo(Video givenSecondaryVideo) {
        if (secondaryVideo != null) {
            // update attributes of old secondary video
            Video oldSecondaryVideo = this.secondaryVideo;
            oldSecondaryVideo.setCutPoint(-1.0);
        }
        // update new secondary video
        this.secondaryVideo = givenSecondaryVideo;
        this.secondaryVideo.setCutPoint(findCutPoint(givenSecondaryVideo));
    }

    /**
     * Compares the keyframes of the video to the keyframes of another video. If an overlap is found, the overlapping
     * timestamp is output to console (but only returned if the video is a "lead" video in the timestamp (i.e. other
     * video is secondary)
     * @param videoTwo the second video being compared
     * @return
     */
    public Double findOverlap(Video videoTwo) {
        for (KeyFrame frameVidTwo: videoTwo.getKeyFrames()) {
            for (KeyFrame frameVidOne : this.keyFrames) {
                if (frameVidOne.equals(frameVidTwo)) {
                    System.out.println("First Match found at: " + frameVidOne.getTimestamp() +
                            " and: " + frameVidTwo.getTimestamp());
                    // Overlapping videos only need to be unidirectonal, so we don't return the timestamp if it is
                    // as secondary video (i.e. if frameVidOne is 0.0)
                    if (frameVidOne.getTimestamp() == 0.0) {
                        return -1.0;
                    } else {
                        return frameVidOne.getTimestamp();
                    }
                }
            }
        }
        // returns -1.0 if no overlap is found.
        return -1.0;
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
        try {
            findDuration();
        } catch (InvalidFileException | ExternalCommandException e) {
            e.getMessage();
        }

    }
}
