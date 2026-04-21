package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.Feedback;
import com.zhaoyichi.devplatformbackend.service.FeedbackService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/submit")
    public Result<Feedback> submit(@RequestParam String type,
                                   @RequestParam String title,
                                   @RequestParam String content,
                                   @RequestParam(required = false) String contact,
                                   @RequestParam(required = false) MultipartFile file,
                                   HttpServletRequest request) throws IOException {
        Long userId = AuthHelper.currentUserId(request);
        if (userId == null) {
            return Result.error("未登录");
        }

        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setType(type);
        feedback.setTitle(title);
        feedback.setContent(content);
        feedback.setContact(contact);
        if (file != null && !file.isEmpty()) {
            feedback.setAttachmentPath(saveAttachment(file));
        }
        Feedback saved = feedbackService.create(feedback);
        return Result.success(saved);
    }

    @GetMapping("/myList")
    public Result<List<Feedback>> myList(HttpServletRequest request) {
        return Result.success(feedbackService.listByUser(AuthHelper.currentUserId(request)));
    }

    private String saveAttachment(MultipartFile file) throws IOException {
        String dirPath = System.getProperty("user.dir") + File.separator + "uploads" + File.separator + "feedback";
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String filename = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + (ext == null ? "" : "." + ext);
        File target = new File(dir, filename);
        file.transferTo(target);
        return "uploads/feedback/" + filename;
    }
}
