package com.LegMeat.rbo.Backend;

import com.LegMeat.rbo.Exceptions.InvalidFileTypeException;

import java.util.Date;

public class Video {
    private String fileName;
    private Date startTime;
    private Date endTime;
    private FileType fileType;

    public Date getEndTime() {
        return endTime;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Video(String fileName, Date startTime, Date endTime) {
        this.fileName = fileName;
        this.startTime = startTime;
        this.endTime = endTime;

        int extension = fileName.lastIndexOf(".");
        if (extension != -1) {
            try {
                this.fileType = FileType.valueOf(fileName.substring(extension + 1));
            }
            catch (Exception e) {
                throw new InvalidFileTypeException("File type not supported.");
            }
        } else {
            throw new InvalidFileTypeException("Invalid file type.");
        }
    }
}
