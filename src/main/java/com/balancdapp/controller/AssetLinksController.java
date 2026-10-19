package com.balancdapp.controller;

import com.balancdapp.service.PasskeyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Digital Asset Links: Android solo deja a la app usar passkeys de balancd.es si el dominio declara aquí
 * su paquete y la huella del certificado de firma (WEBAUTHN_ANDROID_CERTS). Sin huellas devuelve [].
 */
@RestController
public class AssetLinksController {

    private final PasskeyService passkeyService;
    private final String androidPackage;

    public AssetLinksController(PasskeyService passkeyService,
                                @Value("${app.webauthn.android-package:com.balancdapp.android}") String androidPackage) {
        this.passkeyService = passkeyService;
        this.androidPackage = androidPackage;
    }

    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> assetLinks() {
        if (passkeyService.getAndroidCertFingerprints().isEmpty()) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(List.of(Map.of(
                "relation", List.of("delegate_permission/common.get_login_creds", "delegate_permission/common.handle_all_urls"),
                "target", Map.of(
                        "namespace", "android_app",
                        "package_name", androidPackage,
                        "sha256_cert_fingerprints", List.copyOf(passkeyService.getAndroidCertFingerprints())
                )
        )));
    }
}
