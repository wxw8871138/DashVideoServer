package com.nus.cs5248.team3.videoServer.service;

import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.hubspot.jinjava.Jinjava;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import org.apache.commons.io.Charsets;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    @Value("${mpd_template_path}")
    private String MPD_TEMPLATE_PATH;
    private static final Logger logger
            = LoggerFactory.getLogger(FileService.class);
    private static Path fileStorageLocation;

    @Autowired
    private EncodeUtil encodeUtil;

    private Path getFileStorageLocation() {
        if (this.fileStorageLocation == null) {
            this.fileStorageLocation = Paths.get(UPLOADED_FOLDER)
                    .toAbsolutePath().normalize();
        }
        return this.fileStorageLocation;
    }

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

    public void encode(String fileName) {
        logger.info("Start encoding now");
        try {
            FFmpeg ffmpeg = new FFmpeg(FFMPEG_PATH);
            FFprobe ffprobe = new FFprobe(FFPROBE_PATH);
            FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
            String filePath = this.fileStorageLocation.resolve(fileName).normalize().toAbsolutePath().toString();
            int dotPostion = filePath.indexOf('.');

            encodeUtil.encodeTo720p(filePath, dotPostion, executor);
            encodeUtil.encodeTo480p(filePath, dotPostion, executor);
            encodeUtil.encodeTo360p(filePath, dotPostion, executor);
            encodeUtil.encodeTo240p(filePath, dotPostion, executor);
        } catch (IOException e) {
            logger.trace(e.getMessage());
            logger.trace(e.getStackTrace().toString());
            e.printStackTrace();
        }
    }

    public void generateMPD(String videoID) {
        List<String> files = null;
        try (Stream<Path> walk = Files.walk(Paths.get(UPLOADED_FOLDER))) {
            files = walk.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().startsWith(videoID))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())) //sort from begin segment to end segment
                    .map(x -> (x.getFileName().toString()))
                    .collect(Collectors.toList());
            Jinjava jinjava = new Jinjava();
            Map<String, Object> context = Maps.newHashMap();
            context.put("name", videoID);
            context.put("segments_720p",files);
            String template = Resources.toString(Resources.getResource("mpdTemplate.xml"), Charsets.UTF_8);
            String renderedTemplate = jinjava.render(template, context);
            Path path = this.fileStorageLocation.resolve(videoID + ".mpd");
            byte[] strToBytes = renderedTemplate.getBytes();
            Files.write(path, strToBytes);
        } catch (IOException e) {
            e.printStackTrace();
            logger.trace(e.getLocalizedMessage());
            logger.trace(e.getStackTrace().toString());
        }

    }
}