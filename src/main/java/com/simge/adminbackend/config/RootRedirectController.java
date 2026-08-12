package com.simge.adminbackend.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Kök yolu Swagger arayüzüne yönlendirir.
 *
 * <p>
 * Bu bir API servisi; kökte gösterilecek bir sayfası yok. Yönlendirme olmasa
 * {@code http://localhost:8081/} boş bir 404 döner ve "sunucu ayakta mı"
 * sorusunun cevabı belirsizleşirdi.
 * </p>
 */
@Controller
public class RootRedirectController {

    @GetMapping("/")
    public String root() {
        return "redirect:/swagger-ui.html";
    }
}
