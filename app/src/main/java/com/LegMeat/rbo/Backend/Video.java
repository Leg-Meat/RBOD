package com.LegMeat.rbo.Backend;

import com.LegMeat.rbo.Exceptions.InvalidFileTypeException;

import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Video extends File {
    private String fileName;
    private Double duration;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private FileType fileType;
    private ArrayList<Integer> data = new ArrayList<>();

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public Double getDuration() {
        return duration;
    }

    public FileType getFileType() { return fileType; }

    public String getFileName() { return fileName; }

    public ArrayList<Integer> getData() { return data; }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public void setDuration(Double duration) {
        this.duration = duration;
    }

    public void setFileType(FileType fileType) { this.fileType = fileType; }

    public void setFileName(String fileName) { this.fileName = fileName; }

    public void setData(ArrayList<Integer> data) { this.data = data; }

    public void setMetaData() {
        try {
            // List of required commands is created, and fed into new ProcessBuilder object, which opens/starts new
            // process.
            String[] commandList = {"ffprobe", "-v", "quiet", "-print_format", "json", "-show_format",
                    "-show_streams", this.getAbsolutePath()};
            ProcessBuilder pb = new ProcessBuilder(commandList);
            Process process = pb.start();

            // BufferedReader is opened and stringBuilder initialised to incrementally create one large
            // json string for the video
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder json = new StringBuilder();
            while (br.readLine() != null) {
                json.append(br.readLine());
            }
            // ensure process has been finished and successfully completed
            int exitValue = 1;
            if (process.waitFor(40, TimeUnit.SECONDS)) {
                exitValue = process.exitValue();
            }

            if (exitValue == 0) {
                // convert string builder object to string
                String jsonString = json.toString();
                // regex pattern matching to find duration and time created in metadata
                Pattern durationPattern = Pattern.compile("\"duration\":\\s*\"([0-9.]+)\"");
                Matcher durationMatcher = durationPattern.matcher(jsonString);
                // sets the video duration to the one found in the metadata
                if (durationMatcher.find()) {
                    duration = Double.parseDouble(durationMatcher.group(1));
                }
                System.out.println("duration: " + duration);
                Pattern timeCreatedPattern = Pattern.compile("\"creation_time\":\\s*\"([^\"]+)\"");
                Matcher timeCreatedMatcher = timeCreatedPattern.matcher(jsonString);
                if (timeCreatedMatcher.find()) {
                    endTime = LocalDateTime.parse(timeCreatedMatcher.group(1));
                }
                System.out.println(endTime);
                // use end time and duration to calculate start time (our issue is that clips are of a fixed length)
                // convert duration to long for direct subtraction (round as a precaution; most clip lengths integers)
                long lDuration = Math.round(duration);
                startTime = endTime.minusSeconds(lDuration);
                System.out.println(startTime);


            }

        } catch (IOException | InterruptedException e) {
            System.out.println(e.getMessage());
        }


    }

    public Video(String fileName, LocalDateTime startTime, LocalDateTime endTime, String filePath) {
        super(filePath);
        this.fileName = fileName;
        this.startTime = startTime;
        this.endTime = endTime;

        int extension = fileName.lastIndexOf(".");
        if (extension != -1) {
           try {
                this.fileType = FileType.valueOf(fileName.substring(extension + 1).toUpperCase());
                setMetaData();
           } catch (RuntimeException e) {
               throw new InvalidFileTypeException("File metadata corrupt.");
           }
        } else {
            throw new InvalidFileTypeException("Invalid file type.");
        }

        try (FileInputStream fis = new FileInputStream(filePath)) {
            for (int i = 0; i < data.size(); i++) {
                data.add(fis.read());
            }
        } catch (FileNotFoundException e) {
            System.out.println("File not found.");
        } catch (SecurityException e) {
            System.out.println("File security does not allow it to be read.");
        } catch (IOException e) {
            System.out.println("Error reading file.");
        }
    }

}
