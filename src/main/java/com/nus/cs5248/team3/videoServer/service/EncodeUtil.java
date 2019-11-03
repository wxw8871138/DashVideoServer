package com.nus.cs5248.team3.videoServer.service;

import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EncodeUtil {
    private static final Logger logger
            = LoggerFactory.getLogger(EncodeUtil.class);
    public void encodeTo720p(String filePath, int dotPostion, FFmpegExecutor executor) {
        logger.info("Start encoding 720p now");
        String encodedFilePath = filePath.substring(0, dotPostion) + "_encoded_720p" + filePath.substring(dotPostion);
        FFmpegBuilder builder = new FFmpegBuilder().
                setInput(filePath)     // Filename, or a FFmpegProbeResult
                .overrideOutputFiles(true) // Override the output if it exists

                .addOutput(encodedFilePath)   // Filename for the destination
                .setFormat("mp4")        // Format is inferred from filename, or can be set
                .setVideoBitRate(4096000)   //at 4000 kbs

                .disableSubtitle()       // No subtiles

                .setAudioChannels(1)         // Mono audio
                .setAudioCodec("aac")        // using the aac codec
                .setAudioSampleRate(48_000)  // at 48KHz
                .setAudioBitRate(131072)      // at 128 kbit/s

                .setVideoCodec("libx264")     // Video using x264
                .setVideoFrameRate(24, 1)     // at 24 frames per second
                .setVideoResolution(1280, 720) // at 640x480 resolution

                .setStrict(FFmpegBuilder.Strict.EXPERIMENTAL) // Allow FFmpeg to use experimental specs
                .done();

        // Run a one-pass encode
        executor.createJob(builder).run();
    }

    public void encodeTo480p(String filePath, int dotPostion, FFmpegExecutor executor) {
        logger.info("Start encoding 480p now");

        String encodedFilePath = filePath.substring(0, dotPostion) + "_encoded_480p" + filePath.substring(dotPostion);
        FFmpegBuilder builder = new FFmpegBuilder().
                setInput(filePath)     // Filename, or a FFmpegProbeResult
                .overrideOutputFiles(true) // Override the output if it exists

                .addOutput(encodedFilePath)   // Filename for the destination
                .setFormat("mp4")        // Format is inferred from filename, or can be set
                .setVideoBitRate(2048000)   //at 2000 kbs

                .disableSubtitle()       // No subtiles

                .setAudioChannels(1)         // Mono audio
                .setAudioCodec("aac")        // using the aac codec
                .setAudioSampleRate(48_000)  // at 48KHz
                .setAudioBitRate(131072)      // at 128 kbit/s

                .setVideoCodec("libx264")     // Video using x264
                .setVideoFrameRate(24, 1)     // at 24 frames per second
                .setVideoResolution(854, 480) // at 640x480 resolution

                .setStrict(FFmpegBuilder.Strict.EXPERIMENTAL) // Allow FFmpeg to use experimental specs
                .done();

        // Run a one-pass encode
        executor.createJob(builder).run();
    }

    public void encodeTo360p(String filePath, int dotPostion, FFmpegExecutor executor) {
        logger.info("Start encoding 360p now");

        String encodedFilePath = filePath.substring(0, dotPostion) + "_encoded_360p" + filePath.substring(dotPostion);
        FFmpegBuilder builder = new FFmpegBuilder().
                setInput(filePath)     // Filename, or a FFmpegProbeResult
                .overrideOutputFiles(true) // Override the output if it exists

                .addOutput(encodedFilePath)   // Filename for the destination
                .setFormat("mp4")        // Format is inferred from filename, or can be set
                .setVideoBitRate(1024000)   //at 1000 kbs

                .disableSubtitle()       // No subtiles

                .setAudioChannels(1)         // Mono audio
                .setAudioCodec("aac")        // using the aac codec
                .setAudioSampleRate(48_000)  // at 48KHz
                .setAudioBitRate(131072)      // at 128 kbit/s

                .setVideoCodec("libx264")     // Video using x264
                .setVideoFrameRate(24, 1)     // at 24 frames per second
                .setVideoResolution(640, 360) // at 640x480 resolution

                .setStrict(FFmpegBuilder.Strict.EXPERIMENTAL) // Allow FFmpeg to use experimental specs
                .done();

        // Run a one-pass encode
        executor.createJob(builder).run();
    }


    public void encodeTo240p(String filePath, int dotPostion, FFmpegExecutor executor) {
        logger.info("Start encoding 240p now");

        String encodedFilePath = filePath.substring(0, dotPostion) + "_encoded_240p" + filePath.substring(dotPostion);
        FFmpegBuilder builder = new FFmpegBuilder().
                setInput(filePath)     // Filename, or a FFmpegProbeResult
                .overrideOutputFiles(true) // Override the output if it exists

                .addOutput(encodedFilePath)   // Filename for the destination
                .setFormat("mp4")        // Format is inferred from filename, or can be set
                .setVideoBitRate(716800)   //at 700 kbs

                .disableSubtitle()       // No subtiles

                .setAudioChannels(1)         // Mono audio
                .setAudioCodec("aac")        // using the aac codec
                .setAudioSampleRate(48_000)  // at 48KHz
                .setAudioBitRate(65536)      // at 128 kbit/s

                .setVideoCodec("libx264")     // Video using x264
                .setVideoFrameRate(24, 1)     // at 24 frames per second
                .setVideoResolution(426, 240) // at 640x480 resolution

                .setStrict(FFmpegBuilder.Strict.EXPERIMENTAL) // Allow FFmpeg to use experimental specs
                .done();

        // Run a one-pass encode
        executor.createJob(builder).run();
    }
}
