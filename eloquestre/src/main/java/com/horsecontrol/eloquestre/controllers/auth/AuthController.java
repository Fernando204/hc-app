package com.horsecontrol.eloquestre.controllers.auth;

import com.horsecontrol.eloquestre.services.auth.AuthService;
import com.horsecontrol.eloquestre.services.internal.Logger;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.horsecontrol.eloquestre.config.JwtAuthFilter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** POST /auth/register — cria a conta e já devolve o token (sem confirmação de e-mail por enquanto). */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResponse register(@Valid @RequestBody AuthService.RegisterRequest request) {
        Logger.debug("teste registro");
        return authService.register(request);
    }

    /** POST /auth/login — 401 "Invalid login credentials" se e-mail ou senha estiverem errados. */
    @PostMapping("/login")
    public AuthService.AuthResponse login(@Valid @RequestBody AuthService.LoginRequest request) {
        Logger.debug("teste login");
        return authService.login(request);
    }

    /** POST /auth/logout {all?} - com all=true sai de todos os aparelhos. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @RequestBody(required = false) Map<String, Object> body) {
        authService.logout(me, body != null && Boolean.TRUE.equals(body.get("all")));
    }

    /** POST /auth/forgot-password {email} - sempre 204 (não revela se a conta existe). */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@RequestBody Map<String, String> body) {
        authService.forgotPassword(body.get("email"));
    }

    @PostMapping("/resend-confirmation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendConfirmation(@RequestBody Map<String, String> body) {
        authService.resendConfirmation(body.get("email"));
    }

    /** POST /auth/exchange {code} - troca o código do link do e-mail por uma sessão. */
    @PostMapping("/exchange")
    public AuthService.AuthResponse exchange(@RequestBody Map<String, String> body) {
        return authService.exchange(body.get("code"));
    }

    /** POST /auth/reset-password {password} - logado; devolve token novo (os antigos deixam de valer). */
    @PostMapping("/reset-password")
    public AuthService.AuthResponse resetPassword(@RequestAttribute(JwtAuthFilter.USER_ID) String me, @RequestBody Map<String, String> body) {
        return authService.resetPassword(me, body.get("password"));
    }
}
