package com.cos.fairbid.notification.adapter.out.fcm;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Firebase 설정
 * FCM Push 알림을 위한 Firebase Admin SDK 초기화
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.config.path:firebase-service-account.json}")
    private String firebaseConfigPath;

    @Value("${firebase.enabled:false}")
    private boolean firebaseEnabled;

    /**
     * Firebase 초기화
     * firebase-service-account.json 파일이 있으면 초기화
     */
    @PostConstruct
    public void initialize() {
        if (!firebaseEnabled) {
            log.info("Firebase is disabled. Skipping initialization.");
            return;
        }

        try {
            if (FirebaseApp.getApps().isEmpty()) {
                ClassPathResource resource = new ClassPathResource(firebaseConfigPath);

                // try-with-resources로 InputStream 자동 닫기
                try (InputStream serviceAccount = resource.getInputStream()) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                            .build();

                    FirebaseApp.initializeApp(options);
                    log.info("Firebase initialized successfully");
                }
            }
        } catch (IOException e) {
            log.warn("Firebase initialization failed. FCM will not work. Error: {}", e.getMessage());
        }
    }
}
