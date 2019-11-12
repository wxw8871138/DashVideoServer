package com.nus.cs5248.team3.videoServer.controller;

import com.nus.cs5248.team3.videoServer.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

@Controller
public class VideoController {
    @Autowired
    FileService fileService;

    /* API for directing to index page
     */
    @GetMapping(path = "/index")
    public String index(Model model) {
//        List<String> files = fileService.findAll();
        List<String> files = fileService.findAllMpd();
        model.addAttribute("files", files);
        return "index";
    }

    /* API for directing to player page
     */
    @GetMapping(path = "/player")
    public String getToPlayer(Model model, @RequestParam("videoURL") String videoURL) {
        videoURL = HtmlUtils.htmlEscape(videoURL);
        model.addAttribute("videoURL", videoURL);
        return "dashPlayer";
    }

    /* API for directing to upload page
     */
    @GetMapping("/upload")
    public String uploadForm(Model model) {
        return "uploadForm";
    }

    /* API for directing to upload status page
     */
    @GetMapping("/uploadStatus")
    public String uploadStatus(Model model) {
        return "uploadStatus";
    }

}
