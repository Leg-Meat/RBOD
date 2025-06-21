package com.LegMeat.rbo.Backend;

import com.LegMeat.rbo.Exceptions.ExternalCommandError;
import com.LegMeat.rbo.Exceptions.InvalidFileException;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Video extends File {
    private String fileName;
    private FileType fileType;

    public void cut(LocalDateTime newStart) throws ExternalCommandError, InvalidFileException {

    }

    public Video(String fileName, String filePath) {
        super(filePath);
        this.fileName = fileName;

    }
}
