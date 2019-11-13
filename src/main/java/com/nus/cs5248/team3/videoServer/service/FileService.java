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

    //generate MPD using MP4Box
    public void generateMPD(List<String> uploadedFiles, String resolution, String videoID) {
        String concatFile = resolution + "_" + videoID + ".mp4";
        String concatCommand = "MP4Box -add " + resolution + "_" + uploadedFiles.get(0);
        for (int i = 1; i < uploadedFiles.size(); i++) {
            concatCommand = concatCommand + " -cat " + resolution + "_" + uploadedFiles.get(i);
        }
        concatCommand = concatCommand + " " + concatFile;

        String mpdCommand = "mp4box -dash-strict 1000 -rap -frag-rap -profile full";
        mpdCommand = mpdCommand + " " + concatFile;
        ProcessBuilder concatBuilder =null;
        ProcessBuilder mpdBuilder=null;
        if (isWindows) {
             concatBuilder = new ProcessBuilder(
                    "cmd.exe", "/c", "cd " + UPLOADED_FOLDER + " && " + concatCommand);
             mpdBuilder = new ProcessBuilder(         "cmd.exe", "/c", "cd " + UPLOADED_FOLDER + " && " + mpdCommand);

        } else {
            concatBuilder = new ProcessBuilder(
                    "sh", "-c", "cd " + UPLOADED_FOLDER + " && " + concatCommand);
            mpdBuilder = new ProcessBuilder(         "sh", "-c", "cd " + UPLOADED_FOLDER + " && " + mpdCommand);

        }
        concatBuilder.redirectErrorStream(true);
        mpdBuilder.redirectErrorStream(true);
        Process p = null;
        Process p2 = null;
        try {
            p = concatBuilder.start();

//            p.waitFor();
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
            p2 = mpdBuilder.start();
            r = new BufferedReader(new InputStreamReader(p2.getInputStream()));
            while (true) {
                line = r.readLine();
                if (line == null) {
                    break;
                }
                System.out.println(line);
            }
            p2.waitFor();
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

    //combine MPD from different resolutions
    public void concatMPD(String videoID) {
        List<File> files = null;
        int id = 2;
        try (Stream<Path> walk = Files.walk(Paths.get(UPLOADED_FOLDER))) {
            files = walk.filter(Files::isRegularFile)
                    .filter(f -> "mpd".equals(com.google.common.io.Files.getFileExtension(f.getFileName().toString())))
                    .filter(f -> f.getFileName().toString().contains(videoID))
                    .sorted(Comparator.comparing(f -> f.getFileName().toString()))
                    .map(f->f.toFile())
                    .collect(Collectors.toList());
            File mpdFile = files.get(0);
            files.remove(mpdFile);
            String mpdFileContext = FileUtils.readFileToString(mpdFile,Charsets.UTF_8);
            for (File file:files){
                String fileContext = FileUtils.readFileToString(file,Charsets.UTF_8);
                fileContext = fileContext.substring(fileContext.indexOf("<Representation"),fileContext.indexOf("</AdaptationSet>"));
                fileContext = fileContext.replaceAll("id=\"1\"", "id=\""+id+"\"");
                mpdFileContext = mpdFileContext.substring(0, mpdFileContext.lastIndexOf("</AdaptationSet>"))+fileContext+mpdFileContext.substring(mpdFileContext.lastIndexOf("</AdaptationSet>"));
                FileUtils.write(mpdFile, mpdFileContext, Charsets.UTF_8);
                id ++;
            }
            //Rename and clean up useless mpd
            mpdFile.renameTo(new File(UPLOADED_FOLDER+File.separator+videoID+".mpd"));
            for (File file: files){
                file.delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
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