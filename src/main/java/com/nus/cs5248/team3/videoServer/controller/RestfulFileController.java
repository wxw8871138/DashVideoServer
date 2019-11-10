package com.nus.cs5248.team3.videoServer.controller;

import com.google.common.io.Files;
import com.nus.cs5248.team3.videoServer.model.UploadFileResponse;
import com.nus.cs5248.team3.videoServer.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@RestController
public class RestfulFileController {
    @Autowired
    private FileService fileService;
    private static final Logger logger
            = LoggerFactory.getLogger(RestfulFileController.class);


    /* API for uploading files to server
    */
    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public UploadFileResponse handleFileUpload(@RequestParam("file") MultipartFile file) throws Exception {
        if(!"mp4".equals(Files.getFileExtension(StringUtils.cleanPath(file.getOriginalFilename())))){
            throw new Exception("File is not mp4 type. Cannot upload");
        }
        String fileName = fileService.store(file);
        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/downloadFile/")
                .path(fileName)
                .toUriString();
//        fileService.encode(fileName);
        fileService.generateMPD("test");
        return new UploadFileResponse(fileName, fileDownloadUri,
                file.getContentType(), file.getSize());
    }

    /* API for retrieving files from server
     */
    @GetMapping("/downloadFile/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName, HttpServletRequest request) throws Exception {
        // Load file as Resource
        Resource resource = fileService.loadFileAsResource(fileName);

        // Try to determine file's content type
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            logger.info("Could not determine file type.");
        }

        // Fallback to the default content type if type could not be determined
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
