package com.nus.cs5248.team3.videoServer.service;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FileService {
    @Value("${upload_folder_path}")
    private String UPLOADED_FOLDER;
    @Value("${ffmpeg_path}")
    private String FFMPEG_PATH;
    @Value("${ffprobe_path}")
    private String FFPROBE_PATH;
    private static final Logger logger
            = LoggerFactory.getLogger(FileService.class);
    private static Path fileStorageLocation;
    private boolean isWindows = System.getProperty("os.name")
            .toLowerCase().startsWith("windows");
    @Autowired
    private EncodeUtil encodeUtil;

    //get file storage location, configured in application.properties
    private Path getFileStorageLocation() {
        if (this.fileStorageLocation == null) {
            this.fileStorageLocation = Paths.get(UPLOADED_FOLDER)
                    .toAbsolutePath().normalize();
        }
        return this.fileStorageLocation;
    }

    //get all uploaded files stored on server
    public List<String> findAll() {
        List<String> files = null;
        try (Stream<Path> walk = Files.walk(Paths.get(UPLOADED_FOLDER))) {
            files = walk.filter(Files::isRegularFile)
                    .map(x -> ServletUriComponentsBuilder.fromCurrentContextPath()
                            .path("/downloadFile/")
                            .path(x.getFileName().toString())
                            .toUriString()).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            logger.trace(e.getLocalizedMessage());
            logger.trace(e.getStackTrace().toString());
        }
        return files;
    }

    //store file on to server
    public String store(MultipartFile file) throws Exception {
        this.fileStorageLocation = getFileStorageLocation();
        logger.trace("Get into StorageService");

        // Normalize file name
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());

        try {
            // Check if the file's name contains invalid characters
            if (fileName.contains("..")) {
                logger.trace("Sorry! Filename contains invalid path sequence " + fileName);
                throw new Exception("Sorry! Filename contains invalid path sequence " + fileName);
            }

            // Copy file to the target location (Replacing existing file with the same name)
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            //Encode stored file
            return fileName;
        } catch (Exception e) {
            e.printStackTrace();
            logger.trace(e.getLocalizedMessage());
            logger.trace(e.getStackTrace().toString());
            throw new Exception("Failed to store file " + fileName);
        }
    }


    //provide file for others to download
    public Resource loadFileAsResource(String fileName) throws Exception {
        this.fileStorageLocation = getFileStorageLocation();
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            } else {
                logger.trace("File not found " + fileName);
                throw new Exception("File not found " + fileName);
            }
        } catch (Exception e) {
            logger.trace(e.getMessage());
            logger.trace(e.getStackTrace().toString());
            e.printStackTrace();
            throw new Exception("File not found " + fileName, e);
        }
    }

    //encode uploaded files to different resolutions by FFMpeg
    public void encode(String fileName) {
        logger.info("Start encoding now");
        try {
            FFmpeg ffmpeg = new FFmpeg(FFMPEG_PATH);
            FFprobe ffprobe = new FFprobe(FFPROBE_PATH);
            FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
            String filePath = this.fileStorageLocation.resolve(fileName).normalize().toAbsolutePath().toString();
            int slashPostion = filePath.lastIndexOf(File.separator);

            encodeUtil.encodeTo720p(filePath, slashPostion, executor);
            encodeUtil.encodeTo480p(filePath, slashPostion, executor);
            encodeUtil.encodeTo360p(filePath, slashPostion, executor);
            encodeUtil.encodeTo240p(filePath, slashPostion, executor);
        } catch (IOException e) {
            logger.trace(e.getMessage());
            logger.trace(e.getStackTrace().toString());
            e.printStackTrace();
        }
    }

    //Concat segments with same resolution
    public void concatSegment(List<String> uploadedFiles, String resolution, String videoID) {
        String concatFile = resolution + "_" + videoID + ".mp4";
        String concatCommand = "MP4Box -add " + resolution + "_" + uploadedFiles.get(0);
        for (int i = 1; i < uploadedFiles.size(); i++) {
            concatCommand = concatCommand + " -cat " + resolution + "_" + uploadedFiles.get(i);
        }
        concatCommand = concatCommand + " " + concatFile;
        System.out.println("concatCommand: "+ concatCommand);
        ProcessBuilder concatBuilder = null;
        if (isWindows) {
            concatBuilder = new ProcessBuilder(
                    "cmd.exe", "/c", "cd " + UPLOADED_FOLDER + " && " + concatCommand);
        } else {
            concatBuilder = new ProcessBuilder(
                    "sh", "-c", "cd " + UPLOADED_FOLDER + " && " + concatCommand);
        }
        concatBuilder.redirectErrorStream(true);
        Process p = null;
        try {
            p = concatBuilder.start();

            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while (true) {
                line = r.readLine();
                if (line == null) {
                    break;
                }
                System.out.println(line);
            }
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            logger.trace(e.getMessage());
            logger.trace(e.getStackTrace().toString());
        }

    }

    //get uploaded videos.
    public List<String> getUploadedFiles(String videoID) {
        List<String> files = null;
        try (Stream<Path> walk = Files.walk(Paths.get(UPLOADED_FOLDER))) {
            files = walk.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().startsWith(videoID))
                    .filter(f -> "mp4".equals(com.google.common.io.Files.getFileExtension(f.getFileName().toString())))
                    .sorted(Comparator.comparing(f -> f.getFileName().toString())) //sort from begin segment to end segment
                    .map(f -> (f.getFileName().toString()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return files;
    }

    //generate MPD using MP4Box
    public void generateMPD(List<String> uploadedFiles, String videoID) {
        String concatFiles = "720p" + "_" + videoID + ".mp4 ";
        concatFiles = concatFiles + "480p" + "_" + videoID + ".mp4 ";
        concatFiles = concatFiles + "360p" + "_" + videoID + ".mp4 ";
        concatFiles = concatFiles + "240p" + "_" + videoID + ".mp4";

        String mpdCommand = "MP4Box -dash 3000 -rap -profile dashavc264:onDemand";
        mpdCommand = mpdCommand + " " + concatFiles;
        System.out.println("mpdCommand: " + mpdCommand);
        ProcessBuilder mpdBuilder = null;
        if (isWindows) {
            mpdBuilder = new ProcessBuilder("cmd.exe", "/c", "cd " + UPLOADED_FOLDER + " && " + mpdCommand);
        } else {
            mpdBuilder = new ProcessBuilder("sh", "-c", "cd " + UPLOADED_FOLDER + " && " + mpdCommand);
        }
        mpdBuilder.redirectErrorStream(true);
        Process p2 = null;
        try {
            String line;
            p2 = mpdBuilder.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p2.getInputStream()));
            r = new BufferedReader(new InputStreamReader(p2.getInputStream()));
            while (true) {
                line = r.readLine();
                if (line == null) {
                    break;
                }
                System.out.println(line);
            }
            p2.waitFor();
            List<File> files = null;
            try (Stream<Path> walk = Files.walk(Paths.get(UPLOADED_FOLDER))) {
                files = walk.filter(Files::isRegularFile)
                        .filter(f -> "mpd".equals(com.google.common.io.Files.getFileExtension(f.getFileName().toString())))
                        .filter(f -> f.getFileName().toString().contains(videoID))
                        .sorted(Comparator.comparing(f -> f.getFileName().toString()))
                        .map(f -> f.toFile())
                        .collect(Collectors.toList());
                File mpdFile = files.get(0);
                mpdFile.renameTo(new File(UPLOADED_FOLDER + File.separator + videoID + ".mpd"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            logger.trace(e.getMessage());
            logger.trace(e.getStackTrace().toString());
        }
    }

    //return all mpd on server
    public List<String> findAllMpd() {
        List<String> files = null;
        try (Stream<Path> walk = Files.walk(Paths.get(UPLOADED_FOLDER))) {
            files = walk.filter(Files::isRegularFile)
                    .filter(f -> "mpd".equals(com.google.common.io.Files.getFileExtension(f.getFileName().toString())))
                    .map(x -> ServletUriComponentsBuilder.fromCurrentContextPath()
                            .path("/downloadFile/")
                            .path(x.getFileName().toString())
                            .toUriString()).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            logger.trace(e.getLocalizedMessage());
            logger.trace(e.getStackTrace().toString());
        }
        return files;
    }
}