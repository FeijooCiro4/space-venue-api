package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.model.records.ImageUploadUrlRequestDTO;
import com.utn.space.venueaapi.model.records.ImageUploadUrlResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
//Genera la URL para subir imagenes al bucket de S3 y tambien se ocupa de borrar archivos del bucket
//Solo se pueden subir JPG, PNG y WEBP de hasta 5 MB.
//Los archivos se guardan en: media/spaces/{idSpace}/{uuid}.{ext}
@Service
public class ImageStorageService {
    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final Duration URL_DURATION = Duration.ofMinutes(5);
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    @Autowired
    S3Presigner s3Presigner;
    @Autowired
    S3Client s3Client;

    @Value("${aws.s3.bucket}")
    String bucket;

    // El prefijo "media/" coincide con el behavior media/* de CloudFront, que no lo quita al pedirle a S3
    public static String keyPrefixForSpace(Integer spaceId) {
        return "media/spaces/" + spaceId + "/";
    }

    public ImageUploadUrlResponseDTO createUploadUrl(ImageUploadUrlRequestDTO request) {
        String extension = ALLOWED_TYPES.get(request.contentType());
        if (extension == null) {
            throw new InvalidDataException("Tipo de imagen no permitido. Use JPG, PNG o WEBP");
        }

        if (request.size() > MAX_SIZE) {
            throw new InvalidDataException("La imagen debe pesar como máximo 5 MB");
        }

        String key = keyPrefixForSpace(request.idSpace()) + UUID.randomUUID() + "." + extension;

        // El tipo y el tamaño quedan firmados: S3 rechaza la subida si el archivo no coincide
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(request.contentType())
                .contentLength(request.size())
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presign -> presign
                        .signatureDuration(URL_DURATION)
                        .putObjectRequest(putObjectRequest))
                .url()
                .toString();

        return new ImageUploadUrlResponseDTO(uploadUrl, key);
    }

    public void deleteObject(String key) {
        try {
            s3Client.deleteObject(delete -> delete.bucket(bucket).key(key));
        } catch (RuntimeException e) {
            // La imagen ya se borró de la base; si falla S3 solo queda un archivo huérfano en el bucket
            log.warn("No se pudo borrar la imagen {} de S3", key, e);
        }
    }
}
