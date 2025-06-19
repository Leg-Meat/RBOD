package com.LegMeat.rbo.Backend;

import com.LegMeat.rbo.Exceptions.InvalidFileTypeException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

public class Video extends File {
    private String fileName;
    private Date startTime;
    private Date endTime;
    private FileType fileType;
    private ArrayList<Integer> data = new ArrayList<>();

    public Date getEndTime() {
        return endTime;
    }

    public Date getStartTime() {
        return startTime;
    }

    public FileType getFileType() { return fileType; }

    public String getFileName() { return fileName; }

    public ArrayList<Integer> getData() { return data; }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public void setFileType(FileType fileType) { this.fileType = fileType; }

    public void setFileName(String fileName) { this.fileName = fileName; }

    public void setData(ArrayList<Integer> data) { this.data = data; }

    public Video(String fileName, Date startTime, Date endTime, String filePath) {
        super(filePath);
        this.fileName = fileName;
        this.startTime = startTime;
        this.endTime = endTime;

        int extension = fileName.lastIndexOf(".");
        if (extension != -1) {
            try {
                this.fileType = FileType.valueOf(fileName.substring(extension + 1).toUpperCase());
            }
            catch (Exception e) {
                throw new InvalidFileTypeException("File type not supported.");
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
