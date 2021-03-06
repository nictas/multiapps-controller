package com.sap.cloud.lm.sl.cf.persistence.services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;

import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sap.cloud.lm.sl.cf.persistence.Constants;
import com.sap.cloud.lm.sl.cf.persistence.message.Messages;
import com.sap.cloud.lm.sl.cf.persistence.model.FileInfo;
import com.sap.cloud.lm.sl.cf.persistence.model.ImmutableFileInfo;

public class FileUploader {

    private static final String EXTENSION = "tmp";
    private static final String PREFIX = "fileUpload";
    public static final String DIGEST_METHOD = "MD5";

    private static final Logger logger = LoggerFactory.getLogger(FileUploader.class);

    private FileUploader() {
    }

    /**
     * Uploads file.
     * 
     * @param is input stream
     * @param fileUploadProcessor file upload processor
     * @return uploaded file
     * @throws FileStorageException
     */
    public static FileInfo uploadFile(InputStream is) throws FileStorageException {
        BigInteger size = BigInteger.valueOf(0);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(DIGEST_METHOD);
        } catch (NoSuchAlgorithmException e) {
            throw new FileStorageException(e);
        }

        File tempFile;
        try {
            tempFile = File.createTempFile(PREFIX, EXTENSION);
        } catch (IOException e) {
            throw new FileStorageException(e);
        }

        // store the passed input to the file system
        try (OutputStream outputFileStream = new FileOutputStream(tempFile)) {
            int read = 0;
            byte[] buffer = new byte[Constants.BUFFER_SIZE];
            while ((read = is.read(buffer, 0, Constants.BUFFER_SIZE)) > -1) {
                outputFileStream.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                size = size.add(BigInteger.valueOf(read));
            }
        } catch (IOException e) {
            FileUploader.deleteFile(tempFile);
            throw new FileStorageException(e);
        }

        return ImmutableFileInfo.builder()
                                .file(tempFile)
                                .size(size)
                                .digest(getDigestString(digest.digest()))
                                .digestAlgorithm(DIGEST_METHOD)
                                .build();
    }

    private static String getDigestString(byte[] digest) {
        return DatatypeConverter.printHexBinary(digest);
    }

    public static void deleteFile(FileInfo uploadedFile) {
        File file = uploadedFile.getFile();
        deleteFile(file);
    }

    private static void deleteFile(File file) {
        Path filePath = file.toPath();
        try {
            Files.delete(filePath);
        } catch (IOException e) {
            logger.warn(MessageFormat.format(Messages.FAILED_TO_DELETE_FILE, filePath), e);
        }
    }

}
